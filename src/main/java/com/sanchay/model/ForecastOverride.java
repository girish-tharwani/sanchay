package com.sanchay.model;

import java.time.YearMonth;
import java.util.Objects;

/**
 * A user-applied correction or exclusion for a pattern-forecasted expense sub-category.
 *
 * {@code month} is stored as "YYYY-MM"; {@code null} means the override applies to every
 * month in the projection window.  Override resolution priority in the service:
 * month-specific override > all-months override > computed forecast.
 */
public class ForecastOverride {

    private String  categoryId;
    private String  subCategoryId;
    private String  month;               // "YYYY-MM" or null = all months
    private Long    overrideAmountPaise; // non-null = amount correction; null = pure exclusion/inclusion
    private boolean excluded;

    public ForecastOverride() {}

    // ── Factory helpers ───────────────────────────────────────────────────────

    /** Manual amount correction for a specific month or all months (month == null). */
    public static ForecastOverride correction(String catId, String subCatId,
                                              YearMonth month, long amountPaise) {
        ForecastOverride o = new ForecastOverride();
        o.categoryId           = catId;
        o.subCategoryId        = subCatId;
        o.month                = month != null ? month.toString() : null;
        o.overrideAmountPaise  = amountPaise;
        o.excluded             = false;
        return o;
    }

    /** Exclusion for a specific month or all months (month == null). */
    public static ForecastOverride exclusion(String catId, String subCatId, YearMonth month) {
        ForecastOverride o = new ForecastOverride();
        o.categoryId    = catId;
        o.subCategoryId = subCatId;
        o.month         = month != null ? month.toString() : null;
        o.excluded      = true;
        return o;
    }

    /**
     * Inclusion marker — cancels a broader all-months exclusion for just this month.
     * When month == null, the service treats this as "clear all overrides for this sub-category".
     */
    public static ForecastOverride inclusionMarker(String catId, String subCatId, YearMonth month) {
        ForecastOverride o = new ForecastOverride();
        o.categoryId    = catId;
        o.subCategoryId = subCatId;
        o.month         = month != null ? month.toString() : null;
        o.excluded      = false;
        return o;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String  getCategoryId()          { return categoryId; }
    public String  getSubCategoryId()       { return subCategoryId; }
    public String  getMonth()               { return month; }
    public Long    getOverrideAmountPaise() { return overrideAmountPaise; }
    public boolean isExcluded()             { return excluded; }
    public boolean isAllMonths()            { return month == null; }

    public YearMonth toYearMonth() {
        return month != null ? YearMonth.parse(month) : null;
    }

    /** Two overrides share the same key when (categoryId, subCategoryId, month) all match. */
    public boolean sameKey(ForecastOverride other) {
        return Objects.equals(categoryId,    other.categoryId)
            && Objects.equals(subCategoryId, other.subCategoryId)
            && Objects.equals(month,         other.month);
    }
}
