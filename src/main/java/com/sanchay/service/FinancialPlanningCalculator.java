package com.sanchay.service;

import com.sanchay.model.EarningSource;
import com.sanchay.model.FamilyMember;
import com.sanchay.model.InvestmentAccount;
import com.sanchay.model.MajorEvent;
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
            long scheduledInflowsPaise, long scheduledMajorEventsPaise, long endingBalancePaise,
            boolean startingDepleted, boolean endingNegative) {}

    private static final long ROUND_10K_PAISE = 1_000_000L;
    private static final long ROUND_1_LAKH_PAISE = 10_000_000L;

    // Three-phase retirement spending model (Blanchett / Morningstar):
    //   Phase 1 — active retirement  (< SLOW_GO_AGE)      : full inflation
    //   Phase 2 — slow-go retirement (< NO_GO_AGE)        : inflation − SLOW_GO_REDUCTION
    //   Phase 3 — late / healthcare  (≥ NO_GO_AGE)        : full inflation
    public static final int    SPENDING_SLOW_GO_AGE      = 73;
    public static final int    SPENDING_NO_GO_AGE        = 83;
    public static final double SPENDING_SLOW_GO_REDUCTION = 0.015; // 1.5 % per year
    public static final double SPENDING_SLOW_GO_INFLATION_ADJUSTMENT = -0.015;
    public static final double SPENDING_HEALTHCARE_INFLATION_ADJUSTMENT = 0.0;

    public int getSpendingSlowGoAge(PlanParameters params) {
        return params != null && params.spendingSlowGoAge > 0
                ? params.spendingSlowGoAge
                : SPENDING_SLOW_GO_AGE;
    }

    public int getSpendingNoGoAge(PlanParameters params) {
        int slowGoAge = getSpendingSlowGoAge(params);
        int configured = params != null && params.spendingNoGoAge > slowGoAge
                ? params.spendingNoGoAge
                : SPENDING_NO_GO_AGE;
        return Math.max(configured, slowGoAge + 1);
    }

    public double getSpendingSlowGoReductionRate(PlanParameters params) {
        return params != null && params.spendingSlowGoReductionPct >= 0
                ? params.spendingSlowGoReductionPct / 100.0
                : SPENDING_SLOW_GO_REDUCTION;
    }

    public double getSpendingSlowGoInflationAdjustmentRate(PlanParameters params) {
        if (params == null) return SPENDING_SLOW_GO_INFLATION_ADJUSTMENT;
        if (params.spendingSlowGoInflationAdjustmentPct != null) {
            return params.spendingSlowGoInflationAdjustmentPct / 100.0;
        }
        if (params.spendingSlowGoReductionPct >= 0) {
            return -params.spendingSlowGoReductionPct / 100.0;
        }
        return SPENDING_SLOW_GO_INFLATION_ADJUSTMENT;
    }

    public double getSpendingHealthcareInflationAdjustmentRate(PlanParameters params) {
        if (params == null || params.spendingHealthcareInflationAdjustmentPct == null) {
            return SPENDING_HEALTHCARE_INFLATION_ADJUSTMENT;
        }
        return params.spendingHealthcareInflationAdjustmentPct / 100.0;
    }

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
            if (rt.getSourceInvestment() != null) continue; // interest income — handled in bond/FD buckets
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
        for (FamilyMember m : ds.getFamilyMembers()) {
            if (m.getRelationship() != FamilyMember.Relationship.SELF) continue;
            if (!m.isEarning() || !m.hasEarningsConfigured()) continue;
            for (EarningSource es : m.getEarningSources()) {
                if (es.getType() != FamilyMember.EarningType.SALARY) continue;
                if (!es.isGratuityEnabled()) continue;
                LocalDate startDate = es.getStartDate();
                if (startDate == null || !startDate.isBefore(retireDate)) continue;
                long serviceYears = ChronoUnit.YEARS.between(startDate, retireDate);
                if (serviceYears <= 0) continue;
                long monthlyBasic = es.getBasicDaPaise() / 12;
                long g = Math.round(monthlyBasic * 15.0 * serviceYears / 26.0);
                gratuity += g;
            }
        }
        gratuity = Math.min(gratuity, 200_000_000L);

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

        Set<String> redeemedFdRefs = collectRedeemedFdRefs();

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
                if (rt.getSourceInvestment() == null) continue;
                if (!ia.getId().equals(rt.getSourceInvestment().getSrcAccount())) continue;
                bondsInterest += rt.getAmountPaise() * countOccurrences(rt, retireDate);
            }
        }
        bondsInterest = Math.round(bondsInterest * (1.0 - params.preRetireTaxPct / 100.0));

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
                if (fd.getMaturityDate() != null && fd.getMaturityDate().isAfter(retireDate)) continue;
                fdInterest += fd.getMaturityAmountPaise() - t.getAmountPaise();
            }
            for (RecurringTransaction rt : ds.getRecurring()) {
                if (rt.getTransactionType() != Transaction.Type.INCOME) continue;
                if (rt.getSourceInvestment() == null) continue;
                if (!ia.getId().equals(rt.getSourceInvestment().getSrcAccount())) continue;
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
            LocalDate maturityDate = resolveRdMaturityDate(rt);
            if (maturityDate != null && maturityDate.isAfter(retireDate)) continue;
            rdInterest += computeRdMaturityGain(rt);
        }
        rdInterest = Math.round(rdInterest * (1.0 - params.preRetireTaxPct / 100.0));

        long equityPv = 0;
        for (InvestmentAccount ia : ds.getInvestmentAccounts()) {
            if (ia.getInvestmentType() != InvestmentAccount.InvestmentType.EQUITY) continue;
            if (ia.getInvestmentStatus() == InvestmentAccount.InvestmentStatus.REDEEMED) continue;
            MarketValueEntry mv = ds.getLatestMarketValue(ia.getId());
            long value = mv != null ? (long) (mv.getMarketValuePaise() * 0.9)
                    : ds.getInvestedPaiseAsOf(ia, today);
            equityPv += value;
        }
        double equityRate = params.rorEquitiesPct / 100.0;
        // SIP projections start from next month to avoid double-counting current month's partial investments
        // which are already included in equityPv (current corpus)
        long sipMonths = Math.max(0, totalMonths - 1);
        long equityApprec = Math.round(equityPv * Math.pow(1 + equityRate, yearsToRetire))
                + computeSipFv(params.monthlySipEquityPaise, equityRate, sipMonths)
                - equityPv
                - params.monthlySipEquityPaise * sipMonths;

        long mfPv = 0;
        for (InvestmentAccount ia : ds.getInvestmentAccounts()) {
            if (ia.getInvestmentType() != InvestmentAccount.InvestmentType.MUTUAL_FUNDS) continue;
            if (ia.getInvestmentStatus() == InvestmentAccount.InvestmentStatus.REDEEMED) continue;
            MarketValueEntry mv = ds.getLatestMarketValue(ia.getId());
            long value = mv != null ? (long) (mv.getMarketValuePaise() * 0.9)
                    : ds.getInvestedPaiseAsOf(ia, today);
            mfPv += value;
        }
        double mfRate = params.rorMfPct / 100.0;
        long mfApprec = Math.round(mfPv * Math.pow(1 + mfRate, yearsToRetire))
                + computeSipFv(params.monthlySipMfPaise, mfRate, sipMonths)
                - mfPv
                - params.monthlySipMfPaise * sipMonths;

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
        // SIP projections start from next month to avoid double-counting current month's partial investments
        long sipMonths = Math.max(0, totalMonths - 1);
        ExpenseSummary expenses = computeExpenseSummary(params, selfDob);

        long pfBalance = corpusBreakdown.pfPaise()
                + futureEarnings.pfContribPaise()
                + futureEarnings.pfInterestPaise();

        long stocksMf = corpusBreakdown.equityPaise()
                + corpusBreakdown.mfPaise()
                + futureEarnings.equityApprecPaise()
                + params.monthlySipEquityPaise * sipMonths
                + futureEarnings.mfApprecPaise()
                + params.monthlySipMfPaise * sipMonths;

        long cashBondsFds = corpusBreakdown.bankPaise()
                + corpusBreakdown.bondsPaise()
                + corpusBreakdown.fdPaise()
                + corpusBreakdown.rdPaise()
                + futureEarnings.bondsInterestPaise()
                + futureEarnings.fdInterestPaise()
                + futureEarnings.rdInterestPaise()
                + futureEarnings.postTaxIncomePaise()
                + futureEarnings.gratuityPaise()
                - expenses.totalPaise()
                - (params.monthlySipEquityPaise * sipMonths)
                - (params.monthlySipMfPaise * sipMonths);

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
                                                             long startingBalancePaise,
                                                             boolean includeScheduledInflows) {
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
        long[] annualScheduledInflows = includeScheduledInflows
                ? computeAnnualPostRetirementInvestmentInflows(retireDate, totalYears, params)
                : new long[totalYears];
        long[] annualScheduledMajorEvents = includeScheduledInflows
                ? computeAnnualPostRetirementMajorEvents(retireDate, totalYears, params)
                : new long[totalYears];

        List<PostRetirementRow> rows = new ArrayList<>();
        long balance = startingBalancePaise;
        for (int i = 0; i < totalYears; i++) {
            int year = retirementYear + i;
            int age = retirementAge + i;
            long withdrawal = roundUpTo10k(Math.round(colAtRetirement * colGrowthMultiplier(params, retirementAge, inflationRate, i)))
                    + roundUpTo10k(annualLoanPayments[i]);
            long scheduledInflows = roundDownTo10k(annualScheduledInflows[i]);
            long scheduledMajorEvents = roundUpTo10k(annualScheduledMajorEvents[i]);

            if (balance > 0) {
                long postWithdrawalBalance = balance - withdrawal - scheduledMajorEvents;
                long roi = roundDownTo10k(Math.round(Math.max(0, postWithdrawalBalance) * ror));
                long tax = roundUpTo10k(Math.round(roi * taxRate));
                long endingBalance = postWithdrawalBalance + scheduledInflows + roi - tax;
                rows.add(new PostRetirementRow(
                        year, age, balance, roi, tax, withdrawal,
                        scheduledInflows, scheduledMajorEvents, endingBalance, false, endingBalance < 0));
                balance = endingBalance;
            } else {
                long endingBalance = balance - withdrawal - scheduledMajorEvents + scheduledInflows;
                rows.add(new PostRetirementRow(
                        year, age, balance, 0, 0, withdrawal,
                        scheduledInflows, scheduledMajorEvents, endingBalance, true, endingBalance < 0));
                balance = endingBalance;
            }
        }
        return rows;
    }

    private long[] computeAnnualPostRetirementMajorEvents(LocalDate retireDate,
                                                          int totalYears,
                                                          PlanParameters params) {
        long[] annual = new long[totalYears];
        if (params == null || params.majorEvents == null) return annual;

        for (MajorEvent event : params.majorEvents) {
            addAnnualPostRetirementMajorEventForecast(annual, retireDate, totalYears, event);
        }
        return annual;
    }

    private void addAnnualPostRetirementMajorEventForecast(long[] annual,
                                                           LocalDate retireDate,
                                                           int totalYears,
                                                           MajorEvent event) {
        if (event == null || event.getAmountPaise() <= 0 || retireDate == null || totalYears <= 0) return;

        if (event.getType() == MajorEvent.EventType.ONE_TIME) {
            LocalDate startDate = parseDate(event.getStartDate());
            if (startDate != null && startDate.isAfter(retireDate)) {
                addAmountToRetirementYear(annual, retireDate, totalYears, startDate, event.getAmountPaise());
            }
            return;
        }

        LocalDate projectionEnd = retireDate.plusYears(totalYears);
        LocalDate endDate = parseDate(event.getEndDate());
        if (endDate != null && !endDate.isAfter(retireDate)) return;
        if (endDate != null && endDate.isBefore(projectionEnd)) {
            projectionEnd = endDate;
        }

        for (int i = 0; i < totalYears; i++) {
            LocalDate yearStart = retireDate.plusYears(i);
            LocalDate yearEndExclusive = retireDate.plusYears(i + 1);
            if (yearEndExclusive.isAfter(projectionEnd)) {
                yearEndExclusive = projectionEnd;
            }

            long occurrences = countMajorEventOccurrences(event, yearStart, yearEndExclusive);
            annual[i] += occurrences * event.getAmountPaise();
        }
    }

    private long countMajorEventOccurrences(MajorEvent event,
                                            LocalDate rangeStart,
                                            LocalDate rangeEndExclusive) {
        if (rangeStart == null || rangeEndExclusive == null || !rangeStart.isBefore(rangeEndExclusive)) {
            return 0;
        }

        LocalDate occurrence = parseDate(event.getStartDate());
        if (occurrence == null) {
            occurrence = rangeStart;
        }

        long count = 0;
        while (occurrence.isBefore(rangeEndExclusive)) {
            if (!occurrence.isBefore(rangeStart)) {
                count++;
            }
            occurrence = advance(occurrence, event.getFrequency());
        }
        return count;
    }

    private LocalDate advance(LocalDate from, MajorEvent.Frequency frequency) {
        return switch (frequency != null ? frequency : MajorEvent.Frequency.MONTHLY) {
            case MONTHLY -> from.plusMonths(1);
            case QUARTERLY -> from.plusMonths(3);
            case YEARLY -> from.plusYears(1);
        };
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

    private long[] computeAnnualPostRetirementInvestmentInflows(LocalDate retireDate,
                                                                int totalYears,
                                                                PlanParameters params) {
        long[] annual = new long[totalYears];
        Set<String> redeemedFdRefs = collectRedeemedFdRefs();

        addAnnualPostRetirementBondAndFdInflows(annual, retireDate, totalYears, redeemedFdRefs, params);
        addAnnualPostRetirementRdInflows(annual, retireDate, totalYears, params);
        return annual;
    }

    private void addAnnualPostRetirementBondAndFdInflows(long[] annual,
                                                         LocalDate retireDate,
                                                         int totalYears,
                                                         Set<String> redeemedFdRefs,
                                                         PlanParameters params) {
        for (InvestmentAccount ia : ds.getInvestmentAccounts()) {
            if (ia.getInvestmentStatus() == InvestmentAccount.InvestmentStatus.REDEEMED) continue;
            if (ia.getInvestmentType() != InvestmentAccount.InvestmentType.DEBT_BONDS
                    && ia.getInvestmentType() != InvestmentAccount.InvestmentType.FIXED_DEPOSIT) {
                continue;
            }

            for (Transaction t : ds.getTransactions()) {
                if (t.getType() != Transaction.Type.INVESTMENT) continue;
                if (!ia.getId().equals(t.getToAccountId())) continue;
                if (t.getInvestmentDetails() == null) continue;
                Transaction.FdDetails fd = t.getInvestmentDetails().getFd();
                if (fd == null || fd.getMaturityAmountPaise() == null) continue;
                if (fd.getRef() != null && redeemedFdRefs.contains(fd.getRef())) continue;
                if (fd.getMaturityDate() == null || !fd.getMaturityDate().isAfter(retireDate)) continue;

                long gain = Math.max(0, fd.getMaturityAmountPaise() - t.getAmountPaise());
                long inflow = fd.getMaturityAmountPaise();
                inflow -= Math.round(gain * params.postRetireTaxPct / 100.0);
                addAmountToRetirementYear(annual, retireDate, totalYears, fd.getMaturityDate(), Math.max(0, inflow));
            }
        }

        for (InvestmentAccount ia : ds.getInvestmentAccounts()) {
            if (ia.getInvestmentStatus() == InvestmentAccount.InvestmentStatus.REDEEMED) continue;
            if (ia.getInvestmentType() != InvestmentAccount.InvestmentType.DEBT_BONDS
                    && ia.getInvestmentType() != InvestmentAccount.InvestmentType.FIXED_DEPOSIT) {
                continue;
            }

            double taxMultiplier = 1.0 - params.postRetireTaxPct / 100.0;
            for (RecurringTransaction rt : ds.getRecurring()) {
                if (rt.getTransactionType() != Transaction.Type.INCOME) continue;
                if (rt.getSourceInvestment() == null) continue;
                if (!ia.getId().equals(rt.getSourceInvestment().getSrcAccount())) continue;

                for (int i = 0; i < totalYears; i++) {
                    LocalDate yearStart = retireDate.plusYears(i);
                    LocalDate yearEnd = retireDate.plusYears(i + 1).minusDays(1);
                    long occurrences = getOccurrencesInRange(rt, yearStart, yearEnd).size();
                    if (occurrences <= 0) continue;
                    annual[i] += Math.max(0, Math.round(rt.getAmountPaise() * taxMultiplier) * occurrences);
                }
            }
        }
    }

    private void addAnnualPostRetirementRdInflows(long[] annual,
                                                  LocalDate retireDate,
                                                  int totalYears,
                                                  PlanParameters params) {
        Set<String> rdAccountIds = ds.getInvestmentAccounts().stream()
                .filter(ia -> ia.getInvestmentType() == InvestmentAccount.InvestmentType.RECURRING_DEPOSIT
                        && ia.getInvestmentStatus() != InvestmentAccount.InvestmentStatus.REDEEMED)
                .map(InvestmentAccount::getId)
                .collect(Collectors.toSet());

        for (RecurringTransaction rt : ds.getRecurring()) {
            if (rt.getTransactionType() != Transaction.Type.INVESTMENT) continue;
            if (!rdAccountIds.contains(rt.getToAccountId())) continue;
            if (rt.getRdMaturityAmountPaise() <= 0) continue;

            LocalDate maturityDate = resolveRdMaturityDate(rt);
            if (maturityDate == null || !maturityDate.isAfter(retireDate)) continue;

            long gain = computeRdMaturityGain(rt);
            long inflow = rt.getRdMaturityAmountPaise() - Math.round(gain * params.postRetireTaxPct / 100.0);
            addAmountToRetirementYear(annual, retireDate, totalYears, maturityDate, Math.max(0, inflow));
        }
    }

    private LocalDate resolveRdMaturityDate(RecurringTransaction rt) {
        if (rt.getMaturityDate() != null) {
            return rt.getMaturityDate();
        }
        return rt.getLastPaymentDate();
    }

    private long computeRdMaturityGain(RecurringTransaction rt) {
        long numPayments;
        if (rt.getNumberOfPayments() != null && rt.getNumberOfPayments() > 0) {
            numPayments = rt.getNumberOfPayments();
        } else if (rt.getStartDate() != null && rt.getMaturityDate() != null) {
            numPayments = ChronoUnit.MONTHS.between(rt.getStartDate(), rt.getMaturityDate());
        } else {
            return 0;
        }
        long totalPrincipal = numPayments * rt.getAmountPaise();
        return Math.max(0, rt.getRdMaturityAmountPaise() - totalPrincipal);
    }

    private void addAmountToRetirementYear(long[] annual,
                                            LocalDate retireDate,
                                            int totalYears,
                                            LocalDate eventDate,
                                            long amountPaise) {
        if (eventDate == null || amountPaise <= 0) return;
        if (!eventDate.isAfter(retireDate)) return;

        int yearIndex = (int) ChronoUnit.YEARS.between(retireDate, eventDate);
        if (yearIndex < 0 || yearIndex >= totalYears) return;

        annual[yearIndex] += amountPaise;
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(date);
        } catch (Exception ignored) {
            return null;
        }
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
        long[] annualScheduledInflows = computeAnnualPostRetirementInvestmentInflows(retireDate, totalYears, params);
        long[] annualScheduledMajorEvents = computeAnnualPostRetirementMajorEvents(retireDate, totalYears, params);

        long balance = startingBalancePaise;
        for (int i = 0; i < totalYears; i++) {
            long withdrawal = roundUpTo10k(Math.round(colAtRetirement * colGrowthMultiplier(params, retirementAge, inflationRate, i)))
                    + roundUpTo10k(annualLoanPayments[i]);
            long scheduledMajorEvents = roundUpTo10k(annualScheduledMajorEvents[i]);
            if (balance < withdrawal + scheduledMajorEvents) return false;

            long scheduledInflows = roundDownTo10k(annualScheduledInflows[i]);
            long postWithdrawalBalance = balance - withdrawal - scheduledMajorEvents;
            long roi = roundDownTo10k(Math.round(postWithdrawalBalance * ror));
            long tax = roundUpTo10k(Math.round(roi * taxRate));
            balance = postWithdrawalBalance + scheduledInflows + roi - tax;
        }
        return true;
    }

    /**
     * Returns the cumulative cost-of-living multiplier for a given year into retirement,
     * applying the three-phase spending model rather than a flat inflation rate.
     *
     * @param retirementAge       age at start of retirement
     * @param inflationRate       annual inflation (e.g. 0.06 for 6 %)
     * @param yearsIntoRetirement 0 = first year of retirement
     */
    private double colGrowthMultiplier(int retirementAge, double inflationRate, int yearsIntoRetirement) {
        return colGrowthMultiplier(null, retirementAge, inflationRate, yearsIntoRetirement);
    }

    private double colGrowthMultiplier(PlanParameters params,
                                       int retirementAge,
                                       double inflationRate,
                                       int yearsIntoRetirement) {
        double multiplier = 1.0;
        int slowGoAge = getSpendingSlowGoAge(params);
        int noGoAge = getSpendingNoGoAge(params);
        double slowGoAdjustment = getSpendingSlowGoInflationAdjustmentRate(params);
        double healthcareAdjustment = getSpendingHealthcareInflationAdjustmentRate(params);
        for (int y = 0; y < yearsIntoRetirement; y++) {
            int age = retirementAge + y;
            double rate;
            if (age < slowGoAge) {
                rate = inflationRate;                                    // Phase 1: active
            } else if (age < noGoAge) {
                rate = inflationRate + slowGoAdjustment;                  // Phase 2: slow-go
            } else {
                rate = inflationRate + healthcareAdjustment;              // Phase 3: healthcare
            }
            multiplier *= (1.0 + rate);
        }
        return multiplier;
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

    private Set<String> collectRedeemedFdRefs() {
        return ds.getTransactions().stream()
                .filter(tx -> tx.getRedeemDetails() != null
                        && tx.getRedeemDetails().getOrgnlFDRef() != null)
                .map(tx -> tx.getRedeemDetails().getOrgnlFDRef())
                .collect(Collectors.toSet());
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
