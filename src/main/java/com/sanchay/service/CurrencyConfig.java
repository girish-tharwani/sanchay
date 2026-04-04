package com.sanchay.service;

/**
 * Central source of truth for the active currency.
 * Currently fixed to INR; extend by reading from settings when
 * multi-currency support is added to the Settings screen.
 */
public final class CurrencyConfig {

    private static String symbol = "₹";

    private CurrencyConfig() {}

    public static String symbol() { return symbol; }
}
