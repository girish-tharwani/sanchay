package com.sanchay.service;

/**
 * Central source of truth for the active currency.
 * Reads the currency code from {@link DataStore} (persisted in settings.json).
 *
 * Add a new {@code case} block for each additional currency code when
 * multi-currency support is extended beyond INR.
 */
public final class CurrencyConfig {

    private CurrencyConfig() {}

    /** The ISO 4217 currency code currently selected by the user (e.g. "INR"). */
    public static String code() {
        return DataStore.getInstance().getCurrency();
    }

    /** The display symbol for the active currency (e.g. "₹"). */
    public static String symbol() {
        switch (code()) {
            case "INR": return "₹";
            // Add future currencies here, e.g.:
            // case "USD": return "$";
            // case "EUR": return "€";
            default:    return code();   // fall back to the code itself
        }
    }

    /**
     * The grouping style used by {@link MoneyFormatter}.
     * INDIAN  → last-3 then every-2 (10,00,000)
     * WESTERN → every-3           (1,000,000)
     */
    public enum GroupingStyle { INDIAN, WESTERN }

    public static GroupingStyle groupingStyle() {
        switch (code()) {
            case "INR": return GroupingStyle.INDIAN;
            default:    return GroupingStyle.WESTERN;
        }
    }

    /**
     * Whether compact formatting should use Indian units (Lakh / Crore)
     * or Western units (K / M / B).
     */
    public enum CompactUnits { INDIAN, WESTERN }

    public static CompactUnits compactUnits() {
        switch (code()) {
            case "INR": return CompactUnits.INDIAN;
            default:    return CompactUnits.WESTERN;
        }
    }
}
