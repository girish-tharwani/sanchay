package com.sanchay.model;

/** Bucket-model investment account. */
public class InvestmentAccount extends Account {

    public enum InvestmentType {
        MUTUAL_FUNDS, EQUITY, DEBT_BONDS, FIXED_DEPOSIT, RECURRING_DEPOSIT, PROVIDENT_FUND
    }

    public enum InvestmentStatus { ACTIVE, REDEEMED }

    private InvestmentType investmentType;
    private String folioAccountNumber;
    private String institutionName;
    private long investedAmountPaise;
    private InvestmentStatus investmentStatus;

    public InvestmentAccount(String name, InvestmentType investmentType) {
        super(name);
        this.investmentType = investmentType;
        this.investmentStatus = InvestmentStatus.ACTIVE;
    }

    @Override public AccountCategory getAccountCategory() { return AccountCategory.INVESTMENT; }

    @Override public String getAccountType() {
        return switch (investmentType) {
            case MUTUAL_FUNDS      -> "Mutual Funds";
            case EQUITY            -> "Equity";
            case DEBT_BONDS        -> "Debt Bonds";
            case FIXED_DEPOSIT     -> "Fixed Deposit (FD)";
            case RECURRING_DEPOSIT -> "Recurring Deposit (RD)";
            case PROVIDENT_FUND    -> "Provident Fund";
        };
    }

    public InvestmentType getInvestmentType()           { return investmentType; }
    public String getFolioAccountNumber()               { return folioAccountNumber; }
    public String getInstitutionName()                  { return institutionName; }
    public long getInvestedAmountPaise()                { return investedAmountPaise; }
    public InvestmentStatus getInvestmentStatus()       { return investmentStatus; }

    public void setFolioAccountNumber(String n)         { this.folioAccountNumber = n; }
    public void setInstitutionName(String n)            { this.institutionName = n; }
    public void setInvestedAmountPaise(long p)          { this.investedAmountPaise = p; }
    public void setInvestmentStatus(InvestmentStatus s) { this.investmentStatus = s; }
}