package com.sanchay.ui.profile;

import com.sanchay.model.EarningSource;
import com.sanchay.model.FamilyMember;
import com.sanchay.model.InvestmentAccount;
import com.sanchay.model.RecurringTransaction;
import com.sanchay.model.Transaction;
import com.sanchay.service.DataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class EarningScheduleBuilderTest {

    private DataStore ds;
    private InvestmentAccount equityAccount;

    @BeforeEach
    void setUp() {
        ds = DataStore.getInstance();
        ds.reset();
        equityAccount = new InvestmentAccount("ESPP", InvestmentAccount.InvestmentType.EQUITY);
        ds.addAccountInternal(equityAccount);
    }

    @Test
    void uncheckedEsppPausesExistingScheduleInsteadOfDeletingIt() {
        EarningSource source = salarySource();
        RecurringTransaction espp = esppSchedule(50_000);
        source.setEsppScheduleId(espp.getId());
        source.setEsppEnabled(false);
        source.setEsppAmountPaise(50_000);

        EarningScheduleBuilder.build(source, ds, "Mira", null, null);

        RecurringTransaction saved = ds.findRecurringById(espp.getId());
        assertNotNull(saved);
        assertEquals(espp.getId(), source.getEsppScheduleId());
        assertSame(RecurringTransaction.Status.PAUSED, saved.getStatus());
    }

    @Test
    void checkedEsppResumesExistingPausedSchedule() {
        EarningSource source = salarySource();
        RecurringTransaction espp = esppSchedule(50_000);
        espp.setStatus(RecurringTransaction.Status.PAUSED);
        source.setEsppScheduleId(espp.getId());
        source.setEsppEnabled(true);
        source.setEsppAmountPaise(75_000);

        EarningScheduleBuilder.build(source, ds, "Mira", null, equityAccount);

        RecurringTransaction saved = ds.findRecurringById(espp.getId());
        assertEquals(75_000, saved.getAmountPaise());
        assertEquals(equityAccount.getId(), saved.getToAccountId());
        assertSame(RecurringTransaction.Status.ACTIVE, saved.getStatus());
    }

    private EarningSource salarySource() {
        EarningSource source = new EarningSource();
        source.setSourceName("Salary");
        source.setType(FamilyMember.EarningType.SALARY);
        source.setScheduleDescription("Salary");
        source.setDepositAccountId("bank-account");
        source.setDepositDay(1);
        source.setBasicDaPaise(1_200_000);
        return source;
    }

    private RecurringTransaction esppSchedule(long amountPaise) {
        RecurringTransaction espp = new RecurringTransaction(
                "ESPP",
                Transaction.Type.INVESTMENT,
                RecurringTransaction.Frequency.MONTHLY,
                1,
                LocalDate.now(),
                amountPaise);
        espp.setToAccountId(equityAccount.getId());
        ds.addRecurringInternal(espp);
        return espp;
    }
}
