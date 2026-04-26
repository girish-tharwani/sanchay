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
    public long   costOfLivingPaise     = 150_000_000L;   // ₹15,00,000 default
    public String employmentStartDate   = null;           // ISO "YYYY-MM-DD"
    public long   monthlySipMfPaise     = 0L;             // ₹0 default
    public long   monthlySipEquityPaise = 0L;             // ₹0 default

    public int    spendingSlowGoAge     = 73;
    public int    spendingNoGoAge       = 83;
    public Double spendingSlowGoInflationAdjustmentPct = null;
    public Double spendingHealthcareInflationAdjustmentPct = null;
    public double spendingSlowGoReductionPct = 1.5;

    public java.util.List<MajorEvent> majorEvents = new java.util.ArrayList<>();

    public String lastCalculatedDate = null;  // ISO "YYYY-MM-DD"; set each time the user recalculates
}
