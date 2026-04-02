package com.sanchay.service;

import com.sanchay.model.*;
import com.sanchay.model.Transaction.Type;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.Month;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pure-computation service: projects account balances month by month
 * over a given date range, based on recurring schedules and maturity events.
 * No DataStore mutation — all state comes from DataStore queries.
 */
public class CashFlowProjectionService {

    private final DataStore ds = DataStore.getInstance();

    // ── Public types ──────────────────────────────────────────────────────────

    public record ProjectionPoint(LocalDate date, long balancePaise) {}

    public record ForecastedExpense(String categoryId, String subCategoryId, YearMonth month, 
                                  long amountPaise, double confidence, String method) {}

    public record ExpensePattern(String categoryId, String subCategoryId, long avgMonthlyPaise, 
                               Map<Month, Double> seasonalFactors, 
                               double trendSlope, int dataPoints) {}

    public record ProjectionResult(
            List<Account>                      accounts,
            List<ProjectionPoint>              totalSeries,
            Map<String, List<ProjectionPoint>> accountSeries,
            long                               totalProjectedIncomePaise,
            long                               totalProjectedExpensesPaise,
            List<String>                       warnings,
            List<ForecastedExpense>            forecastedExpenses
    ) {}

    // ── Main entry point ──────────────────────────────────────────────────────

