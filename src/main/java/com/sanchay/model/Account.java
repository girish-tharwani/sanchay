package com.sanchay.model;

import java.time.LocalDate;
import java.util.UUID;

/** Base class for all account types. */
public abstract class Account {

    public enum Status { ACTIVE, CLOSED }

    /** Stable discriminator used for JSON serialisation — never rename these values. */
    public enum AccountCategory { BANK, CREDIT_CARD, LOAN, INVESTMENT }

    private String id;
    private String name;
    private String description;
    private String currency = "INR";
    private Status status;
    private LocalDate openingDate;
    private boolean jointAccount;
    private String secondHolderName;
    private String notes;

    protected Account(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.status = Status.ACTIVE;
        this.jointAccount = false;
    }

    public String getId()               { return id; }
    public String getName()             { return name; }
    public String getDescription()      { return description; }
    public String getCurrency()         { return currency; }
    public Status getStatus()           { return status; }
    public LocalDate getOpeningDate()   { return openingDate; }
    public boolean isJointAccount()     { return jointAccount; }
    public String getSecondHolderName() { return secondHolderName; }
    public String getNotes()            { return notes; }
    public boolean isActive()           { return status == Status.ACTIVE; }

    public void setName(String name)                       { this.name = name; }
    public void setDescription(String description)         { this.description = description; }
    public void setStatus(Status status)                   { this.status = status; }
    public void setOpeningDate(LocalDate openingDate)      { this.openingDate = openingDate; }
    public void setJointAccount(boolean jointAccount)      { this.jointAccount = jointAccount; }
    public void setSecondHolderName(String name)           { this.secondHolderName = name; }
    public void setNotes(String notes)                     { this.notes = notes; }

    @Override public String toString() { return name; }

    /** Stable category used as the JSON discriminator. */
    public abstract AccountCategory getAccountCategory();

    /** Human-readable display label for the UI (not written to JSON). */
    public abstract String getAccountType();
}
