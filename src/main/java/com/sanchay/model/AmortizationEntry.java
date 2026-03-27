package com.sanchay.model;

import java.time.LocalDate;
import java.util.UUID;

/** One row in a loan amortization schedule. Amounts stored in paise. */
public class AmortizationEntry {

    private String    id;
    private String    loanAccountId;
    private int       sequenceNo;              // 1-based month index
    private LocalDate paymentDate;
    private long      openingBalancePaise;
    private long      emiAmountPaise;
    private long      principalRepaymentPaise;
    private long      interestPaymentPaise;
    private boolean   manualOverride;          // true = user corrected this row

    public AmortizationEntry() {
        this.id = UUID.randomUUID().toString();
    }

    public AmortizationEntry(String loanAccountId, int sequenceNo, LocalDate paymentDate,
                             long openingBalancePaise, long emiAmountPaise,
                             long principalRepaymentPaise, long interestPaymentPaise) {
        this.id                      = UUID.randomUUID().toString();
        this.loanAccountId           = loanAccountId;
        this.sequenceNo              = sequenceNo;
        this.paymentDate             = paymentDate;
        this.openingBalancePaise     = openingBalancePaise;
        this.emiAmountPaise          = emiAmountPaise;
        this.principalRepaymentPaise = principalRepaymentPaise;
        this.interestPaymentPaise    = interestPaymentPaise;
    }

    public String    getId()                        { return id; }
    public String    getLoanAccountId()             { return loanAccountId; }
    public int       getSequenceNo()                { return sequenceNo; }
    public LocalDate getPaymentDate()               { return paymentDate; }
    public long      getOpeningBalancePaise()       { return openingBalancePaise; }
    public long      getEmiAmountPaise()            { return emiAmountPaise; }
    public long      getPrincipalRepaymentPaise()   { return principalRepaymentPaise; }
    public long      getInterestPaymentPaise()      { return interestPaymentPaise; }
    public boolean   isManualOverride()             { return manualOverride; }

    public void setLoanAccountId(String id)              { this.loanAccountId           = id; }
    public void setSequenceNo(int n)                     { this.sequenceNo              = n; }
    public void setPaymentDate(LocalDate d)              { this.paymentDate             = d; }
    public void setOpeningBalancePaise(long p)           { this.openingBalancePaise     = p; }
    public void setEmiAmountPaise(long p)                { this.emiAmountPaise          = p; }
    public void setPrincipalRepaymentPaise(long p)       { this.principalRepaymentPaise = p; }
    public void setInterestPaymentPaise(long p)          { this.interestPaymentPaise    = p; }
    public void setManualOverride(boolean b)             { this.manualOverride          = b; }
}
