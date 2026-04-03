package com.sanchay.model;

/**
 * User-configurable parameters for the Financial Planning screen.
 * Persisted to {@code plan_params.json} in the app data folder.
 * All amounts are in paise (integer). Percentages are doubles (e.g. 30.0 = 30%).
 */
public class PlanParameters {

    public int    retirementAge         = 60;
    public String retirementDate        = null;  // ISO "YYYY-MM-DD"; preferred over retirementAge when set
    public int    lifeExpectancy        = 80;
    public double preRetireTaxPct       = 30.0;
    public double postRetireTaxPct      = 20.0;
    public double rorEquitiesPct        = 12.0;
    public double rorMfPct              = 10.0;
    public double rorPfPct              = 8.1;
    public double rorPostRetirePct      = 7.0;
    public double inflationPct          = 6.0;
    public long   costOfLivingPaise     = 15_000_000L;   // ₹1,50,000 default
    public String employmentStartDate   = null;           // ISO "YYYY-MM-DD"
    public long   monthlySipMfPaise     = 1_000_000L;    // ₹10,000 default
    public long   monthlySipEquityPaise = 500_000L;      // ₹5,000 default

    public java.util.List<MajorEvent> majorEvents = new java.util.ArrayList<>();
}
