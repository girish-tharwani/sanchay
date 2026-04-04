package com.sanchay.service;

/**
 * Single formatting gateway for all monetary amounts.
 * All methods read the active currency symbol from {@link CurrencyConfig}.
 *
 * Indian grouping (10,00,000 style) is implemented manually because Java's
 * DecimalFormat does not reliably apply secondary grouping sizes on all JVMs.
 * Algorithm: last 3 digits form the first group; every 2 digits after that.
 */
public final class MoneyFormatter {

    private MoneyFormatter() {}

    // ── Core helpers ──────────────────────────────────────────────────────────

    /**
     * Applies Indian comma grouping to the integer string of a number.
     * "1691802" → "16,91,802"
     * "1000000" → "10,00,000"
     */
    private static String indianGroup(String intDigits) {
        if (intDigits.length() <= 3) return intDigits;
        String result = intDigits.substring(intDigits.length() - 3);
        String rest   = intDigits.substring(0, intDigits.length() - 3);
        while (!rest.isEmpty()) {
            int take  = Math.min(2, rest.length());
            result    = rest.substring(rest.length() - take) + "," + result;
            rest      = rest.substring(0, rest.length() - take);
        }
        return result;
    }

    /** Formats paise with the active currency's grouping style and the given decimal places. */
    private static String applyGrouping(long paise, int decimals) {
        String raw  = String.format("%." + decimals + "f", Math.abs(paise) / 100.0);
        int dot     = raw.indexOf('.');
        String intP = dot >= 0 ? raw.substring(0, dot) : raw;
        String decP = dot >= 0 ? raw.substring(dot)    : "";
        String grouped;
        switch (CurrencyConfig.groupingStyle()) {
            case INDIAN:  grouped = indianGroup(intP); break;
            default:      grouped = westernGroup(intP); break;
        }
        return CurrencyConfig.symbol()
                + (paise < 0 ? "-" : "")
                + grouped
                + decP;
    }

    /** Applies Western comma grouping every 3 digits: "1000000" → "1,000,000" */
    private static String westernGroup(String intDigits) {
        if (intDigits.length() <= 3) return intDigits;
        StringBuilder sb = new StringBuilder();
        int offset = intDigits.length() % 3;
        if (offset > 0) sb.append(intDigits, 0, offset);
        for (int i = offset; i < intDigits.length(); i += 3) {
            if (sb.length() > 0) sb.append(',');
            sb.append(intDigits, i, i + 3);
        }
        return sb.toString();
    }

    // ── Display formatting ────────────────────────────────────────────────────

    /**
     * Full-precision with active currency grouping: ₹16,91,802.83 (INR) or $1,234,567.89 (others).
     * Use for transaction lists, account details, dialog fields.
     */
    public static String format(long paise) {
        return applyGrouping(paise, 2);
    }

    /**
     * No decimals with active currency grouping: ₹16,91,802 (INR) or $1,234,567 (others).
     * Use for account cards, table cells, planning rows.
     */
    public static String formatNoDecimal(long paise) {
        return applyGrouping(paise, 0);
    }

    /**
     * Compact KPI tile display.
     *   INR:     ≥ 1 Cr → ₹3.31 Cr  |  ≥ 1 L → ₹45 Lakh  |  otherwise → ₹12,500
     *   Others:  ≥ 1 B  → $3.31B     |  ≥ 1 M → $45M       |  ≥ 1 K → $12.5K  |  otherwise → $500
     * Replaces UiUtils.formatCorpusDisplay().
     */
    public static String formatCompact(long paise) {
        String sym  = CurrencyConfig.symbol();
        long rupees = paise / 100;
        switch (CurrencyConfig.compactUnits()) {
            case INDIAN:
                if (rupees >= 1_00_00_000L) return sym + String.format("%.2f Cr",  rupees / 1_00_00_000.00);
                if (rupees >= 1_00_000L)    return sym + Math.round(rupees / 1_00_000.00) + " Lakh";
                break;
            default:
                if (rupees >= 1_000_000_000L) return sym + String.format("%.2fB", rupees / 1_000_000_000.00);
                if (rupees >= 1_000_000L)     return sym + String.format("%.0fM", rupees / 1_000_000.00);
                if (rupees >= 1_000L)         return sym + String.format("%.1fK", rupees / 1_000.00);
                break;
        }
        return formatNoDecimal(paise);
    }

    /**
     * Compact table display with accounting brackets for negatives.
     *   INR positive:    ₹3.31Cr / ₹45.2L / ₹12.5K / ₹500
     *   Others positive: $3.31B  / $45.2M  / $12.5K / $500
     *   Negative (any):  (₹3.31Cr)
     * Replaces CashFlowForecastTab.formatPaise().
     */
    public static String formatTableCompact(long paise) {
        String sym  = CurrencyConfig.symbol();
        long   abs  = Math.abs(paise);
        double absR = abs / 100.0;
        String formatted;
        switch (CurrencyConfig.compactUnits()) {
            case INDIAN:
                if      (absR >= 1_00_00_000) formatted = String.format("%.2fCr", absR / 1_00_00_000.0);
                else if (absR >= 1_00_000)    formatted = String.format("%.2fL",  absR / 1_00_000.0);
                else if (absR >= 1_000)       formatted = String.format("%.1fK",  absR / 1_000.0);
                else                          formatted = String.format("%.0f",   absR);
                break;
            default:
                if      (absR >= 1_000_000_000) formatted = String.format("%.2fB", absR / 1_000_000_000.0);
                else if (absR >= 1_000_000)     formatted = String.format("%.2fM", absR / 1_000_000.0);
                else if (absR >= 1_000)         formatted = String.format("%.1fK", absR / 1_000.0);
                else                            formatted = String.format("%.0f",  absR);
                break;
        }
        return paise < 0 ? "(" + sym + formatted + ")" : sym + formatted;
    }

    /**
     * Full-precision with accounting brackets for negatives:
     *   (₹16,91,802.83) for negative, ₹16,91,802.83 for positive.
     * Use for signed transaction amounts (debit/credit columns).
     */
    public static String formatSigned(long paise) {
        if (paise < 0) return "(" + format(-paise) + ")";
        return format(paise);
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    /**
     * Strips the active currency symbol and grouping separators, parses as rupees,
     * and returns paise. Throws {@link NumberFormatException} on invalid input.
     */
    public static long parseAmount(String text) {
        String clean = text.replace(CurrencyConfig.symbol(), "")
                           .replace(",", "")
                           .trim();
        return Math.round(Double.parseDouble(clean) * 100);
    }

    /** Same as {@link #parseAmount} but returns {@code fallback} on any parse error. */
    public static long parseAmountSafe(String text, long fallback) {
        try { return parseAmount(text); }
        catch (Exception e) { return fallback; }
    }

    // ── Symbol access ─────────────────────────────────────────────────────────

    /** The active currency symbol, e.g. "₹". */
    public static String symbol() { return CurrencyConfig.symbol(); }
}
