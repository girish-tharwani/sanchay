package com.sanchay.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents a household family member.
 *
 * EarningType.SIMPLE → single recurring INCOME schedule (user-specified amount + frequency).
 * EarningType.SALARY → structured salary breakdown; in-hand computed from Basic+DA, HRA,
 *                      Other Allowances, Employee PF (12% of Basic+DA), and estimated tax rate.
 * recurringScheduleId links back to the auto-created recurring schedule so it can
 * be updated when salary changes and paused/resumed with the earning flag.
 */
public class FamilyMember {

    public enum Relationship { SELF, SPOUSE, CHILD, PARENT, SIBLING, OTHER }

    /** How the earnings are specified. Null means earning=true but not yet configured. */
    public enum EarningType { SIMPLE, SALARY }

    private String       id;
    private String       name;
    private Relationship relationship;
    private boolean      earning;
    private LocalDate    dateOfBirth;

    // ── Earnings configuration (shared) ──────────────────────────────────────
    private EarningType earningType;
    private String      depositAccountId;
    private int         depositDay;               // 1–28
    private String      scheduleDescription;
    private String      recurringScheduleId;      // ID of the auto-created salary INCOME schedule
    private String      pfScheduleId;             // ID of the auto-created PF INVESTMENT schedule

    // ── SIMPLE-specific ───────────────────────────────────────────────────────
    private long        simpleAmountPaise;
    private String      simpleFrequency;          // RecurringTransaction.Frequency name()

    // ── SALARY-specific ───────────────────────────────────────────────────────
    private long        basicDaPaise;
    private long        hraPaise;
    private long        otherAllowancesPaise;
    private double      estimatedTaxRatePct;
    private double      vpfPct;               // Voluntary PF % of Basic+DA (additive to 12%)
    private boolean     gratuityEnabled;      // Whether gratuity is shown in breakdown

    public FamilyMember(String name, Relationship relationship, boolean earning) {
        this.id           = UUID.randomUUID().toString();
        this.name         = name;
        this.relationship = relationship;
        this.earning      = earning;
    }

    // ── Computed helper ───────────────────────────────────────────────────────

    /**
     * Returns the monthly net in-hand paise for this member.
     * SIMPLE → the configured amount.
     * SALARY → Gross − Employee PF (12% of Basic+DA) − Estimated TDS.
     * Returns 0 if earnings are not configured.
     */
    public long computeInHandPaise() {
        if (earningType == EarningType.SIMPLE) return simpleAmountPaise;
        if (earningType == EarningType.SALARY) {
            long gross        = basicDaPaise + hraPaise + otherAllowancesPaise;
            long mandatoryEpf = Math.round(basicDaPaise * 0.12);
            long totalEmpPf   = Math.round(basicDaPaise * (0.12 + vpfPct / 100.0));
            long tds          = Math.round((gross - mandatoryEpf) * estimatedTaxRatePct / 100.0);
            return gross - totalEmpPf - tds;
        }
        return 0;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String       getId()                   { return id; }
    public String       getName()                 { return name; }
    public Relationship getRelationship()         { return relationship; }
    public boolean      isEarning()               { return earning; }
    public LocalDate    getDateOfBirth()          { return dateOfBirth; }
    public EarningType  getEarningType()          { return earningType; }
    public String       getDepositAccountId()     { return depositAccountId; }
    public int          getDepositDay()           { return depositDay; }
    public String       getScheduleDescription()  { return scheduleDescription; }
    public String       getRecurringScheduleId()  { return recurringScheduleId; }
    public String       getPfScheduleId()          { return pfScheduleId; }
    public long         getSimpleAmountPaise()    { return simpleAmountPaise; }
    public String       getSimpleFrequency()      { return simpleFrequency; }
    public long         getBasicDaPaise()         { return basicDaPaise; }
    public long         getHraPaise()             { return hraPaise; }
    public long         getOtherAllowancesPaise() { return otherAllowancesPaise; }
    public double       getEstimatedTaxRatePct()  { return estimatedTaxRatePct; }
    public double       getVpfPct()               { return vpfPct; }
    public boolean      isGratuityEnabled()       { return gratuityEnabled; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setName(String name)                        { this.name = name; }
    public void setRelationship(Relationship r)             { this.relationship = r; }
    public void setEarning(boolean earning)                 { this.earning = earning; }
    public void setDateOfBirth(LocalDate d)                 { this.dateOfBirth = d; }
    public void setEarningType(EarningType t)               { this.earningType = t; }
    public void setDepositAccountId(String id)              { this.depositAccountId = id; }
    public void setDepositDay(int day)                      { this.depositDay = day; }
    public void setScheduleDescription(String d)            { this.scheduleDescription = d; }
    public void setRecurringScheduleId(String id)           { this.recurringScheduleId = id; }
    public void setPfScheduleId(String id)                  { this.pfScheduleId = id; }
    public void setSimpleAmountPaise(long p)                { this.simpleAmountPaise = p; }
    public void setSimpleFrequency(String f)                { this.simpleFrequency = f; }
    public void setBasicDaPaise(long p)                     { this.basicDaPaise = p; }
    public void setHraPaise(long p)                         { this.hraPaise = p; }
    public void setOtherAllowancesPaise(long p)             { this.otherAllowancesPaise = p; }
    public void setEstimatedTaxRatePct(double r)            { this.estimatedTaxRatePct = r; }
    public void setVpfPct(double v)                         { this.vpfPct = v; }
    public void setGratuityEnabled(boolean b)               { this.gratuityEnabled = b; }

    @Override
    public String toString() { return name; }
}