package com.sanchay.service;

/**
 * Extracts stable, human-meaningful keys from raw bank transaction descriptions.
 * Strips payment-rail prefixes (UPI/, NEFT-, BIL/INFT/, etc.), transaction IDs,
 * reference hashes, bank names, and other noise so that repeated transactions
 * to the same merchant produce the same key.
 */
public final class DescriptionNormalizer {
    private DescriptionNormalizer() {}

    /**
     * Normalizes a raw bank transaction description to a stable key.
     *
     * Examples:
     *   "UPI/Swiggy Lim/swiggy1online.@axb/..."  -> "swiggy"
     *   "NEFT-BARCN...-BARCLAYS GLOBAL SERVICE..." -> "barclays global service centre salary"
     *   "BIL/INFT/EJ.../INFT//VIDHI THARWANI"     -> "vidhi tharwani"
     *   "CAM/09852OAR/CASH WDL/27-09-25"           -> "cash withdrawal atm"
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String s = raw.trim();
        String u = s.toUpperCase();

        if (u.startsWith("UPI/"))  return normalizeUpi(s);
        if (u.startsWith("UPL/"))  return normalizeUpl(s);
        if (u.startsWith("NEFT"))  return normalizeNeft(s);
        if (u.startsWith("BIL/NEFT/")) return normalizeBilNeft(s);
        if (u.startsWith("BIL/INFT/")) return normalizeBilInft(s);
        if (u.startsWith("BIL/"))  return normalizeBil(s);
        if (u.startsWith("CAM/"))  return "cash withdrawal atm";
        if (u.startsWith("ACH/"))  return normalizeAch(s);
        if (s.matches("(?i).*int\\.?pd.*") || u.contains("INTEREST")) return "interest credit";
        if (u.startsWith("TRF TO FD")) return "transfer to fd";
        if (u.startsWith("TO RD AC"))  return "rd account transfer";

        return cleanDesc(s);
    }

    // ── UPI: UPI/{display}/{vpa}/{remark}/{bank}/{txnId}/{ref}/
    private static String normalizeUpi(String s) {
        String[] p = s.split("/", -1);
        String display = p.length > 1 ? p[1].trim() : "";
        String vpa     = p.length > 2 ? p[2].trim() : "";
        return cleanDesc(extractUpiMerchant(display, vpa));
    }

    // ── UPL: UPL/{txnId}/UPI/{account}/{bank}/{ref} -- linked-account auto-debit
    private static String normalizeUpl(String s) {
        String[] p = s.split("/", -1);
        String account = p.length > 3 ? p[3].replaceAll("[^0-9]", "") : "";
        String suffix  = account.length() >= 4 ? account.substring(account.length() - 4) : account;
        return cleanDesc("auto debit " + suffix);
    }

    // ── NEFT: NEFT-{ref}-{counterparty}-{more}-...
    private static String normalizeNeft(String s) {
        String body = s.replaceFirst("(?i)^NEFT[-/\\s]*", "");
        String[] parts = body.split("[-/]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty() || p.matches("\\d+") || p.matches("[A-Z0-9]{10,}"))
                continue;
            sb.append(p).append(" ");
        }
        return cleanDesc(sb.toString());
    }

    // ── BIL/NEFT/{ref}/{party}/{bank}
    private static String normalizeBilNeft(String s) {
        String[] p = s.split("/", -1);
        String party = p.length > 3 ? p[3].trim() : s;
        return cleanDesc(party);
    }

    // ── BIL/INFT/{ref}/{desc}/{party}
    private static String normalizeBilInft(String s) {
        String[] p = s.split("/", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 3; i < p.length; i++) {
            String part = p[i].trim();
            if (!part.isEmpty()
                    && !part.equalsIgnoreCase("INFT")
                    && !part.matches("[A-Z0-9]{6,}")
                    && !part.matches("\\d+")) {
                sb.append(part).append(" ");
            }
        }
        return cleanDesc(sb.toString());
    }

    // ── BIL/{other}: e.g. "BIL/Home Loan XX47458 EMI Girish T"
    private static String normalizeBil(String s) {
        String rest = s.substring(4).replaceAll("(?i)XX\\d+", "").trim();
        return cleanDesc(rest);
    }

    // ── ACH: NACH/ECS mandate debit
    // p[1] often looks like "KOTAKMF05032026 CAMS" — strip ALL digit sequences so the
    // date component doesn't make every month a distinct key.
    private static String normalizeAch(String s) {
        String[] p = s.split("/", -1);
        String code = p.length > 1 ? p[1].replaceAll("\\d+", " ").trim() : "";
        return cleanDesc("nach " + code);
    }

    /** Picks the stable merchant identifier from a UPI display name + VPA pair. */
    static String extractUpiMerchant(String display, String vpa) {
        String handle = vpa.contains("@") ? vpa.substring(0, vpa.indexOf('@')) : vpa;
        handle = handle.replaceAll("[.\\-_]+$", "").replaceAll("\\d+$", "").trim();

        boolean isGenericGateway = handle.isEmpty()
                || handle.length() <= 2
                || handle.matches("\\d+")
                || handle.toLowerCase().matches("paytm.*")
                || handle.toLowerCase().startsWith("vyapar")
                || handle.toLowerCase().startsWith("pinelabs")
                || handle.toLowerCase().startsWith("razorpay");

        if (isGenericGateway) {
            String fallback = display.replaceAll("[^a-zA-Z0-9 ]+", " ").trim();
            fallback = fallback.replaceAll("\\s+\\S$", "").trim();
            fallback = fallback.replaceAll("\\d+$", "").trim();
            return fallback;
        }

        handle = handle.replaceAll("(?i)(online|pay|app|store|\\d+)$", "").trim();
        return handle.isEmpty() ? display : handle;
    }

    /**
     * Lowercases, removes non-alphanumeric characters, collapses whitespace, and strips
     * the trailing " &lt;city&gt; IN" location suffix common in Indian bank/CC statement
     * descriptions (e.g. "NETFLIX DI SI MUMBAI IN" → "netflix di si").
     * Stripping at normalization time keeps city names out of rule keys entirely,
     * so matching never needs a city stopword list.
     */
    static String cleanDesc(String s) {
        String clean = s.trim().toLowerCase()
                .replaceAll("[^a-z0-9 ]+", " ")
                .replaceAll("\\s{2,}", " ")
                .strip();
        // Strip trailing "<word> in" — the ISO-3166 country code "IN" is appended
        // after the city name in most Indian bank/CC merchant descriptions.
        return clean.replaceAll("\\s+\\w+\\s+in$", "").strip();
    }

    /**
     * Returns true if two normalized descriptions match (exact or fuzzy word overlap).
     * Only words of 5+ characters are compared to avoid false positives on short tokens.
     * Prefix matching is also accepted so that old stored keys containing an embedded date
     * (e.g. "kotakmf05012026") still match a freshly-normalized key with the date stripped
     * (e.g. "kotakmf").
     */
    public static boolean matches(String needle, String haystack) {
        if (needle.equals(haystack)) return true;
        java.util.Set<String> nw = java.util.Arrays.stream(needle.split(" "))
                .filter(w -> w.length() >= 5).collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> hw = java.util.Arrays.stream(haystack.split(" "))
                .filter(w -> w.length() >= 5).collect(java.util.stream.Collectors.toSet());
        return nw.stream().anyMatch(nWord ->
                hw.stream().anyMatch(hWord ->
                        hWord.equals(nWord)
                        || hWord.startsWith(nWord)
                        || nWord.startsWith(hWord)));
    }
}