    public ProjectionResult compute(LocalDate startDate, LocalDate endDate) {

        // ── Step A: Build candidate accounts ──────────────────────────────────
        List<Account> candidates = new ArrayList<>();
        candidates.addAll(ds.getBankAccounts());
        candidates.addAll(ds.getCreditCardAccounts());
        ds.getInvestmentAccounts().stream()
                .filter(ia -> {
                    InvestmentAccount.InvestmentType t = ia.getInvestmentType();
                    return (t == InvestmentAccount.InvestmentType.FIXED_DEPOSIT
                         || t == InvestmentAccount.InvestmentType.RECURRING_DEPOSIT
                         || t == InvestmentAccount.InvestmentType.DEBT_BONDS)
                        && ia.getInvestmentStatus() == InvestmentAccount.InvestmentStatus.ACTIVE;
                })
                .forEach(candidates::add);

        // ── Step B: Compute starting balance per candidate account ─────────────
        Map<String, Long> rawBalances = new LinkedHashMap<>();

        for (Account acc : candidates) {
            if (acc instanceof BankAccount ba) {
                long bal = ba.getOpeningBalancePaise();
                for (Transaction t : ds.getTransactions()) {
                    if (ba.getId().equals(t.getFromAccountId())) bal -= t.getAmountPaise();
                    if (ba.getId().equals(t.getToAccountId()))   bal += t.getAmountPaise();
                }
                rawBalances.put(ba.getId(), bal);

            } else if (acc instanceof CreditCardAccount cc) {
                // CC: negative = outstanding liability
                long bal = 0;
                for (Transaction t : ds.getTransactions()) {
                    if (cc.getId().equals(t.getFromAccountId())) bal -= t.getAmountPaise();
                    if (cc.getId().equals(t.getToAccountId()))   bal += t.getAmountPaise();
                }
                rawBalances.put(cc.getId(), bal);

            } else if (acc instanceof InvestmentAccount ia) {
                long invested = ds.getBaseInvestedPaise(ia);
                rawBalances.put(ia.getId(), invested);
            }
        }

        // ── Filter out zero-balance investment accounts ────────────────────────
        // Build the final accounts list and balances map together before any lambda
        List<Account> accounts = new ArrayList<>();
        Map<String, Long> balances = new LinkedHashMap<>();
        for (Account acc : candidates) {
            long bal = rawBalances.getOrDefault(acc.getId(), 0L);
            if (acc instanceof InvestmentAccount && bal == 0) continue; // skip zero-balance
            accounts.add(acc);
            balances.put(acc.getId(), bal);
        }

        // Compute includedIds from the final filtered list
        Set<String> includedIds = accounts.stream()
                .map(Account::getId)
                .collect(Collectors.toSet());

        // ── Step C: Month-end dates ────────────────────────────────────────────
        List<YearMonth> months = new ArrayList<>();
        YearMonth cur = YearMonth.from(startDate);
        YearMonth end = YearMonth.from(endDate);
        while (!cur.isAfter(end)) {
            months.add(cur);
            cur = cur.plusMonths(1);
        }

        // ── Step C.5: Analyze expense patterns and generate forecasts ────────
        List<ExpensePattern> expensePatterns = analyzeExpensePatterns();
        List<ForecastedExpense> forecastedExpenses = generateExpenseForecasts(expensePatterns, startDate, endDate);

        // Group forecasts by month for efficient lookup
        Map<YearMonth, List<ForecastedExpense>> forecastsByMonth = forecastedExpenses.stream()
            .collect(Collectors.groupingBy(ForecastedExpense::month));

        // ── Pre-process: build FD maturity events ─────────────────────────────
        record MaturityEvent(String bankId, String investId, long matAmtPaise, long investedPaise) {}
        Map<YearMonth, List<MaturityEvent>> fdMaturities = new HashMap<>();

        for (Transaction t : ds.getTransactions()) {
            if (t.getType() != Type.INVESTMENT) continue;
            if (t.getInvestmentDetails() == null) continue;
            Transaction.FdDetails fd = t.getInvestmentDetails().getFd();
            if (fd == null || fd.getMaturityDate() == null) continue;
            YearMonth matMonth = YearMonth.from(fd.getMaturityDate());
            if (matMonth.isBefore(YearMonth.from(startDate)) || matMonth.isAfter(YearMonth.from(endDate))) continue;
            if (!includedIds.contains(t.getToAccountId())) continue;

            long matAmt = (fd.getMaturityAmountPaise() != null && fd.getMaturityAmountPaise() > 0)
                    ? fd.getMaturityAmountPaise() : t.getAmountPaise();

            fdMaturities.computeIfAbsent(matMonth, k -> new ArrayList<>())
                    .add(new MaturityEvent(t.getFromAccountId(), t.getToAccountId(),
                                           matAmt, t.getAmountPaise()));
        }

        // ── Pre-process: build RD maturity events ─────────────────────────────
        Map<YearMonth, List<MaturityEvent>> rdMaturities = new HashMap<>();
        for (RecurringTransaction rt : ds.getRecurring()) {
            if (rt.getTransactionType() != Type.INVESTMENT) continue;
            if (rt.getMaturityDate() == null) continue;
            if (!includedIds.contains(rt.getToAccountId())) continue;
            Account toAcc = accounts.stream()
                    .filter(a -> a.getId().equals(rt.getToAccountId()))
                    .findFirst().orElse(null);
            if (!(toAcc instanceof InvestmentAccount ia)) continue;
            if (ia.getInvestmentType() != InvestmentAccount.InvestmentType.RECURRING_DEPOSIT) continue;

            YearMonth matMonth = YearMonth.from(rt.getMaturityDate());
            if (matMonth.isBefore(YearMonth.from(startDate)) || matMonth.isAfter(YearMonth.from(endDate))) continue;

            long matAmt = rt.getRdMaturityAmountPaise() > 0
                    ? rt.getRdMaturityAmountPaise() : rt.getAmountPaise();

            rdMaturities.computeIfAbsent(matMonth, k -> new ArrayList<>())
                    .add(new MaturityEvent(rt.getFromAccountId(), rt.getToAccountId(),
                                           matAmt, 0));
        }

        // ── Step D & E: Month-by-month projection ─────────────────────────────
        Map<String, List<ProjectionPoint>> accountSeries = new LinkedHashMap<>();
        for (Account acc : accounts) accountSeries.put(acc.getId(), new ArrayList<>());

        List<ProjectionPoint> totalSeries = new ArrayList<>();
        long totalIncomePaise  = 0;
        long totalExpensePaise = 0;

        for (YearMonth ym : months) {
            LocalDate monthStart = ym.atDay(1);
            LocalDate monthEnd   = ym.atEndOfMonth();

            // Track recurring EXPENSE amount per (categoryId|subCategoryId) so we can
            // subtract it from pattern-based forecasts and avoid double-counting.
            Map<String, Long> recurringExpenseByCatKey = new HashMap<>();

            // Apply recurring schedules for this month
            for (RecurringTransaction rt : ds.getRecurring()) {
                if (rt.getAmountPaise() == 0) continue; // variable / CC reminders — skip
                List<LocalDate> occurrences = getOccurrencesInRange(rt, monthStart, monthEnd);
                if (occurrences.isEmpty()) continue;
                long amt = rt.getAmountPaise() * occurrences.size();

                Type   type   = rt.getTransactionType();
                String fromId = rt.getFromAccountId();
                String toId   = rt.getToAccountId();

                switch (type) {
                    case INCOME -> {
                        if (toId != null && includedIds.contains(toId)) {
                            balances.merge(toId, amt, Long::sum);
                            totalIncomePaise += amt;
                        }
                    }
                    case EXPENSE -> {
                        if (fromId != null && includedIds.contains(fromId)) {
                            balances.merge(fromId, -amt, Long::sum);
                            totalExpensePaise += amt;
                        }
                        // Record against sub-category key so forecasts can deduct this coverage
                        if (rt.getCategoryId() != null && rt.getSubCategoryId() != null) {
                            String catKey = rt.getCategoryId() + "|" + rt.getSubCategoryId();
                            recurringExpenseByCatKey.merge(catKey, amt, Long::sum);
                        }
                    }
                    case INVESTMENT -> {
                        if (fromId != null && includedIds.contains(fromId)) {
                            balances.merge(fromId, -amt, Long::sum);
                            totalExpensePaise += amt; // outflow from bank
                        }
                        if (toId != null && includedIds.contains(toId)) {
                            balances.merge(toId, amt, Long::sum);
                        }
                    }
                    case LOAN_PAYMENT -> {
                        if (fromId != null && includedIds.contains(fromId)) {
                            balances.merge(fromId, -amt, Long::sum);
                            totalExpensePaise += amt;
                        }
                    }
                    case CC_PAYMENT -> {
                        if (fromId != null && includedIds.contains(fromId)) {
                            balances.merge(fromId, -amt, Long::sum);
                            totalExpensePaise += amt;
                        }
                        if (toId != null && includedIds.contains(toId)) {
                            balances.merge(toId, amt, Long::sum);
                        }
                    }
                    case TRANSFER -> {
                        if (fromId != null && includedIds.contains(fromId))
                            balances.merge(fromId, -amt, Long::sum);
                        if (toId != null && includedIds.contains(toId))
                            balances.merge(toId, amt, Long::sum);
                    }
                    default -> { /* REDEEM, GAIN, LOSE — skip */ }
                }
            }

            // Apply FD maturities for this month
            for (MaturityEvent me : fdMaturities.getOrDefault(ym, List.of())) {
                if (me.bankId() != null && includedIds.contains(me.bankId()))
                    balances.merge(me.bankId(), me.matAmtPaise(), Long::sum);
                if (includedIds.contains(me.investId()))
                    balances.put(me.investId(), 0L);
                totalIncomePaise += me.matAmtPaise();
            }

            // Apply RD maturities for this month
            for (MaturityEvent me : rdMaturities.getOrDefault(ym, List.of())) {
                if (me.bankId() != null && includedIds.contains(me.bankId()))
                    balances.merge(me.bankId(), me.matAmtPaise(), Long::sum);
                if (includedIds.contains(me.investId()))
                    balances.put(me.investId(), 0L);
                totalIncomePaise += me.matAmtPaise();
            }

            // Apply forecasted expenses for this month — discretionary portion only.
            // Pattern averages are trained on all historical expenses, which includes amounts
            // already covered by recurring schedules. Subtract recurring coverage to avoid
            // double-counting; only apply what exceeds the scheduled amount.
            for (ForecastedExpense forecast : forecastsByMonth.getOrDefault(ym, List.of())) {
                String catKey = forecast.categoryId() + "|" + forecast.subCategoryId();
                long recurringCovered = recurringExpenseByCatKey.getOrDefault(catKey, 0L);
                long discretionary = Math.max(0, forecast.amountPaise() - recurringCovered);
                if (discretionary == 0) continue;

                String expenseAccountId = findMostLikelyExpenseAccount(forecast.categoryId(), forecast.subCategoryId());
                if (expenseAccountId != null && includedIds.contains(expenseAccountId)) {
                    balances.merge(expenseAccountId, -discretionary, Long::sum);
                    totalExpensePaise += discretionary;
                }
            }

            // Record snapshot for this month
            long total = 0;
            for (Account acc : accounts) {
                long bal = balances.getOrDefault(acc.getId(), 0L);
                accountSeries.get(acc.getId()).add(new ProjectionPoint(monthEnd, bal));
                total += bal;
            }
            totalSeries.add(new ProjectionPoint(monthEnd, total));
        }

        // ── Step F: Loan warnings ──────────────────────────────────────────────
        List<String> warnings = new ArrayList<>();
        for (LoanAccount loan : ds.getActiveLoanAccounts()) {
            boolean hasSchedule = ds.getRecurring().stream()
                    .anyMatch(rt -> rt.getTransactionType() == Type.LOAN_PAYMENT
                            && loan.getId().equals(rt.getToAccountId()));
            if (!hasSchedule) {
                warnings.add("EMI for \u201c" + loan.getName() + "\u201d not included \u2014 no recurring payment set up");
            }
        }

        return new ProjectionResult(
                accounts,
                totalSeries,
                accountSeries,
                totalIncomePaise,
                totalExpensePaise,
                warnings,
                forecastedExpenses
        );
    }

