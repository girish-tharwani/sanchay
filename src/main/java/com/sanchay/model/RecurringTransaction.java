package com.sanchay.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A recurring transaction schedule (EMI, SIP, rent, salary, CC bill, etc.).
 * Amount is stored in paise; zero means the amount is variable (e.g. CC payment reminders).
 */
public class RecurringTransaction {

    public enum Frequency { MONTHLY, QUARTERLY, ANNUALLY, ALTERNATE_YEAR }
    public enum Status    { ACTIVE, PAUSED, COMPLETED }

    private String id;
    private String description;
    private Transaction.Type transactionType;
    private Frequency frequency;
    private int dueDayOfMonth;          // 1–28
    private LocalDate startDate;
    private LocalDate endDate;          // null = open-ended
    private long amountPaise;           // 0 for CC_PAYMENT reminders (variable)
    private String categoryId;
    private String subCategoryId;
    private String fromAccountId;
    private String toAccountId;
    private String notes;
    private Status status;
    /** True if auto-created by the system (RD, loan, or credit card account setup). */
    private boolean autoCreated;
    /** Last date on which this recurring was confirmed and posted. */
    private LocalDate lastRecordedDate;
    /**
     * When > 0, the occurrence is auto-recorded (no user confirmation) once the due date
     * has passed by this many days. 0 = manual confirmation always required.
     */
    private int autoRecordAfterDays;

    /** RD only: total instalments already paid before this schedule was set up (paise). */
    private long rdOpeningBalancePaise;
    /** RD only: expected maturity amount (paise). */
    private long rdMaturityAmountPaise;

    // ── Investment sub-type specific fields ───────────────────────────────────
    /** RD: reference number identifying this RD schedule. */
    private String rdRef;
    /** FD/RD: annual interest rate as a percentage (e.g. 7.25). */
    private Double interestRate;
    /** FD/RD: maturity date. */
    private LocalDate maturityDate;
    /** FD: reference number. */
    private String fdRef;
    /** FD: expected maturity amount (paise). */
    private long fdMaturityAmountPaise;
    /** MF/Equity/Bonds: scheme or script name. */
    private String schemeScript;
    /** MF/Equity/Bonds: units / NAV info at investment time. */
    private String unitsNav;

    public RecurringTransaction(String description, Transaction.Type type,
                                Frequency frequency, int dueDayOfMonth,
                                LocalDate startDate, long amountPaise) {
        this.id = UUID.randomUUID().toString();
        this.description = description;
        this.transactionType = type;
        this.frequency = frequency;
        this.dueDayOfMonth = dueDayOfMonth;
        this.startDate = startDate;
        this.amountPaise = amountPaise;
        this.status = Status.ACTIVE;
    }

    /** Returns true if this schedule has an occurrence due today or overdue. */
    public boolean hasPendingOccurrence() {
        if (status != Status.ACTIVE) return false;
        LocalDate today = LocalDate.now();
        LocalDate nextDue = getNextDueDate();
        return nextDue != null && !nextDue.isAfter(today);
    }

    /** Calculates the next due date based on last recorded date or start date. */
    public LocalDate getNextDueDate() {
        LocalDate base = (lastRecordedDate != null)
                ? advanceByFrequency(lastRecordedDate)
                : startDate;
        if (base == null) return null;
        if (endDate != null && base.isAfter(endDate)) return null;
        int day = Math.min(dueDayOfMonth, base.lengthOfMonth());
        return base.withDayOfMonth(day);
    }

    private LocalDate advanceByFrequency(LocalDate from) {
        return switch (frequency) {
            case MONTHLY        -> from.plusMonths(1);
            case QUARTERLY      -> from.plusMonths(3);
            case ANNUALLY       -> from.plusYears(1);
            case ALTERNATE_YEAR -> from.plusYears(2);
        };
    }

    /** Called when the user records or skips an occurrence. */
    public void markRecorded(LocalDate onDate) {
        this.lastRecordedDate = onDate;
        if (persistence_saveHook != null) persistence_saveHook.run();
    }

