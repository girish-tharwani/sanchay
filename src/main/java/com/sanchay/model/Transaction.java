package com.sanchay.model;

import java.time.LocalDate;
import java.util.UUID;

/** Represents a single financial transaction of any type. Amount stored in paise. */
public class Transaction {

    public enum Type {
        EXPENSE, INCOME, TRANSFER, INVESTMENT,
        CC_PAYMENT,   // Credit card bill payment from bank to credit card
        REFUND,       // Money returned to account (offsets an expense category)
        REDEEM,       // Investment redemption: principal returned + gain/loss recorded
        LOAN_PAYMENT, // Repayment from a bank account to a loan account
        GAIN,         // Investment gain posted to bank — created by code, not user-selectable
        LOSE          // Investment loss debited from bank — created by code, not user-selectable
    }

    public enum PaymentMode {
        UPI, NET_BANKING, DEBIT_CARD, CREDIT_CARD, CASH, CHEQUE, AUTO_DEBIT,
        NEFT, IMPS, INTERNAL_TRANSFER
    }

    public enum IncomeType {
        SALARY, INTEREST, DIVIDEND, RENTAL, FREELANCE, GIFT, OTHER
    }

    private String id;
    private Type type;
    private LocalDate date;
    private String description;
    private long amountPaise;
    private String categoryId;
    private String subCategoryId;
    private String familyMember;
    private String referenceNumber;
    private String notes;
    private String fromAccountId;
    private String toAccountId;
    private PaymentMode paymentMode;
    private IncomeType incomeType;
    private String source;
    private String schemeScriptName;
    private Double unitsNav;
    private boolean fromRecurring;
    private String recurringId;
    private long principalPaise;      // REDEEM only: original invested amount being returned
    private String groupTransactionId; // links the 3 transactions created by a REDEEM operation

    public enum SourceIndicator {
        MANUAL,           // User entered manually
        IMPORTED,         // Imported from CSV; category not yet confirmed
        AUTO_CATEGORIZED, // Imported and auto-categorized by rules; awaiting user acceptance
        RECONCILED        // Import row matched and merged with an existing manual transaction
    }
    private SourceIndicator sourceIndicator;
    private String importHash;

    public Transaction(Type type, LocalDate date, String description, long amountPaise) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.date = date;
        this.description = description;
        this.amountPaise = amountPaise;
    }

    public String getAmountInr() {
        return String.format("₹%,.2f", amountPaise / 100.0);
    }

    public String getSignedAmountInr(String viewingAccountId) {
        boolean isDebit;
        if (type == Type.TRANSFER || type == Type.CC_PAYMENT || type == Type.INVESTMENT
                || type == Type.REDEEM || type == Type.LOAN_PAYMENT || type == Type.LOSE) {
            isDebit = viewingAccountId != null && viewingAccountId.equals(fromAccountId);
        } else {
            isDebit = (type == Type.EXPENSE); // INCOME, REFUND, GAIN → always credit
        }
        return isDebit ? "(" + getAmountInr() + ")" : getAmountInr();
    }

    public String getTypedSignedAmountInr() {
        return switch (type) {
            case INCOME, TRANSFER, REFUND, REDEEM, GAIN -> getAmountInr();
            case EXPENSE, INVESTMENT, CC_PAYMENT, LOAN_PAYMENT, LOSE -> "(" + getAmountInr() + ")";
        };
    }

    public String getId()                   { return id; }
    public Type getType()                   { return type; }
    public LocalDate getDate()              { return date; }
    public String getDescription()          { return description; }
    public long getAmountPaise()            { return amountPaise; }
    public String getCategoryId()           { return categoryId; }
    public String getSubCategoryId()        { return subCategoryId; }
    public String getFamilyMember()         { return familyMember; }
    public String getReferenceNumber()      { return referenceNumber; }
    public String getNotes()                { return notes; }
    public String getFromAccountId()        { return fromAccountId; }
    public String getToAccountId()          { return toAccountId; }
    public PaymentMode getPaymentMode()     { return paymentMode; }
    public IncomeType getIncomeType()       { return incomeType; }
    public String getSource()               { return source; }
    public String getSchemeScriptName()     { return schemeScriptName; }
    public Double getUnitsNav()             { return unitsNav; }
    public boolean isFromRecurring()        { return fromRecurring; }
    public String getRecurringId()          { return recurringId; }
    /** Returns MANUAL when sourceIndicator is null (data migrated from before this field existed). */
    public SourceIndicator getSourceIndicator() {
        return sourceIndicator == null ? SourceIndicator.MANUAL : sourceIndicator;
    }
    public String getImportHash()           { return importHash; }

    public void setDate(LocalDate d)                { this.date = d; }
    public void setDescription(String d)            { this.description = d; }
    public void setAmountPaise(long p)              { this.amountPaise = p; }
    public void setCategoryId(String c)             { this.categoryId = c; }
    public void setSubCategoryId(String s)          { this.subCategoryId = s; }
    public void setFamilyMember(String f)           { this.familyMember = f; }
    public void setReferenceNumber(String r)        { this.referenceNumber = r; }
    public void setNotes(String n)                  { this.notes = n; }
    public void setFromAccountId(String id)         { this.fromAccountId = id; }
    public void setToAccountId(String id)           { this.toAccountId = id; }
    public void setPaymentMode(PaymentMode m)       { this.paymentMode = m; }
    public void setIncomeType(IncomeType t)         { this.incomeType = t; }
    public void setSource(String s)                 { this.source = s; }
    public void setSchemeScriptName(String s)       { this.schemeScriptName = s; }
    public void setUnitsNav(Double u)               { this.unitsNav = u; }
    public void setFromRecurring(boolean b)           { this.fromRecurring = b; }
    public void setRecurringId(String id)             { this.recurringId = id; }
    public void setSourceIndicator(SourceIndicator s) { this.sourceIndicator = s; }
    public void setImportHash(String h)               { this.importHash = h; }
    public long getPrincipalPaise()                   { return principalPaise; }
    public void setPrincipalPaise(long p)             { this.principalPaise = p; }
    public String getGroupTransactionId()             { return groupTransactionId; }
    public void setGroupTransactionId(String g)       { this.groupTransactionId = g; }
}
