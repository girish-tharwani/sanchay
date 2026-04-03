package com.sanchay.model;

import java.util.UUID;

/** A user-defined financial event with a forecasted expense, tracked against actual spend. */
public class MajorEvent {

    public enum EventType { ONE_TIME, RECURRING }

    public enum Frequency { MONTHLY, QUARTERLY, YEARLY }

    private String    id;
    private String    name;
    private EventType type;
    private Frequency frequency;     // null for ONE_TIME
    private long      amountPaise;   // per-occurrence for RECURRING; total for ONE_TIME
    private String    categoryId;    // optional; used to compute actual spend
    private String    subCategoryId; // optional
    private String    startDate;     // ISO "YYYY-MM-DD"; expense tracking starts from this date
    private String    endDate;       // ISO "YYYY-MM-DD"; for RECURRING — overrides retirement date

    public MajorEvent() {
        this.id   = UUID.randomUUID().toString();
        this.type = EventType.ONE_TIME;
    }

    public String    getId()            { return id; }
    public String    getName()          { return name; }
    public EventType getType()          { return type; }
    public Frequency getFrequency()     { return frequency; }
    public long      getAmountPaise()   { return amountPaise; }
    public String    getCategoryId()    { return categoryId; }
    public String    getSubCategoryId() { return subCategoryId; }
    public String    getStartDate()     { return startDate; }
    public String    getEndDate()       { return endDate; }

    public void setName(String n)          { this.name = n; }
    public void setType(EventType t)       { this.type = t; }
    public void setFrequency(Frequency f)  { this.frequency = f; }
    public void setAmountPaise(long p)     { this.amountPaise = p; }
    public void setCategoryId(String c)    { this.categoryId = c; }
    public void setSubCategoryId(String s) { this.subCategoryId = s; }
    public void setStartDate(String d)     { this.startDate = d; }
    public void setEndDate(String d)       { this.endDate = d; }
}