    // Hook so markRecorded() can trigger a save without a direct DataStore reference.
    // Set by DataStore after construction if needed; null-safe.
    private transient Runnable persistence_saveHook;
    public void setPersistenceSaveHook(Runnable hook) { this.persistence_saveHook = hook; }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId()                   { return id; }
    public String getDescription()          { return description; }
    public Transaction.Type getTransactionType() { return transactionType; }
    public Frequency getFrequency()         { return frequency; }
    public int getDueDayOfMonth()           { return dueDayOfMonth; }
    public LocalDate getStartDate()         { return startDate; }
    public LocalDate getEndDate()           { return endDate; }
    public long getAmountPaise()            { return amountPaise; }
    public String getCategoryId()           { return categoryId; }
    public String getSubCategoryId()        { return subCategoryId; }
    public String getFromAccountId()        { return fromAccountId; }
    public String getToAccountId()          { return toAccountId; }
    public String getNotes()                { return notes; }
    public Status getStatus()               { return status; }
    public boolean isAutoCreated()          { return autoCreated; }
    public LocalDate getLastRecordedDate()  { return lastRecordedDate; }
    public int getAutoRecordAfterDays()     { return autoRecordAfterDays; }
    public long getRdOpeningBalancePaise()  { return rdOpeningBalancePaise; }
    public long getRdMaturityAmountPaise()  { return rdMaturityAmountPaise; }
    public String getRdRef()                { return rdRef; }
    public Double getInterestRate()         { return interestRate; }
    public LocalDate getMaturityDate()      { return maturityDate; }
    public String getFdRef()                { return fdRef; }
    public long getFdMaturityAmountPaise()  { return fdMaturityAmountPaise; }
    public String getSchemeScript()         { return schemeScript; }
    public String getUnitsNav()             { return unitsNav; }

    public String getAmountInr() {
        return amountPaise > 0
                ? String.format("₹%,.2f", amountPaise / 100.0)
                : "Variable";
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setDescription(String d)            { this.description = d; }
    public void setTransactionType(Transaction.Type t) { this.transactionType = t; }
    public void setFrequency(Frequency f)           { this.frequency = f; }
    public void setDueDayOfMonth(int d)             { this.dueDayOfMonth = d; }
    public void setStartDate(LocalDate d)           { this.startDate = d; }
    public void setEndDate(LocalDate d)             { this.endDate = d; }
    public void setAmountPaise(long p)              { this.amountPaise = p; }
    public void setCategoryId(String c)             { this.categoryId = c; }
    public void setSubCategoryId(String s)          { this.subCategoryId = s; }
    public void setFromAccountId(String id)         { this.fromAccountId = id; }
    public void setToAccountId(String id)           { this.toAccountId = id; }
    public void setNotes(String n)                  { this.notes = n; }
    public void setStatus(Status s)                 { this.status = s; }
    public void setAutoCreated(boolean b)           { this.autoCreated = b; }
    public void setLastRecordedDate(LocalDate d)    { this.lastRecordedDate = d; }
    public void setAutoRecordAfterDays(int n)        { this.autoRecordAfterDays = n; }
    public void setRdOpeningBalancePaise(long p)     { this.rdOpeningBalancePaise = p; }
    public void setRdMaturityAmountPaise(long p)     { this.rdMaturityAmountPaise = p; }
    public void setRdRef(String s)                   { this.rdRef = s; }
    public void setInterestRate(Double d)            { this.interestRate = d; }
    public void setMaturityDate(LocalDate d)         { this.maturityDate = d; }
    public void setFdRef(String s)                   { this.fdRef = s; }
    public void setFdMaturityAmountPaise(long p)     { this.fdMaturityAmountPaise = p; }
    public void setSchemeScript(String s)            { this.schemeScript = s; }
    public void setUnitsNav(String s)                { this.unitsNav = s; }
}