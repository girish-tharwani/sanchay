package com.sanchay.service;

import com.sanchay.model.EarningSource;
import com.sanchay.model.FamilyMember;
import com.sanchay.model.InvestmentAccount;
import com.sanchay.model.MarketValueEntry;
import com.sanchay.model.PlanParameters;
import com.sanchay.model.RecurringTransaction;
import com.sanchay.model.Transaction;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure calculation helper for the Financial Planning screen.
 * Keeps projection and retirement math out of JavaFX code.
 */
public class FinancialPlanningCalculator {

    public record CorpusBreakdown(
            long bankPaise, long equityPaise, long mfPaise,
            long bondsPaise, long fdPaise, long rdPaise,
            long pfPaise, long totalPaise) {}

    public record FutureEarningsBreakdown(
            long postTaxIncomePaise,
            long pfContribPaise,
            long gratuityPaise,
            long pfInterestPaise,
            long earningsSubtotalPaise,
            long bondsInterestPaise,
            long fdInterestPaise,
            long rdInterestPaise,
            long realizedRoiSubtotalPaise,
            long equityApprecPaise,
            long mfApprecPaise,
            long unrealizedRoiSubtotalPaise,
            long totalPaise) {}

    public record ExpenseSummary(long loanPaymentsPaise, long costOfLivingPaise,
                                 long majorEventsPaise, long totalPaise) {}

    public record ForecastedCorpusBreakdown(long pfBalancePaise, long stocksMfPaise,
                                            long cashBondsFdsPaise, long totalPaise) {}

    public record PostRetirementRow(
            int year, int age,
            long startingBalancePaise, long roiPaise, long taxPaise, long withdrawalPaise,
            long endingBalancePaise, boolean startingDepleted, boolean endingNegative) {}

    private static final long ROUND_10K_PAISE = 1_000_000L;
    private static final long ROUND_1_LAKH_PAISE = 10_000_000L;

    private final DataStore ds;
    private final MajorEventPlanner majorEventPlanner;

    public FinancialPlanningCalculator() {
        this(DataStore.getInstance(), new MajorEventPlanner());
    }

    public FinancialPlanningCalculator(DataStore ds) {
        this(ds, new MajorEventPlanner(ds));
    }

    public FinancialPlanningCalculator(DataStore ds, MajorEventPlanner majorEventPlanner) {
        this.ds = ds;
        this.majorEventPlanner = majorEventPlanner;
    }

    public CorpusBreakdown computeCorpusBreakdown() {
        LocalDate today = LocalDate.now();

        long bank = Math.floorDiv(
                ds.getTotalBankBalancePaise() - ds.getTotalCreditCardOutstandingPaise(),
                ROUND_10K_PAISE) * ROUND_10K_PAISE;

        long equity = 0, mf = 0, bonds = 0, fd = 0, rd = 0, pf = 0;
        for (InvestmentAccount ia : ds.getInvestmentAccounts()) {
            if (ia.getInvestmentStatus() == InvestmentAccount.InvestmentStatus.REDEEMED) continue;
            long value;
            switch (ia.getInvestmentType()) {
                case EQUITY -> {
                    MarketValueEntry mv = ds.getLatestMarketValue(ia.getId());
                    value = mv != null ? (long) (mv.getMarketValuePaise() * 0.9)
                            : ds.getInvestedPaiseAsOf(ia, today);
                    equity += Math.floorDiv(value, ROUND_10K_PAISE) * ROUND_10K_PAISE;
                }
                case MUTUAL_FUNDS -> {
                    MarketValueEntry mv = ds.getLatestMarketValue(ia.getId());
                    value = mv != null ? (long) (mv.getMarketValuePaise() * 0.9)
                            : ds.getInvestedPaiseAsOf(ia, today);
                    mf += Math.floorDiv(value, ROUND_10K_PAISE) * ROUND_10K_PAISE;
                }
                case DEBT_BONDS -> bonds += Math.floorDiv(ds.getInvestedPaiseAsOf(ia, today), ROUND_10K_PAISE) * ROUND_10K_PAISE;
                case FIXED_DEPOSIT -> fd += Math.floorDiv(ds.getInvestedPaiseAsOf(ia, today), ROUND_10K_PAISE) * ROUND_10K_PAISE;
                case RECURRING_DEPOSIT -> rd += Math.floorDiv(ds.getInvestedPaiseAsOf(ia, today), ROUND_10K_PAISE) * ROUND_10K_PAISE;
                case PROVIDENT_FUND -> pf += Math.floorDiv(ds.getInvestedPaiseAsOf(ia, today), ROUND_10K_PAISE) * ROUND_10K_PAISE;
            }
        }

        return new CorpusBreakdown(bank, equity, mf, bonds, fd, rd, pf,
                bank + equity + mf + bonds + fd + rd + pf);
    }

