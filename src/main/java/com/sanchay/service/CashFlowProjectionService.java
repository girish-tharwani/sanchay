package com.sanchay.service;

import com.sanchay.model.*;
import com.sanchay.model.Transaction.Type;

import java.time.LocalDate;
import java.time.YearMonth;
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

    public record ProjectionResult(
            List<Account>                      accounts,
            List<ProjectionPoint>              totalSeries,
            Map<String, List<ProjectionPoint>> accountSeries,
            long                               totalProjectedIncomePaise,
            long                               totalProjectedExpensesPaise,
            List<String>                       warnings
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
                warnings
        );
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
