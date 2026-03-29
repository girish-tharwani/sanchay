package com.sanchay.model;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Represents a household family member.
 *
 * EarningType.SIMPLE → single recurring INCOME schedule (user-specified amount + frequency).
 * EarningType.SALARY → structured salary breakdown; in-hand computed from Basic+DA, HRA,
 *                      Other Allowances, Employee PF (12% of Basic+DA), and estimated tax rate.
 *
 * Earnings configuration is held in a list of EarningSource objects (one per income source).
 * Each EarningSource links back to the auto-created recurring schedule so it can be updated
 * when salary changes and paused/resumed with the earning flag.
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

    private List<EarningSource> earningSources;

    public FamilyMember(String name, Relationship relationship, boolean earning) {
        this.id           = UUID.randomUUID().toString();
        this.name         = name;
        this.relationship = relationship;
        this.earning      = earning;
    }

    // ── Computed helpers ──────────────────────────────────────────────────────

    /**
     * Returns the total monthly net in-hand paise across all income sources.
     * Returns 0 if no earnings are configured.
     */
    public long computeInHandPaise() {
        if (!hasEarningsConfigured()) return 0;
        return earningSources.stream().mapToLong(EarningSource::computeMonthlyInHandPaise).sum();
    }

    /** Returns true when at least one EarningSource has been configured. */
    public boolean hasEarningsConfigured() {
        return earningSources != null && !earningSources.isEmpty();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String             getId()            { return id; }
    public String             getName()          { return name; }
    public Relationship       getRelationship()  { return relationship; }
    public boolean            isEarning()        { return earning; }
    public LocalDate          getDateOfBirth()   { return dateOfBirth; }
    public List<EarningSource> getEarningSources() { return earningSources; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setName(String name)                            { this.name = name; }
    public void setRelationship(Relationship r)                 { this.relationship = r; }
    public void setEarning(boolean earning)                     { this.earning = earning; }
    public void setDateOfBirth(LocalDate d)                     { this.dateOfBirth = d; }
    public void setEarningSources(List<EarningSource> sources)  { this.earningSources = sources; }

    @Override
    public String toString() { return name; }
}
