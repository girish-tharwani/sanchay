package com.sanchay.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Home, Vehicle, or Personal loan account. */
public class LoanAccount extends Account {

    public enum LoanType   { HOME, VEHICLE, PERSONAL }
    public enum LoanStatus { ACTIVE, CLOSED, SETTLED }

    private LoanType loanType;
    private String loanAccountNumber;
    private String lenderName;
    private LocalDate disbursementDate;
    private long loanAmountPaise;
    private double interestRate;
    private int tenureMonths;
    private long emiAmountPaise;
    private int emiDueDay;
    private long outstandingPrincipalPaise;
    private LoanStatus loanStatus;
    private String coApplicantName;
    private String vehicleRegistrationNo;
    private String vehicleDescription;
    private List<LoanRateChange> rateHistory = new ArrayList<>();

    public LoanAccount(String name, LoanType loanType) {
        super(name);
        this.loanType = loanType;
        this.loanStatus = LoanStatus.ACTIVE;
    }

    @Override public AccountCategory getAccountCategory() { return AccountCategory.LOAN; }

    @Override public String getAccountType() {
        return switch (loanType) {
            case HOME     -> "Home Loan";
            case VEHICLE  -> "Vehicle Loan";
            case PERSONAL -> "Personal Loan";
        };
    }

    public LoanType getLoanType()                                { return loanType; }
    public String getLoanAccountNumber()                         { return loanAccountNumber; }
    public String getLenderName()                                { return lenderName; }
    public LocalDate getDisbursementDate()                       { return disbursementDate; }
    public long getLoanAmountPaise()                             { return loanAmountPaise; }
    public double getInterestRate()                              { return interestRate; }
    public int getTenureMonths()                                 { return tenureMonths; }
    public long getEmiAmountPaise()                              { return emiAmountPaise; }
    public int getEmiDueDay()                                    { return emiDueDay; }
    public long getOutstandingPrincipalPaise()                   { return outstandingPrincipalPaise; }
    public LoanStatus getLoanStatus()                            { return loanStatus; }
    public String getCoApplicantName()                           { return coApplicantName; }
    public String getVehicleRegistrationNo()                     { return vehicleRegistrationNo; }
    public String getVehicleDescription()                        { return vehicleDescription; }

    public void setLoanAccountNumber(String n)                   { this.loanAccountNumber = n; }
    public void setLenderName(String n)                          { this.lenderName = n; }
    public void setDisbursementDate(LocalDate d)                 { this.disbursementDate = d; }
    public void setLoanAmountPaise(long p)                       { this.loanAmountPaise = p; }
    public void setInterestRate(double r)                        { this.interestRate = r; }
    public void setTenureMonths(int m)                           { this.tenureMonths = m; }
    public void setEmiAmountPaise(long p)                        { this.emiAmountPaise = p; }
    public void setEmiDueDay(int d)                              { this.emiDueDay = d; }
    public void setOutstandingPrincipalPaise(long p)             { this.outstandingPrincipalPaise = p; }
    public void setLoanStatus(LoanStatus s)                      { this.loanStatus = s; }
    public void setCoApplicantName(String n)                     { this.coApplicantName = n; }
    public void setVehicleRegistrationNo(String n)               { this.vehicleRegistrationNo = n; }
    public void setVehicleDescription(String d)                  { this.vehicleDescription = d; }

    public List<LoanRateChange> getRateHistory() {
        if (rateHistory == null) rateHistory = new ArrayList<>();
        return rateHistory;
    }
    public void setRateHistory(List<LoanRateChange> h)           { this.rateHistory = h; }
}
