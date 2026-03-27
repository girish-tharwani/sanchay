package com.sanchay.model;

import java.time.LocalDate;
import java.util.UUID;

/** Records a change in interest rate or EMI amount for a loan, effective from a given date. */
public class LoanRateChange {

    private String    id;
    private LocalDate effectiveFrom;
    private double    interestRate;   // annual percentage
    private long      emiAmountPaise;

    public LoanRateChange() {
        this.id = UUID.randomUUID().toString();
    }

    public LoanRateChange(LocalDate effectiveFrom, double interestRate, long emiAmountPaise) {
        this.id             = UUID.randomUUID().toString();
        this.effectiveFrom  = effectiveFrom;
        this.interestRate   = interestRate;
        this.emiAmountPaise = emiAmountPaise;
    }

    public String    getId()             { return id; }
    public LocalDate getEffectiveFrom()  { return effectiveFrom; }
    public double    getInterestRate()   { return interestRate; }
    public long      getEmiAmountPaise() { return emiAmountPaise; }

    public void setEffectiveFrom(LocalDate d)   { this.effectiveFrom  = d; }
    public void setInterestRate(double r)        { this.interestRate   = r; }
    public void setEmiAmountPaise(long p)        { this.emiAmountPaise = p; }
}
