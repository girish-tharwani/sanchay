package com.sanchay.ui.profile;

import com.sanchay.model.*;
import com.sanchay.service.DataStore;
import java.time.LocalDate;

class EarningScheduleBuilder {

    static void build(EarningSource src, DataStore ds, String memberName,
                      InvestmentAccount pfAcct, InvestmentAccount esppAcct) {
        long amount = src.computeScheduleAmountPaise();
        RecurringTransaction.Frequency freq = src.getType() == FamilyMember.EarningType.SALARY
                ? RecurringTransaction.Frequency.MONTHLY
                : RecurringTransaction.Frequency.valueOf(src.getSimpleFrequency());

        RecurringTransaction existing = ds.findRecurringById(src.getRecurringScheduleId());
        if (existing != null) {
            existing.setAmountPaise(amount);
            existing.setFrequency(freq);
            existing.setToAccountId(src.getDepositAccountId());
            existing.setDueDayOfMonth(src.getDepositDay());
            existing.setDescription(src.getScheduleDescription());
            existing.setCategoryId(src.getCategoryId());
            existing.setStatus(RecurringTransaction.Status.ACTIVE);
            ds.saveRecurringNow();
        } else {
            RecurringTransaction rt = new RecurringTransaction(
                    src.getScheduleDescription(), Transaction.Type.INCOME,
                    freq, src.getDepositDay(), LocalDate.now(), amount);
            rt.setToAccountId(src.getDepositAccountId());
            rt.setCategoryId(src.getCategoryId());
            rt.setAutoCreated(true);
            ds.addRecurring(rt);
            src.setRecurringScheduleId(rt.getId());
        }

        if (src.getType() == FamilyMember.EarningType.SALARY) {
            if (pfAcct != null) {
                long basicMo = src.getBasicDaPaise() / 12;
                long empPf   = Math.round(basicMo * (0.12 + src.getVpfPct() / 100.0));
                long empEpf  = Math.max(0L, Math.round(basicMo * 0.12) - 125_000L);
                updatePfSchedule(src, empPf + empEpf, pfAcct.getId(), ds, memberName);
            }

            if (src.isEsppEnabled() && esppAcct != null) {
                updateEsppSchedule(src, src.getEsppAmountPaise(), esppAcct.getId(), ds, memberName);
            } else if (!src.isEsppEnabled() && src.getEsppScheduleId() != null) {
                ds.deleteRecurring(src.getEsppScheduleId());
                src.setEsppScheduleId(null);
            }
        }
    }

    private static void updatePfSchedule(EarningSource src, long amountPaise, String pfAccountId,
                                         DataStore ds, String memberName) {
        String desc = memberName + " — PF Deposit (" + src.getSourceName() + ")";
        RecurringTransaction existing = ds.findRecurringById(src.getPfScheduleId());
        if (existing != null) {
            existing.setAmountPaise(amountPaise);
            existing.setToAccountId(pfAccountId);
            existing.setDueDayOfMonth(src.getDepositDay());
            existing.setDescription(desc);
            existing.setStatus(RecurringTransaction.Status.ACTIVE);
            ds.saveRecurringNow();
        } else {
            RecurringTransaction rt = new RecurringTransaction(
                    desc, Transaction.Type.INVESTMENT,
                    RecurringTransaction.Frequency.MONTHLY, src.getDepositDay(),
                    LocalDate.now(), amountPaise);
            rt.setToAccountId(pfAccountId);
            rt.setAutoRecordAfterDays(1);
            rt.setAutoCreated(true);
            ds.addRecurring(rt);
            src.setPfScheduleId(rt.getId());
        }
    }

    private static void updateEsppSchedule(EarningSource src, long amountPaise, String esppAccountId,
                                           DataStore ds, String memberName) {
        String desc = memberName + " — Share Purchase Plan (" + src.getSourceName() + ")";
        RecurringTransaction existing = ds.findRecurringById(src.getEsppScheduleId());
        if (existing != null) {
            existing.setAmountPaise(amountPaise);
            existing.setToAccountId(esppAccountId);
            existing.setDueDayOfMonth(src.getDepositDay());
            existing.setDescription(desc);
            existing.setStatus(RecurringTransaction.Status.ACTIVE);
            ds.saveRecurringNow();
        } else {
            RecurringTransaction rt = new RecurringTransaction(
                    desc, Transaction.Type.INVESTMENT,
                    RecurringTransaction.Frequency.MONTHLY, src.getDepositDay(),
                    LocalDate.now(), amountPaise);
            rt.setToAccountId(esppAccountId);
            rt.setAutoRecordAfterDays(1);
            rt.setAutoCreated(true);
            ds.addRecurring(rt);
            src.setEsppScheduleId(rt.getId());
        }
    }
}
