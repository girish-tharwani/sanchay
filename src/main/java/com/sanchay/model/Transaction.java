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

    public enum SourceIndicator {
        MANUAL,           // User entered manually
        IMPORTED,         // Imported from CSV; category not yet confirmed
        AUTO_CATEGORIZED, // Imported and auto-categorized by rules; awaiting user acceptance
        RECONCILED        // Import row matched and merged with an existing manual transaction
    }

    public enum InterestPayable {
        AT_MATURITY, YEARLY, QUARTERLY, MONTHLY
    }

    /**
     * Controls which date anchors the interest payout schedule.
     * ANNIVERSARY: payouts fall on the anniversary of the investment date (FD default).
     * FIXED_DATE:  payouts fall on a fixed calendar date each period (common for bonds).
     *              When FIXED_DATE, payoutMonth and payoutDay on FdDetails specify the anchor.
     */
    public enum PayoutAnchor {
        ANNIVERSARY, FIXED_DATE
    }

    // ── Sub-object classes ────────────────────────────────────────────────────

    public static class Classification {
        private String categoryId;
        private String subCategoryId;
        private String familyMember;

        public String getCategoryId()              { return categoryId; }
        public void   setCategoryId(String c)      { this.categoryId = c; }
        public String getSubCategoryId()           { return subCategoryId; }
        public void   setSubCategoryId(String s)   { this.subCategoryId = s; }
        public String getFamilyMember()            { return familyMember; }
        public void   setFamilyMember(String f)    { this.familyMember = f; }
    }

    public static class Payment {
        private PaymentMode mode;
        private String      referenceNumber;

        public PaymentMode getMode()                    { return mode; }
        public void        setMode(PaymentMode m)       { this.mode = m; }
        public String      getReferenceNumber()         { return referenceNumber; }
        public void        setReferenceNumber(String r) { this.referenceNumber = r; }
    }

    public static class Recurring {
        private String recurringId;

        public Recurring() {}
        public Recurring(String recurringId) { this.recurringId = recurringId; }

        public String getRecurringId()          { return recurringId; }
        public void   setRecurringId(String r)  { this.recurringId = r; }
    }

    public static class FdDetails {
        private String           ref;
        private Double           interestRate;
        private LocalDate        maturityDate;
        private Long             maturityAmountPaise;
        private InterestPayable  interestPayable;
        // Payout anchor — null / absent means ANNIVERSARY (backward-compatible default)
        private PayoutAnchor     payoutAnchor;
        // Only relevant when payoutAnchor == FIXED_DATE
        private Integer          payoutMonth; // 1–12
        private Integer          payoutDay;   // 1–28

        public String          getRef()                           { return ref; }
        public void            setRef(String r)                   { this.ref = r; }
        public Double          getInterestRate()                  { return interestRate; }
        public void            setInterestRate(Double r)          { this.interestRate = r; }
        public LocalDate       getMaturityDate()                  { return maturityDate; }
        public void            setMaturityDate(LocalDate d)       { this.maturityDate = d; }
        public Long            getMaturityAmountPaise()           { return maturityAmountPaise; }
        public void            setMaturityAmountPaise(Long p)     { this.maturityAmountPaise = p; }
        public InterestPayable getInterestPayable()               { return interestPayable; }
        public void            setInterestPayable(InterestPayable i) { this.interestPayable = i; }
        /** Returns ANNIVERSARY when absent (data predating this field). */
        public PayoutAnchor    getPayoutAnchor()                  { return payoutAnchor == null ? PayoutAnchor.ANNIVERSARY : payoutAnchor; }
        public void            setPayoutAnchor(PayoutAnchor a)    { this.payoutAnchor = a; }
        public Integer         getPayoutMonth()                   { return payoutMonth; }
        public void            setPayoutMonth(Integer m)          { this.payoutMonth = m; }
        public Integer         getPayoutDay()                     { return payoutDay; }
        public void            setPayoutDay(Integer d)            { this.payoutDay = d; }
    }

    public static class InvestmentDetails {
        private String    schemeScriptName;
        private Double    unitsNav;
        private FdDetails fd;

        public String    getSchemeScriptName()          { return schemeScriptName; }
        public void      setSchemeScriptName(String s)  { this.schemeScriptName = s; }
        public Double    getUnitsNav()                  { return unitsNav; }
        public void      setUnitsNav(Double u)          { this.unitsNav = u; }
        public FdDetails getFd()                        { return fd; }
        public void      setFd(FdDetails f)             { this.fd = f; }
    }

    public static class RedeemDetails {
        private long principalPaise;

        public long getPrincipalPaise()         { return principalPaise; }
        public void setPrincipalPaise(long p)   { this.principalPaise = p; }
    }

    // ── Root fields ───────────────────────────────────────────────────────────

    private String         id;
    private Type           type;
    private LocalDate      date;
    private String         description;
    private long           amountPaise;
    private String         fromAccountId;
    private String         toAccountId;
    private String         notes;
    private SourceIndicator sourceIndicator;
    private String         importHash;
    private String         groupTransactionId;

    // ── Sub-object fields ─────────────────────────────────────────────────────

    private Classification   classification;
    private Payment          payment;
    private Recurring        recurring;
    private InvestmentDetails investmentDetails;
    private RedeemDetails    redeemDetails;

    // ── Constructor ───────────────────────────────────────────────────────────

    public Transaction(Type type, LocalDate date, String description, long amountPaise) {
        this.id          = UUID.randomUUID().toString();
        this.type        = type;
        this.date        = date;
        this.description = description;
        this.amountPaise = amountPaise;
    }

    // ── Business methods ──────────────────────────────────────────────────────

    public String getAmountInr() {
        return String.format("₹%,.2f", amountPaise / 100.0);
    }

    private boolean isDebitFor(String viewingAccountId) {
        if (type == Type.TRANSFER || type == Type.CC_PAYMENT || type == Type.INVESTMENT
                || type == Type.REDEEM || type == Type.LOAN_PAYMENT || type == Type.LOSE) {
            return viewingAccountId != null && viewingAccountId.equals(fromAccountId);
        }
        return type == Type.EXPENSE; // INCOME, REFUND, GAIN → always credit
    }

    public String getSignedAmountInr(String viewingAccountId) {
        return isDebitFor(viewingAccountId) ? "(" + getAmountInr() + ")" : getAmountInr();
    }

    /** Signed amount in paise: negative for debits, positive for credits. */
    public long getSignedAmountPaise(String viewingAccountId) {
        return isDebitFor(viewingAccountId) ? -amountPaise : amountPaise;
    }

    public String getTypedSignedAmountInr() {
        return switch (type) {
            case INCOME, TRANSFER, REFUND, REDEEM, GAIN -> getAmountInr();
            case EXPENSE, INVESTMENT, CC_PAYMENT, LOAN_PAYMENT, LOSE -> "(" + getAmountInr() + ")";
        };
    }

    // ── Root getters / setters ────────────────────────────────────────────────

    public String        getId()                         { return id; }
    public Type          getType()                       { return type; }
    public void          setType(Type t)                 { this.type = t; }
    public LocalDate     getDate()                       { return date; }
    public void          setDate(LocalDate d)            { this.date = d; }
    public String        getDescription()                { return description; }
    public void          setDescription(String d)        { this.description = d; }
    public long          getAmountPaise()                { return amountPaise; }
    public void          setAmountPaise(long p)          { this.amountPaise = p; }
    public String        getFromAccountId()              { return fromAccountId; }
    public void          setFromAccountId(String id)     { this.fromAccountId = id; }
    public String        getToAccountId()                { return toAccountId; }
    public void          setToAccountId(String id)       { this.toAccountId = id; }
    public String        getNotes()                      { return notes; }
    public void          setNotes(String n)              { this.notes = n; }
    public String        getImportHash()                 { return importHash; }
    public void          setImportHash(String h)         { this.importHash = h; }
    public String        getGroupTransactionId()         { return groupTransactionId; }
    public void          setGroupTransactionId(String g) { this.groupTransactionId = g; }

    /** Returns MANUAL when sourceIndicator is null (data migrated from before this field existed). */
    public SourceIndicator getSourceIndicator() {
        return sourceIndicator == null ? SourceIndicator.MANUAL : sourceIndicator;
    }
    public void setSourceIndicator(SourceIndicator s) { this.sourceIndicator = s; }

    // ── Sub-object getters / setters ──────────────────────────────────────────

    public Classification    getClassification()                    { return classification; }
    public void              setClassification(Classification c)    { this.classification = c; }
    public Payment           getPayment()                           { return payment; }
    public void              setPayment(Payment p)                  { this.payment = p; }
    public Recurring         getRecurring()                         { return recurring; }
    public void              setRecurring(Recurring r)              { this.recurring = r; }
    public InvestmentDetails getInvestmentDetails()                 { return investmentDetails; }
    public void              setInvestmentDetails(InvestmentDetails i) { this.investmentDetails = i; }
    public RedeemDetails     getRedeemDetails()                     { return redeemDetails; }
    public void              setRedeemDetails(RedeemDetails r)      { this.redeemDetails = r; }
}