    /**
     * Finds the most likely account for expenses in a given sub-category based on historical patterns.
     * Returns the account ID that has been most frequently used for this category and sub-category's expenses.
     */
    private String findMostLikelyExpenseAccount(String categoryId, String subCategoryId) {
        Map<String, Long> accountUsage = new HashMap<>();

        // Count how many times each account has been used for this sub-category's expenses
        for (Transaction t : ds.getTransactions()) {
            if (t.getType() == Type.EXPENSE && t.getClassification() != null && 
                Objects.equals(t.getClassification().getCategoryId(), categoryId) &&
                Objects.equals(t.getClassification().getSubCategoryId(), subCategoryId)) {
                String accountId = t.getFromAccountId();
                if (accountId != null) {
                    accountUsage.merge(accountId, t.getAmountPaise(), Long::sum);
                }
            }
        }

        // Return the account with the highest total spending for this sub-category
        return accountUsage.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    // ── Expense Forecasting ──────────────────────────────────────────────────

    /**
     * Analyzes historical expense data to identify spending patterns by sub-category.
     * Uses the last N months of data (configurable via settings).
     * Skips transactions without a sub-category (Option B).
     */
    private List<ExpensePattern> analyzeExpensePatterns() {
        int analysisMonths = ds.getExpenseForecastAnalysisMonths();
        LocalDate analysisEnd = LocalDate.now(); // March 30, 2026
        LocalDate analysisStart = analysisEnd.minusMonths(analysisMonths);

        // Group expenses by (categoryId, subCategoryId) and month
        // Key: "categoryId|subCategoryId" to uniquely identify sub-category
        Map<String, Map<YearMonth, Long>> subCategoryMonthlyExpenses = new HashMap<>();
        Map<String, String> subCatIdMap = new HashMap<>(); // Store (category, subCategory) mapping

        for (Transaction t : ds.getTransactions()) {
            if (t.getType() != Type.EXPENSE) continue;
            if (t.getDate().isBefore(analysisStart) || t.getDate().isAfter(analysisEnd)) continue;
            if (t.getClassification() == null || t.getClassification().getCategoryId() == null) continue;
            // Option B: Skip if sub-category is null/unmapped
            if (t.getClassification().getSubCategoryId() == null) continue;

            String catId = t.getClassification().getCategoryId();
            String subCatId = t.getClassification().getSubCategoryId();
            String key = catId + "|" + subCatId;

            YearMonth ym = YearMonth.from(t.getDate());
            subCategoryMonthlyExpenses
                .computeIfAbsent(key, k -> new HashMap<>())
                .merge(ym, t.getAmountPaise(), Long::sum);
            
            subCatIdMap.put(key, subCatId); // Store for later reference
        }

        List<ExpensePattern> patterns = new ArrayList<>();

        for (Map.Entry<String, Map<YearMonth, Long>> entry : subCategoryMonthlyExpenses.entrySet()) {
            String key = entry.getKey();
            String[] parts = key.split("\\|");
            String categoryId = parts[0];
            String subCategoryId = parts[1];
            Map<YearMonth, Long> monthlyData = entry.getValue();

            if (monthlyData.size() < 3) continue; // Need at least 3 months of data

            // Calculate average monthly spending over the full analysis window.
            // Dividing by monthlyData.size() (months-with-data) would inflate the average
            // for irregular expenses; use analysisMonths (the full window) instead.
            long totalSpent = monthlyData.values().stream().mapToLong(Long::longValue).sum();
            long avgMonthly = totalSpent / analysisMonths;

            // Calculate seasonal factors (how much each month deviates from average)
            Map<Month, List<Long>> monthlyAmounts = new HashMap<>();
            for (Map.Entry<YearMonth, Long> monthEntry : monthlyData.entrySet()) {
                Month month = monthEntry.getKey().getMonth();
                monthlyAmounts.computeIfAbsent(month, k -> new ArrayList<>()).add(monthEntry.getValue());
            }

            Map<Month, Double> seasonalFactors = new HashMap<>();
            for (Month month : Month.values()) {
                List<Long> amounts = monthlyAmounts.get(month);
                if (amounts != null && !amounts.isEmpty()) {
                    double monthAvg = amounts.stream().mapToLong(Long::longValue).average().orElse(0.0);
                    seasonalFactors.put(month, monthAvg / avgMonthly);
                } else {
                    seasonalFactors.put(month, 1.0); // Default to average
                }
            }

            // Simple trend calculation (slope of linear regression)
            double trendSlope = calculateTrendSlope(monthlyData);

            patterns.add(new ExpensePattern(categoryId, subCategoryId, avgMonthly, seasonalFactors, trendSlope, monthlyData.size()));
        }

        return patterns;
    }

    /**
     * Generates forecasted expenses for future months based on historical patterns.
     * Uses pessimistic approach: max(forecast, sum_of_all_recurring_for_subcategory)
     * to avoid under-projecting and handle growth in spending.
     */
    private List<ForecastedExpense> generateExpenseForecasts(List<ExpensePattern> patterns,
                                                           LocalDate forecastStart, LocalDate forecastEnd) {
        List<ForecastedExpense> forecasts = new ArrayList<>();
        LocalDate currentDate = LocalDate.now(); // March 30, 2026

        // Pre-compute sum of active recurring transactions per (categoryId, subCategoryId)
        Map<String, Long> recurringBySubCategory = new HashMap<>(); // key: "catId|subCatId"
        for (RecurringTransaction rt : ds.getRecurring()) {
            if (rt.getStatus() != RecurringTransaction.Status.ACTIVE) continue;
            if (rt.getTransactionType() != Type.EXPENSE) continue;
            if (rt.getCategoryId() == null || rt.getSubCategoryId() == null) continue;
            
            String key = rt.getCategoryId() + "|" + rt.getSubCategoryId();
            // FIX Issue 1: Calculate occurrences within the actual forecast range (not +1 year)
            List<LocalDate> occurrences = getOccurrencesInRange(rt, forecastStart, forecastEnd);
            // Approximate monthly amount: sum / months in range
            int monthsInRange = (int) java.time.temporal.ChronoUnit.MONTHS.between(forecastStart, forecastEnd) + 1;
            long monthlyRecurring = monthsInRange > 0 ? (rt.getAmountPaise() * occurrences.size()) / monthsInRange : 0;
            recurringBySubCategory.merge(key, monthlyRecurring, Long::sum);
        }

        // Generate forecasts for each month in the projection range
        YearMonth startMonth = YearMonth.from(forecastStart);
        YearMonth endMonth = YearMonth.from(forecastEnd);

        for (YearMonth ym = startMonth; !ym.isAfter(endMonth); ym = ym.plusMonths(1)) {
            for (ExpensePattern pattern : patterns) {
                // Calculate forecasted amount with seasonal adjustment and trend
                long baseAmount = pattern.avgMonthlyPaise();
                double seasonalFactor = pattern.seasonalFactors().getOrDefault(ym.getMonth(), 1.0);

                // Apply trend (simple linear extrapolation), capped to ±50% to prevent
                // unbounded growth on long projections with a persistent positive slope.
                long monthsAhead = ym.getYear() * 12 + ym.getMonthValue() -
                                 (currentDate.getYear() * 12 + currentDate.getMonthValue());
                double trendMultiplier = Math.min(1.5, Math.max(0.5,
                        1.0 + (pattern.trendSlope() * monthsAhead)));

                long forecastedAmount = Math.round(baseAmount * seasonalFactor * trendMultiplier);

                // Pessimistic approach: take max of forecast vs sum of all recurring for this sub-category
                String subCatKey = pattern.categoryId() + "|" + pattern.subCategoryId();
                long recurringAmount = recurringBySubCategory.getOrDefault(subCatKey, 0L);
                long finalAmount = Math.max(forecastedAmount, recurringAmount);

                // Confidence decreases with time and fewer data points
                double confidence = Math.max(0.3, Math.min(0.9,
                    pattern.dataPoints() / 12.0 * Math.exp(-monthsAhead / 12.0)));

                // Only forecast if amount is significant (> ₹100) and confidence > 30%
                if (finalAmount > 10000 && confidence > 0.3) { // 10000 paise = ₹100
                    String method = (recurringAmount > forecastedAmount) ? "Sum of Recurring Schedule" : "Past Average";
                    forecasts.add(new ForecastedExpense(pattern.categoryId(), pattern.subCategoryId(), ym, finalAmount, confidence, method));
                }
            }
        }

        return forecasts;
    }

    /**
     * Calculates the trend slope using simple linear regression.
     */
    private double calculateTrendSlope(Map<YearMonth, Long> monthlyData) {
        List<YearMonth> sortedMonths = monthlyData.keySet().stream()
            .sorted()
            .toList();

        if (sortedMonths.size() < 2) return 0.0;

        int n = sortedMonths.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;

        for (int i = 0; i < n; i++) {
            YearMonth ym = sortedMonths.get(i);
            double x = ym.getYear() * 12 + ym.getMonthValue(); // Convert to months since epoch
            double y = monthlyData.get(ym);

            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
        double avgY = sumY / n;

        // Return slope as a fraction of average (to make it unitless)
        return avgY != 0 ? slope / avgY : 0.0;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<LocalDate> getOccurrencesInRange(RecurringTransaction rt,
                                                   LocalDate rangeStart, LocalDate rangeEnd) {
        if (rt.getStatus() != RecurringTransaction.Status.ACTIVE) return List.of();
        if (rt.getStartDate() == null) return List.of();

        List<LocalDate> result = new ArrayList<>();
        LocalDate cursor = rt.getStartDate();
        int count = 0;

        while (!cursor.isAfter(rangeEnd)) {
            int       day        = Math.min(rt.getDueDayOfMonth(), cursor.lengthOfMonth());
            LocalDate occurrence = cursor.withDayOfMonth(day);

            if (!occurrence.isBefore(rangeStart) && !occurrence.isAfter(rangeEnd)) {
                result.add(occurrence);
            }

            count++;
            if (rt.getNumberOfPayments() != null && count >= rt.getNumberOfPayments()) break;
            if (rt.getEndDate() != null && cursor.isAfter(rt.getEndDate())) break;

            cursor = advance(cursor, rt.getFrequency());
        }
        return result;
    }

    private LocalDate advance(LocalDate from, RecurringTransaction.Frequency freq) {
        return switch (freq) {
            case MONTHLY        -> from.plusMonths(1);
            case QUARTERLY      -> from.plusMonths(3);
            case ANNUALLY       -> from.plusYears(1);
            case ALTERNATE_YEAR -> from.plusYears(2);
        };
    }
}
