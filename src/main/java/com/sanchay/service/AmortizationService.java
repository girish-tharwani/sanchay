package com.sanchay.service;

import com.sanchay.model.AmortizationEntry;
import com.sanchay.model.LoanAccount;
import com.sanchay.model.LoanRateChange;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure calculation logic for loan amortization schedules.
 * No DataStore dependency — all inputs passed as parameters.
 */
public class AmortizationService {

    /**
     * Generates a full reducing-balance amortization schedule for the given loan.
     * Uses rateHistory to apply the correct rate/EMI for each month.
     */
    public static List<AmortizationEntry> generateSchedule(LoanAccount loan) {
        List<LoanRateChange> history = new ArrayList<>(loan.getRateHistory());
        history.sort(Comparator.comparing(LoanRateChange::getEffectiveFrom));

        long      balance     = loan.getOutstandingPrincipalPaise();
        LocalDate openingDate = loan.getOpeningDate();
        int       tenure      = loan.getTenureMonths();
        int       dueDay      = Math.min(loan.getEmiDueDay(), 28);

        List<AmortizationEntry> entries = new ArrayList<>();

        for (int seq = 1; seq <= tenure && balance > 0; seq++) {
            LocalDate paymentDate = openingDate
                    .plusMonths(seq)
                    .withDayOfMonth(Math.min(dueDay,
                            openingDate.plusMonths(seq).lengthOfMonth()));

            LoanRateChange applicable = getApplicableRate(history, paymentDate, loan);
            double annualRate   = applicable.getInterestRate();
            long   emiPaise     = applicable.getEmiAmountPaise();

            long interest  = Math.round(balance * annualRate / 12.0 / 100.0);
            long principal = emiPaise - interest;

            if (principal <= 0) {
                // EMI too small to cover interest — record an interest-only row
                principal = 0;
                emiPaise  = interest;
            }

            // Final payment: clamp principal to remaining balance
            if (principal >= balance) {
                principal = balance;
                emiPaise  = principal + interest;
            }

            entries.add(new AmortizationEntry(
                    loan.getId(), seq, paymentDate,
                    balance, emiPaise, principal, interest));
            balance -= principal;
        }

        return entries;
    }

    /**
     * Recalculates entries from {@code fromIndex} (0-based) onward, using the
     * closing balance of the preceding entry as the new opening balance.
     * Entries before {@code fromIndex} are preserved unchanged.
     * Rate changes are looked up from the loan's rate history.
     */
    public static List<AmortizationEntry> recalculateFrom(
            List<AmortizationEntry> entries, int fromIndex, LoanAccount loan) {

        if (entries.isEmpty() || fromIndex >= entries.size()) return entries;

        List<LoanRateChange> history = new ArrayList<>(loan.getRateHistory());
        history.sort(Comparator.comparing(LoanRateChange::getEffectiveFrom));

        // Opening balance for the recalculation = closing balance of the entry just before
        long balance = fromIndex == 0
                ? loan.getOutstandingPrincipalPaise()
                : entries.get(fromIndex - 1).getOpeningBalancePaise()
                        - entries.get(fromIndex - 1).getPrincipalRepaymentPaise();

        List<AmortizationEntry> result = new ArrayList<>(entries.subList(0, fromIndex));

        for (int i = fromIndex; i < entries.size() && balance > 0; i++) {
            AmortizationEntry existing = entries.get(i);
            LocalDate paymentDate = existing.getPaymentDate();

            LoanRateChange applicable = getApplicableRate(history, paymentDate, loan);
            double annualRate = applicable.getInterestRate();
            long   emiPaise   = applicable.getEmiAmountPaise();

            long interest  = Math.round(balance * annualRate / 12.0 / 100.0);
            long principal = emiPaise - interest;

            if (principal <= 0) { principal = 0; emiPaise = interest; }

            if (principal >= balance) {
                principal = balance;
                emiPaise  = principal + interest;
            }

            AmortizationEntry updated = new AmortizationEntry(
                    loan.getId(), existing.getSequenceNo(), paymentDate,
                    balance, emiPaise, principal, interest);
            // Preserve manual override only on the exact fromIndex row
            if (i == fromIndex && existing.isManualOverride()) {
                updated.setManualOverride(true);
            }
            result.add(updated);
            balance -= principal;
        }

        return result;
    }

    /**
     * Returns the scheduled principal for the given payment date by finding the
     * schedule entry whose payment month matches the date's year-month.
     * Returns 0 if no matching entry is found.
     */
    public static long getScheduledPrincipalForDate(List<AmortizationEntry> schedule, LocalDate date) {
        if (schedule == null || date == null) return 0;
        return schedule.stream()
                .filter(e -> e.getPaymentDate().getYear()  == date.getYear()
                          && e.getPaymentDate().getMonth() == date.getMonth())
                .mapToLong(AmortizationEntry::getPrincipalRepaymentPaise)
                .findFirst()
                .orElse(0L);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static LoanRateChange getApplicableRate(
            List<LoanRateChange> sortedHistory, LocalDate paymentDate, LoanAccount loan) {

        LoanRateChange applicable = null;
        for (LoanRateChange rc : sortedHistory) {
            if (!rc.getEffectiveFrom().isAfter(paymentDate)) {
                applicable = rc;
            }
        }
        // Fall back to account-level fields if history is absent
        if (applicable == null) {
            applicable = new LoanRateChange(
                    loan.getOpeningDate(), loan.getInterestRate(), loan.getEmiAmountPaise());
        }
        return applicable;
    }
}