    public FutureEarningsBreakdown computeFutureEarnings(PlanParameters params, LocalDate selfDob) {
        LocalDate today = LocalDate.now();
        LocalDate retireDate = getRetirementDate(params, selfDob);
        double yearsToRetire = ChronoUnit.DAYS.between(today, retireDate) / 365.25;
        long totalMonths = ChronoUnit.MONTHS.between(today, retireDate);

        Set<String> earningSchedIds = collectEarningScheduleIds();
        long postTaxIncome = 0;
        for (RecurringTransaction rt : ds.getRecurring()) {
            if (rt.getTransactionType() != Transaction.Type.INCOME) continue;
            long occ = countOccurrences(rt, retireDate);
            if (occ <= 0) continue;
            long perOcc = earningSchedIds.contains(rt.getId())
                    ? rt.getAmountPaise()
                    : Math.round(rt.getAmountPaise() * (1.0 - params.preRetireTaxPct / 100.0));
            postTaxIncome += perOcc * occ;
        }

        long pfContrib = 0;
        long monthlyPfDeposit = 0;
        for (FamilyMember m : ds.getFamilyMembers()) {
            if (!m.isEarning() || !m.hasEarningsConfigured()) continue;
            for (EarningSource es : m.getEarningSources()) {
                if (es.getType() != FamilyMember.EarningType.SALARY) continue;
                if (es.getPfScheduleId() == null) continue;
                RecurringTransaction pfSched = ds.getRecurring().stream()
                        .filter(r -> es.getPfScheduleId().equals(r.getId()))
                        .findFirst().orElse(null);
                if (pfSched == null || pfSched.getAmountPaise() <= 0) continue;
                long occ = countOccurrences(pfSched, retireDate);
                pfContrib += pfSched.getAmountPaise() * occ;
                monthlyPfDeposit += pfSched.getAmountPaise();
            }
        }

        long gratuity = 0;
        if (params.employmentStartDate != null) {
            try {
                LocalDate empStart = LocalDate.parse(params.employmentStartDate);
                long serviceYears = ChronoUnit.YEARS.between(empStart, retireDate);
                if (serviceYears > 0) {
                    for (FamilyMember m : ds.getFamilyMembers()) {
                        if (!m.isEarning() || !m.hasEarningsConfigured()) continue;
                        for (EarningSource es : m.getEarningSources()) {
                            if (es.getType() != FamilyMember.EarningType.SALARY) continue;
                            if (!es.isGratuityEnabled()) continue;
                            long monthlyBasic = es.getBasicDaPaise() / 12;
                            long g = Math.round(monthlyBasic * 15.0 * serviceYears / 26.0);
                            gratuity += Math.min(g, 200_000_000L);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        long pfBalance = 0;
        for (InvestmentAccount ia : ds.getInvestmentAccounts()) {
            if (ia.getInvestmentType() == InvestmentAccount.InvestmentType.PROVIDENT_FUND) {
                pfBalance += ds.getInvestedPaiseAsOf(ia, today);
            }
        }
        long pfStartBalance = pfBalance;
        double pfMonthlyRate = params.rorPfPct / 100.0 / 12.0;
        for (long mo = 0; mo < totalMonths; mo++) {
            pfBalance += Math.round(pfBalance * pfMonthlyRate);
            pfBalance += monthlyPfDeposit;
        }
        long pfInterest = Math.max(0, pfBalance - pfStartBalance - (monthlyPfDeposit * totalMonths));

        Set<String> redeemedFdRefs = ds.getTransactions().stream()
                .filter(tx -> tx.getRedeemDetails() != null
                        && tx.getRedeemDetails().getOrgnlFDRef() != null)
                .map(tx -> tx.getRedeemDetails().getOrgnlFDRef())
                .collect(Collectors.toSet());

        long bondsInterest = 0;
        for (InvestmentAccount ia : ds.getInvestmentAccounts()) {
            if (ia.getInvestmentType() != InvestmentAccount.InvestmentType.DEBT_BONDS) continue;
            if (ia.getInvestmentStatus() == InvestmentAccount.InvestmentStatus.REDEEMED) continue;
            for (Transaction t : ds.getTransactions()) {
                if (t.getType() != Transaction.Type.INVESTMENT) continue;
                if (!ia.getId().equals(t.getToAccountId())) continue;
                if (t.getInvestmentDetails() == null) continue;
                Transaction.FdDetails fd = t.getInvestmentDetails().getFd();
                if (fd == null || fd.getMaturityAmountPaise() == null) continue;
                if (fd.getRef() != null && redeemedFdRefs.contains(fd.getRef())) continue;
                if (fd.getMaturityDate() != null && fd.getMaturityDate().isAfter(retireDate)) continue;
                bondsInterest += fd.getMaturityAmountPaise() - t.getAmountPaise();
            }
            for (RecurringTransaction rt : ds.getRecurring()) {
                if (rt.getTransactionType() != Transaction.Type.INCOME) continue;
                if (!ia.getId().equals(rt.getToAccountId())) continue;
                bondsInterest += rt.getAmountPaise() * countOccurrences(rt, retireDate);
            }
        }

        long fdInterest = 0;
        for (InvestmentAccount ia : ds.getInvestmentAccounts()) {
            if (ia.getInvestmentType() != InvestmentAccount.InvestmentType.FIXED_DEPOSIT) continue;
            for (Transaction t : ds.getTransactions()) {
                if (t.getType() != Transaction.Type.INVESTMENT) continue;
                if (!ia.getId().equals(t.getToAccountId())) continue;
                if (t.getInvestmentDetails() == null) continue;
                Transaction.FdDetails fd = t.getInvestmentDetails().getFd();
                if (fd == null || fd.getMaturityAmountPaise() == null) continue;
                if (fd.getRef() != null && redeemedFdRefs.contains(fd.getRef())) continue;
                fdInterest += fd.getMaturityAmountPaise() - t.getAmountPaise();
            }
            for (RecurringTransaction rt : ds.getRecurring()) {
                if (rt.getTransactionType() != Transaction.Type.INCOME) continue;
                if (!ia.getId().equals(rt.getToAccountId())) continue;
                fdInterest += rt.getAmountPaise() * countOccurrences(rt, retireDate);
            }
        }
        fdInterest = Math.round(fdInterest * (1.0 - params.preRetireTaxPct / 100.0));

        Set<String> rdAccountIds = ds.getInvestmentAccounts().stream()
                .filter(ia -> ia.getInvestmentType() == InvestmentAccount.InvestmentType.RECURRING_DEPOSIT
                        && ia.getInvestmentStatus() != InvestmentAccount.InvestmentStatus.REDEEMED)
                .map(InvestmentAccount::getId)
                .collect(Collectors.toSet());

        long rdInterest = 0;
        for (RecurringTransaction rt : ds.getRecurring()) {
            if (rt.getTransactionType() != Transaction.Type.INVESTMENT) continue;
            if (!rdAccountIds.contains(rt.getToAccountId())) continue;
            if (rt.getRdMaturityAmountPaise() <= 0) continue;
            long numPayments;
            if (rt.getNumberOfPayments() != null && rt.getNumberOfPayments() > 0) {
                numPayments = rt.getNumberOfPayments();
            } else if (rt.getStartDate() != null && rt.getMaturityDate() != null) {
                numPayments = ChronoUnit.MONTHS.between(rt.getStartDate(), rt.getMaturityDate());
            } else {
                continue;
            }
            long totalPrincipal = numPayments * rt.getAmountPaise();
            rdInterest += Math.max(0, rt.getRdMaturityAmountPaise() - totalPrincipal);
        }
        rdInterest = Math.round(rdInterest * (1.0 - params.preRetireTaxPct / 100.0));

        long equityPv = 0;
        for (InvestmentAccount ia : ds.getInvestmentAccounts()) {
            if (ia.getInvestmentType() != InvestmentAccount.InvestmentType.EQUITY) continue;
            if (ia.getInvestmentStatus() == InvestmentAccount.InvestmentStatus.REDEEMED) continue;
            MarketValueEntry mv = ds.getLatestMarketValue(ia.getId());
            equityPv += mv != null ? mv.getMarketValuePaise() : ds.getInvestedPaiseAsOf(ia, today);
        }
        double equityRate = params.rorEquitiesPct / 100.0;
        long equityApprec = Math.round(equityPv * Math.pow(1 + equityRate, yearsToRetire))
                + computeSipFv(params.monthlySipEquityPaise, equityRate, totalMonths)
                - equityPv
                - params.monthlySipEquityPaise * totalMonths;

        long mfPv = 0;
        for (InvestmentAccount ia : ds.getInvestmentAccounts()) {
            if (ia.getInvestmentType() != InvestmentAccount.InvestmentType.MUTUAL_FUNDS) continue;
            if (ia.getInvestmentStatus() == InvestmentAccount.InvestmentStatus.REDEEMED) continue;
            MarketValueEntry mv = ds.getLatestMarketValue(ia.getId());
            mfPv += mv != null ? mv.getMarketValuePaise() : ds.getInvestedPaiseAsOf(ia, today);
        }
        double mfRate = params.rorMfPct / 100.0;
        long mfApprec = Math.round(mfPv * Math.pow(1 + mfRate, yearsToRetire))
                + computeSipFv(params.monthlySipMfPaise, mfRate, totalMonths)
                - mfPv
                - params.monthlySipMfPaise * totalMonths;

        long rIncome = floorRound(postTaxIncome, ROUND_10K_PAISE);
        long rPfContrib = floorRound(pfContrib, ROUND_10K_PAISE);
        long rGratuity = floorRound(gratuity, ROUND_10K_PAISE);
        long rPfInt = floorRound(pfInterest, ROUND_10K_PAISE);
        long rBondsInt = floorRound(Math.max(0, bondsInterest), ROUND_10K_PAISE);
        long rFdInt = floorRound(Math.max(0, fdInterest), ROUND_10K_PAISE);
        long rRdInt = floorRound(Math.max(0, rdInterest), ROUND_10K_PAISE);
        long rEquityApprec = floorRound(Math.max(0, equityApprec), ROUND_10K_PAISE);
        long rMfApprec = floorRound(Math.max(0, mfApprec), ROUND_10K_PAISE);

        long earnSub = rIncome + rPfContrib + rGratuity + rPfInt;
        long realizedSub = rBondsInt + rFdInt + rRdInt;
        long unrlzdSub = rEquityApprec + rMfApprec;
        long total = earnSub + realizedSub + unrlzdSub;

        return new FutureEarningsBreakdown(
                rIncome, rPfContrib, rGratuity, rPfInt, earnSub,
                rBondsInt, rFdInt, rRdInt, realizedSub,
                rEquityApprec, rMfApprec, unrlzdSub, total);
    }

    public long computeMajorEventsKpi(PlanParameters params, LocalDate selfDob) {
        return majorEventPlanner.computeMajorEventsKpi(params, getRetirementDate(params, selfDob));
    }

    public long computeLoanPayments(LocalDate retireDate) {
        long total = 0;
        for (RecurringTransaction rt : ds.getRecurring()) {
            if (rt.getTransactionType() != Transaction.Type.LOAN_PAYMENT) continue;
            long occ = countOccurrences(rt, retireDate);
            total += rt.getAmountPaise() * occ;
        }
        return total;
    }

    public ExpenseSummary computeExpenseSummary(PlanParameters params, LocalDate selfDob) {
        LocalDate retireDate = getRetirementDate(params, selfDob);
        LocalDate today = LocalDate.now();
        double yearsToRetire = ChronoUnit.DAYS.between(today, retireDate) / 365.25;
        double inflationRate = params.inflationPct / 100.0;
        long costOfLiving = roundUpTo10k((inflationRate == 0 || yearsToRetire <= 0)
                ? Math.round(params.costOfLivingPaise * yearsToRetire)
                : Math.round(params.costOfLivingPaise
                * (Math.pow(1 + inflationRate, yearsToRetire) - 1) / inflationRate));

        long loanPayments = roundUpTo10k(computeLoanPayments(retireDate));
        long majorEventsNet = roundUpTo10k(computeMajorEventsKpi(params, selfDob));
        return new ExpenseSummary(loanPayments, costOfLiving, majorEventsNet,
                costOfLiving + loanPayments + majorEventsNet);
    }

    public ForecastedCorpusBreakdown computeForecastedCorpusBreakdown(PlanParameters params,
                                                                      LocalDate selfDob,
                                                                      CorpusBreakdown corpusBreakdown,
                                                                      FutureEarningsBreakdown futureEarnings) {
        LocalDate retireDate = getRetirementDate(params, selfDob);
        LocalDate today = LocalDate.now();
        long totalMonths = ChronoUnit.MONTHS.between(today, retireDate);
        ExpenseSummary expenses = computeExpenseSummary(params, selfDob);

        long pfBalance = corpusBreakdown.pfPaise()
                + futureEarnings.pfContribPaise()
                + futureEarnings.pfInterestPaise();

        long stocksMf = corpusBreakdown.equityPaise()
                + corpusBreakdown.mfPaise()
                + futureEarnings.equityApprecPaise()
                + params.monthlySipEquityPaise * totalMonths
                + futureEarnings.mfApprecPaise()
                + params.monthlySipMfPaise * totalMonths;

        long cashBondsFds = corpusBreakdown.bankPaise()
                + corpusBreakdown.bondsPaise()
                + corpusBreakdown.fdPaise()
                + corpusBreakdown.rdPaise()
                + futureEarnings.bondsInterestPaise()
                + futureEarnings.fdInterestPaise()
                + futureEarnings.rdInterestPaise()
                + futureEarnings.postTaxIncomePaise()
                + futureEarnings.gratuityPaise()
                - expenses.totalPaise();

        return new ForecastedCorpusBreakdown(pfBalance, stocksMf, cashBondsFds,
                pfBalance + stocksMf + cashBondsFds);
    }

    public long computeRequiredCorpusPaise(PlanParameters params, LocalDate selfDob) {
        int retirementAge = getRetirementAgeYears(params, selfDob);
        int totalYears = params.lifeExpectancy - retirementAge;
        if (totalYears <= 0) return 0;

        long low = 0;
        long high = Math.max(1, roundUpTo1Lakh(params.costOfLivingPaise * totalYears));
        while (!sustainsPostRetirement(params, selfDob, high)) {
            low = high;
            if (high > Long.MAX_VALUE / 2) return high;
            high = Math.max(high + 1, high * 2);
        }

        while (low + 1 < high) {
            long mid = low + (high - low) / 2;
            if (sustainsPostRetirement(params, selfDob, mid)) {
                high = mid;
            } else {
                low = mid;
            }
        }
        return high;
    }

    public List<PostRetirementRow> computePostRetirementRows(PlanParameters params,
                                                             LocalDate selfDob,
                                                             long startingBalancePaise) {
        LocalDate retireDate = getRetirementDate(params, selfDob);
        int retirementYear = retireDate.getYear();
        int retirementAge = getRetirementAgeYears(params, selfDob);
        int totalYears = params.lifeExpectancy - retirementAge;
        double ror = params.rorPostRetirePct / 100.0;
        double taxRate = params.postRetireTaxPct / 100.0;
        double inflationRate = params.inflationPct / 100.0;
        double yearsToRetire = ChronoUnit.DAYS.between(LocalDate.now(), retireDate) / 365.25;
        double colAtRetirement = params.costOfLivingPaise * Math.pow(1 + inflationRate, yearsToRetire);
        long[] annualLoanPayments = computeAnnualPostRetirementLoanPayments(retireDate, totalYears);

        List<PostRetirementRow> rows = new ArrayList<>();
        long balance = startingBalancePaise;
        for (int i = 0; i < totalYears; i++) {
            int year = retirementYear + i;
            int age = retirementAge + i;
            long withdrawal = roundUpTo10k(Math.round(colAtRetirement * Math.pow(1 + inflationRate, i)))
                    + roundUpTo10k(annualLoanPayments[i]);

            if (balance > 0) {
                long postWithdrawalBalance = balance - withdrawal;
                long roi = roundDownTo10k(Math.round(Math.max(0, postWithdrawalBalance) * ror));
                long tax = roundUpTo10k(Math.round(roi * taxRate));
                long endingBalance = postWithdrawalBalance + roi - tax;
                rows.add(new PostRetirementRow(
                        year, age, balance, roi, tax, withdrawal,
                        endingBalance, false, endingBalance < 0));
                balance = endingBalance;
            } else {
                long endingBalance = balance - withdrawal;
                rows.add(new PostRetirementRow(
                        year, age, balance, 0, 0, withdrawal,
                        endingBalance, true, endingBalance < 0));
                balance = endingBalance;
            }
        }
        return rows;
    }

    private long[] computeAnnualPostRetirementLoanPayments(LocalDate retireDate, int totalYears) {
        long[] annual = new long[totalYears];
        for (RecurringTransaction rt : ds.getRecurring()) {
            if (rt.getTransactionType() != Transaction.Type.LOAN_PAYMENT) continue;
            for (int i = 0; i < totalYears; i++) {
                LocalDate yearStart = retireDate.plusYears(i);
                LocalDate yearEnd = retireDate.plusYears(i + 1).minusDays(1);
                List<LocalDate> occurrences = getOccurrencesInRange(rt, yearStart, yearEnd);
                annual[i] += rt.getAmountPaise() * occurrences.size();
            }
        }
        return annual;
    }

    public LocalDate getRetirementDate(PlanParameters params, LocalDate selfDob) {
        LocalDate retireDate = (params.retirementDate != null)
                ? LocalDate.parse(params.retirementDate)
                : selfDob.plusYears(params.retirementAge);
        return retireDate.isBefore(LocalDate.now()) ? LocalDate.now() : retireDate;
    }

    public int getRetirementAgeYears(PlanParameters params, LocalDate selfDob) {
        LocalDate retireDate = getRetirementDate(params, selfDob);
        return (int) Math.round(ChronoUnit.DAYS.between(selfDob, retireDate) / 365.25);
    }

    private boolean sustainsPostRetirement(PlanParameters params, LocalDate selfDob,
                                           long startingBalancePaise) {
        LocalDate retireDate = getRetirementDate(params, selfDob);
        int retirementAge = getRetirementAgeYears(params, selfDob);
        int totalYears = params.lifeExpectancy - retirementAge;
        if (totalYears <= 0) return true;

        double ror = params.rorPostRetirePct / 100.0;
        double taxRate = params.postRetireTaxPct / 100.0;
        double inflationRate = params.inflationPct / 100.0;
        double yearsToRetire = ChronoUnit.DAYS.between(LocalDate.now(), retireDate) / 365.25;
        double colAtRetirement = params.costOfLivingPaise * Math.pow(1 + inflationRate, yearsToRetire);
        long[] annualLoanPayments = computeAnnualPostRetirementLoanPayments(retireDate, totalYears);

        long balance = startingBalancePaise;
        for (int i = 0; i < totalYears; i++) {
            long withdrawal = roundUpTo10k(Math.round(colAtRetirement * Math.pow(1 + inflationRate, i)))
                    + roundUpTo10k(annualLoanPayments[i]);
            if (balance < withdrawal) return false;

            long postWithdrawalBalance = balance - withdrawal;
            long roi = roundDownTo10k(Math.round(postWithdrawalBalance * ror));
            long tax = roundUpTo10k(Math.round(roi * taxRate));
            balance = postWithdrawalBalance + roi - tax;
        }
        return true;
    }

    private Set<String> collectEarningScheduleIds() {
        Set<String> ids = new HashSet<>();
        for (FamilyMember m : ds.getFamilyMembers()) {
            if (!m.isEarning() || !m.hasEarningsConfigured()) continue;
            for (EarningSource es : m.getEarningSources()) {
                if (es.getRecurringScheduleId() != null) {
                    ids.add(es.getRecurringScheduleId());
                }
            }
        }
        return ids;
    }

    private long countOccurrences(RecurringTransaction rt, LocalDate until) {
        if (rt.getAmountPaise() <= 0 || until == null) return 0;
        return getOccurrencesInRange(rt, LocalDate.now(), until).size();
    }

    private List<LocalDate> getOccurrencesInRange(RecurringTransaction rt,
                                                  LocalDate rangeStart,
                                                  LocalDate rangeEnd) {
        if (rt.getStatus() != RecurringTransaction.Status.ACTIVE) return List.of();
        if (rt.getStartDate() == null) return List.of();
        if (rangeStart == null || rangeEnd == null || rangeStart.isAfter(rangeEnd)) return List.of();

        List<LocalDate> result = new ArrayList<>();
        LocalDate cursor = rt.getLastRecordedDate() != null
                ? advance(rt.getLastRecordedDate(), rt.getFrequency())
                : rt.getStartDate();
        int generated = rt.getPaymentsMade();
        Integer numberOfPayments = rt.getNumberOfPayments();

        if (numberOfPayments != null && generated >= numberOfPayments) return List.of();

        while (!cursor.isAfter(rangeEnd)) {
            int day = Math.min(rt.getDueDayOfMonth(), cursor.lengthOfMonth());
            LocalDate occurrence = cursor.withDayOfMonth(day);

             if (rt.getEndDate() != null && occurrence.isAfter(rt.getEndDate())) break;

            if (!occurrence.isBefore(rangeStart) && !occurrence.isAfter(rangeEnd)) {
                result.add(occurrence);
            }

            generated++;
            if (numberOfPayments != null && generated >= numberOfPayments) break;

            cursor = advance(cursor, rt.getFrequency());
        }
        return result;
    }

    private LocalDate advance(LocalDate from, RecurringTransaction.Frequency freq) {
        return switch (freq) {
            case MONTHLY -> from.plusMonths(1);
            case QUARTERLY -> from.plusMonths(3);
            case HALF_YEARLY -> from.plusMonths(6);
            case ANNUALLY -> from.plusYears(1);
            case ALTERNATE_YEAR -> from.plusYears(2);
        };
    }

    private long computeSipFv(long monthlyPaise, double annualRate, long months) {
        if (monthlyPaise <= 0 || months <= 0) return 0;
        double r = annualRate / 12.0;
        if (r == 0) return monthlyPaise * months;
        return Math.round(monthlyPaise * (Math.pow(1 + r, months) - 1) / r);
    }

    private long floorRound(long value, long unit) {
        return Math.floorDiv(value, unit) * unit;
    }

    private static long roundUpTo10k(long paise) {
        if (paise <= 0) return 0;
        return ((paise + ROUND_10K_PAISE - 1) / ROUND_10K_PAISE) * ROUND_10K_PAISE;
    }

    private static long roundDownTo10k(long paise) {
        if (paise <= 0) return 0;
        return (paise / ROUND_10K_PAISE) * ROUND_10K_PAISE;
    }

    private static long roundUpTo1Lakh(long paise) {
        if (paise <= 0) return 0;
        return ((paise + ROUND_1_LAKH_PAISE - 1) / ROUND_1_LAKH_PAISE) * ROUND_1_LAKH_PAISE;
    }
}
