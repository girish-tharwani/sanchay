package com.financeapp.model;

/** Savings or Current bank account. */
public class BankAccount extends Account {

    public enum SubType { SAVINGS, CURRENT }

    private SubType subType;
    private String accountNumber;
    private String bankName;
    private String branchName;
    private String accountHolder;
    private String ifscCode;
    private long openingBalancePaise;

    public BankAccount(String name, SubType subType) {
        super(name);
        this.subType = subType;
    }

    @Override public AccountCategory getAccountCategory() { return AccountCategory.BANK; }

    @Override public String getAccountType() {
        return subType == SubType.SAVINGS ? "Savings Account" : "Current Account";
    }

    public String getMaskedAccountNumber() {
        if (accountNumber == null || accountNumber.length() <= 4) return accountNumber;
        return "xxxx-xxxx-" + accountNumber.substring(accountNumber.length() - 4);
    }

    public SubType getSubType()                              { return subType; }
    public String getAccountNumber()                         { return accountNumber; }
    public String getBankName()                              { return bankName; }
    public String getBranchName()                            { return branchName; }
    public String getAccountHolder()                         { return accountHolder; }
    public String getIfscCode()                              { return ifscCode; }
    public long getOpeningBalancePaise()                     { return openingBalancePaise; }

    public void setAccountNumber(String n)                   { this.accountNumber = n; }
    public void setBankName(String b)                        { this.bankName = b; }
    public void setBranchName(String b)                      { this.branchName = b; }
    public void setAccountHolder(String h)                   { this.accountHolder = h; }
    public void setIfscCode(String i)                        { this.ifscCode = i; }
    public void setOpeningBalancePaise(long paise)           { this.openingBalancePaise = paise; }
}
