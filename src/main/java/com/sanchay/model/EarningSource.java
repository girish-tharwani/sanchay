package com.sanchay.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents one income source for an earning family member.
 *
 * SIMPLE: gross amount entered, net = gross × (1 − tax%).
 * SALARY: annual components entered, monthly net computed via PF + TDS deductions.
 *
 * Each source links to its own recurring INCOME schedule (and optionally a PF schedule
 * for SALARY sources).
 */
public class EarningSource {

    // ── Shared fields ─────────────────────────────────────────────────────────
    private String id;
    private String sourceName;
    private FamilyMember.EarningType type;
    private String scheduleDescription;
    private LocalDate startDate;         // when this earning source starts
    private String depositAccountId;
    private int    depositDay;            // 1–28
    private String recurringScheduleId;
    private String categoryId;

    // ── SIMPLE-specific ───────────────────────────────────────────────────────
    private long   simpleAmountPaise;     // gross per period
    private String simpleFrequency;       // RecurringTransaction.Frequency name()
    private double estimatedTaxRatePct;   // shared with SALARY

    // ── SALARY-specific (all ANNUAL paise) ───────────────────────────────────
    private long    basicDaPaise;
    private long    hraPaise;
    private long    otherAllowancesPaise;
    private double  vpfPct;
    private boolean gratuityEnabled;
    private String  pfScheduleId;
    private boolean esppEnabled;
    private long    esppAmountPaise;   // monthly contribution
    private String  esppScheduleId;

    /** No-arg constructor — assigns a new UUID. */
    public EarningSource() {
        this.id = UUID.randomUUID().toString();
    }

    // ── Computed methods ──────────────────────────────────────────────────────

    /**
     * Returns the schedule transaction amount in paise (the net amount to deposit
     * per pay period).
     *
     * SIMPLE: gross × (1 − tax%)
     * SALARY: monthly gross − employee PF (12% + VPF) − monthly TDS
     */
    public long computeScheduleAmountPaise() {
        if (type == FamilyMember.EarningType.SIMPLE) {
            return Math.round(simpleAmountPaise * (1.0 - estimatedTaxRatePct / 100.0));
        }
        // SALARY — convert annual to monthly
        long basicMo = basicDaPaise / 12;
        long hraMo   = hraPaise     / 12;
        long otherMo = otherAllowancesPaise / 12;
        long gross        = basicMo + hraMo + otherMo;
        long mandEpf      = Math.round(basicMo * 0.12);
        long totalPf      = Math.round(basicMo * (0.12 + vpfPct / 100.0));
        long tds          = Math.round((gross - mandEpf) * estimatedTaxRatePct / 100.0);
        long base         = gross - totalPf - tds;
        return esppEnabled ? base - esppAmountPaise : base;
    }

    /**
     * Returns the normalised monthly in-hand paise.
     *
     * SALARY: same as computeScheduleAmountPaise() (already monthly).
     * SIMPLE: normalise schedule amount to monthly by frequency multiplier.
     *   MONTHLY=×1, QUARTERLY=÷3, ANNUALLY=÷12, ALTERNATE_YEAR=÷24.
     *   Null/unknown frequency: return net as-is.
     */
    public long computeMonthlyInHandPaise() {
        if (type == FamilyMember.EarningType.SALARY) {
            return computeScheduleAmountPaise();
        }
        long net = computeScheduleAmountPaise();
        if (simpleFrequency == null) return net;
        try {
            RecurringTransaction.Frequency freq =
                    RecurringTransaction.Frequency.valueOf(simpleFrequency);
            return switch (freq) {
                case MONTHLY        -> net;
                case QUARTERLY      -> net / 3;
                case HALF_YEARLY    -> net / 6;
                case ANNUALLY       -> net / 12;
                case ALTERNATE_YEAR -> net / 24;
            };
        } catch (IllegalArgumentException e) {
            return net;
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId()                   { return id; }
    public String getSourceName()           { return sourceName; }
    public FamilyMember.EarningType getType() { return type; }
    public String getScheduleDescription()  { return scheduleDescription; }
    public LocalDate getStartDate()         { return startDate; }
    public String getDepositAccountId()     { return depositAccountId; }
    public int    getDepositDay()           { return depositDay; }
    public String getRecurringScheduleId()  { return recurringScheduleId; }
    public String getCategoryId()           { return categoryId; }
    public long   getSimpleAmountPaise()    { return simpleAmountPaise; }
    public String getSimpleFrequency()      { return simpleFrequency; }
    public double getEstimatedTaxRatePct()  { return estimatedTaxRatePct; }
    public long   getBasicDaPaise()         { return basicDaPaise; }
    public long   getHraPaise()             { return hraPaise; }
    public long   getOtherAllowancesPaise() { return otherAllowancesPaise; }
    public double getVpfPct()               { return vpfPct; }
    public boolean isGratuityEnabled()      { return gratuityEnabled; }
    public String getPfScheduleId()         { return pfScheduleId; }
    public boolean isEsppEnabled()          { return esppEnabled; }
    public long   getEsppAmountPaise()      { return esppAmountPaise; }
    public String getEsppScheduleId()       { return esppScheduleId; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setId(String id)                                  { this.id = id; }
    public void setSourceName(String sourceName)                  { this.sourceName = sourceName; }
    public void setType(FamilyMember.EarningType type)            { this.type = type; }
    public void setScheduleDescription(String scheduleDescription){ this.scheduleDescription = scheduleDescription; }
    public void setStartDate(LocalDate startDate)                 { this.startDate = startDate; }
    public void setDepositAccountId(String depositAccountId)      { this.depositAccountId = depositAccountId; }
    public void setDepositDay(int depositDay)                     { this.depositDay = depositDay; }
    public void setRecurringScheduleId(String recurringScheduleId){ this.recurringScheduleId = recurringScheduleId; }
    public void setCategoryId(String categoryId)                  { this.categoryId = categoryId; }
    public void setSimpleAmountPaise(long simpleAmountPaise)      { this.simpleAmountPaise = simpleAmountPaise; }
    public void setSimpleFrequency(String simpleFrequency)        { this.simpleFrequency = simpleFrequency; }
    public void setEstimatedTaxRatePct(double estimatedTaxRatePct){ this.estimatedTaxRatePct = estimatedTaxRatePct; }
    public void setBasicDaPaise(long basicDaPaise)                { this.basicDaPaise = basicDaPaise; }
    public void setHraPaise(long hraPaise)                        { this.hraPaise = hraPaise; }
    public void setOtherAllowancesPaise(long otherAllowancesPaise){ this.otherAllowancesPaise = otherAllowancesPaise; }
    public void setVpfPct(double vpfPct)                          { this.vpfPct = vpfPct; }
    public void setGratuityEnabled(boolean gratuityEnabled)       { this.gratuityEnabled = gratuityEnabled; }
    public void setPfScheduleId(String pfScheduleId)              { this.pfScheduleId = pfScheduleId; }
    public void setEsppEnabled(boolean esppEnabled)               { this.esppEnabled = esppEnabled; }
    public void setEsppAmountPaise(long esppAmountPaise)          { this.esppAmountPaise = esppAmountPaise; }
    public void setEsppScheduleId(String esppScheduleId)          { this.esppScheduleId = esppScheduleId; }
}
