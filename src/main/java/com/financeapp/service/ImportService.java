package com.financeapp.service;

import com.financeapp.model.*;
import com.financeapp.model.Transaction.SourceIndicator;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Stateless service: CSV parsing, column-mapping lookup, dedup hashing,
 * import execution and reconciliation logic.
 */
public class ImportService {

    // ── Public result types ───────────────────────────────────────────────────

    public static class ImportResult {
        public int newCount        = 0;
        public int reconciledCount = 0;
        public int skippedCount    = 0;
        public final List<AmbiguousMatch> ambiguous = new ArrayList<>();
    }

    public static class AmbiguousMatch {
        public final Transaction       imported;
        public final List<Transaction> candidates;
        public AmbiguousMatch(Transaction imp, List<Transaction> cands) {
            this.imported   = imp;
            this.candidates = cands;
        }
    }

    // ── CSV parsing ───────────────────────────────────────────────────────────

    /**
     * Reads a CSV file and returns all non-blank rows as String arrays.
     * Handles RFC-4180 quoting (double-quote escaping inside quoted fields).
     */
    public static List<String[]> parseCsv(File file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                rows.add(parseLine(line));
            }
        }
        return rows;
    }

    private static String[] parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb    = new StringBuilder();
        boolean inQuotes    = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"'); i++;           // escaped quote
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else {
                if      (c == '"') { inQuotes = true; }
                else if (c == ',') { fields.add(sb.toString().strip()); sb.setLength(0); }
                else               { sb.append(c); }
            }
        }
        fields.add(sb.toString().strip());
        return fields.toArray(new String[0]);
    }

    // ── Header validation ─────────────────────────────────────────────────────

    /**
     * Returns true when the row looks like a header: at least 2 cells that are
     * neither pure numbers nor date-shaped strings.
     */
    public static boolean isLikelyHeader(String[] row) {
        if (row == null || row.length < 2) return false;
        int textCells = 0;
        for (String cell : row) {
            if (cell == null || cell.isBlank()) continue;
            String clean = cell.replaceAll("[,₹$%\\s]", "");
            try { Double.parseDouble(clean); continue; } catch (NumberFormatException ignored) {}
            if (looksLikeDate(cell)) continue;
            textCells++;
        }
        return textCells >= 2;
    }

    private static boolean looksLikeDate(String s) {
        return s.matches("\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}")
            || s.matches("\\d{4}[/\\-]\\d{1,2}[/\\-]\\d{1,2}")
            || s.matches("\\d{1,2}[/\\-][A-Za-z]{3}[/\\-]\\d{2,4}");
    }

    // ── Mapping lookup ────────────────────────────────────────────────────────

    /**
     * Returns the saved ImportMapping for the given account, or null if none exists.
     */
    public static ImportMapping findMapping(String accountId, List<ImportMapping> mappings) {
        return mappings.stream()
                .filter(m -> accountId.equals(m.getAccountId()))
                .findFirst().orElse(null);
    }

    // ── Hash ──────────────────────────────────────────────────────────────────

    /**
     * Produces a SHA-256 hex string over "date|amount|description" (normalised).
     * Used to detect duplicate imports.
     */
    public static String computeHash(String date, String amount, String description) {
        String raw = date + "|" + amount + "|"
                + (description == null ? "" : description.strip().toLowerCase());
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : bytes) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return date + "|" + amount;   // fallback (should never happen)
        }
    }

    // ── Import execution ──────────────────────────────────────────────────────

    /**
     * Processes all data rows (row 0 is the header, skipped automatically),
     * applies reconciliation rules and updates the DataStore.
     *
     * Uses a two-pass approach to prevent silent incorrect reconciliation:
     *   Pass 1 — parse every CSV row and compute manual matches without committing anything.
     *   Pass 2 — detect "contested" manuals (matched by 2+ CSV rows), then:
     *     - 0 matches              → add as new IMPORTED transaction
     *     - 1 match, not contested → reconcile silently
     *     - 1 match, contested     → add to ambiguous list (user must choose)
     *     - 2+ matches             → add to ambiguous list
     *
     * Merge strategy when an imported row matches exactly one manual entry:
     *   - Imported wins : date, amount, importHash
     *   - Manual wins   : category, subCategory, familyMember
     *   - Description   : if different, bank description is appended to notes
     *   - sourceIndicator → RECONCILED
     */
    public static ImportResult executeImport(List<String[]> rows,
                                             ImportMapping mapping,
                                             Account account,
                                             DataStore store) {
        ImportResult result = new ImportResult();

        // Build header → column-index lookup
        String[] headers = rows.get(0);
        Map<String, Integer> hIdx = new HashMap<>();
        for (int i = 0; i < headers.length; i++) hIdx.put(headers[i], i);

        // Pre-compute set of hashes already in the store (for dedup).
        // Only IMPORTED and RECONCILED transactions block re-import.
        // MANUAL transactions may carry a stale importHash from a previous run;
        // excluding them ensures a MANUAL entry can still be reconciled against
        // a CSV row whose hash matches the old hash on that manual transaction.
        Set<String> existingHashes = store.getTransactions().stream()
                .filter(t -> t.getImportHash() != null)
                .filter(t -> t.getSourceIndicator() == SourceIndicator.IMPORTED
                          || t.getSourceIndicator() == SourceIndicator.RECONCILED)
                .map(Transaction::getImportHash)
                .collect(Collectors.toSet());

        // ── Pass 1: parse all rows and compute matches without committing ─────
        List<String>            candidateHashes   = new ArrayList<>();
        List<Transaction>       candidateImported = new ArrayList<>();
        List<List<Transaction>> candidateMatches  = new ArrayList<>();

        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            if (row.length == 0 || (row.length == 1 && row[0].isBlank())) continue;

            ParsedRow pr = parseRow(row, hIdx, mapping);
            if (pr == null) continue;

            String hash = computeHash(pr.dateStr,
                    String.valueOf(Math.abs(pr.amountPaise)), pr.description);

            // Dedup — skip if already imported
            if (existingHashes.contains(hash)) {
                result.skippedCount++;
                continue;
            }
            existingHashes.add(hash); // prevent intra-batch duplicates

            // Build the candidate imported transaction.
            // Special handling for credit card accounts: a CR (positive amount) is never
            // INCOME — it is either a CC bill payment or a cashback/refund/reversal.
            Transaction.Type txType;
            long txAmount;
            boolean isCreditCard = account instanceof CreditCardAccount;

            if (isCreditCard && pr.amountPaise > 0) {
                if (looksLikeCCPayment(pr.description)) {
                    // Bill payment — CC_PAYMENT type; fromAccountId unknown (user can edit)
                    txType   = Transaction.Type.CC_PAYMENT;
                    txAmount = pr.amountPaise;
                } else {
                    // Cashback / refund / reversal — negative Expense offsets spending
                    txType   = Transaction.Type.EXPENSE;
                    txAmount = -pr.amountPaise;
                }
            } else {
                txType   = pr.amountPaise < 0 ? Transaction.Type.EXPENSE : Transaction.Type.INCOME;
                txAmount = Math.abs(pr.amountPaise);
            }

            Transaction imported = new Transaction(txType, pr.date, pr.description, txAmount);
            if (txType == Transaction.Type.CC_PAYMENT) {
                imported.setToAccountId(account.getId());
            } else if (txType == Transaction.Type.INCOME) {
                imported.setToAccountId(account.getId());
            } else {
                imported.setFromAccountId(account.getId());
            }
            imported.setImportHash(hash);
            imported.setSourceIndicator(SourceIndicator.IMPORTED);

            List<Transaction> matches = findManualMatches(imported, store.getTransactions(), account.getId());

            candidateHashes  .add(hash);
            candidateImported.add(imported);
            candidateMatches .add(matches);
        }

        // ── Pass 2: detect contested manuals (matched by 2+ CSV rows) ────────
        // Count how many CSV rows reference each manual transaction ID.
        Map<String, Integer> manualCsvCount = new HashMap<>();
        for (List<Transaction> matches : candidateMatches) {
            for (Transaction m : matches) {
                manualCsvCount.merge(m.getId(), 1, Integer::sum);
            }
        }
        Set<String> contestedManualIds = new HashSet<>();
        manualCsvCount.forEach((id, count) -> { if (count > 1) contestedManualIds.add(id); });

        // ── Pass 3: classify and commit ───────────────────────────────────────
        List<Transaction> toAdd = new ArrayList<>();

        for (int i = 0; i < candidateImported.size(); i++) {
            Transaction       imported = candidateImported.get(i);
            List<Transaction> matches  = candidateMatches.get(i);

            if (matches.isEmpty()) {
                store.suggestCategoryForDescription(imported.getDescription(), imported.getType())
                     .ifPresent(rule -> {
                         imported.setCategoryId(rule.getCategoryId());
                         imported.setSubCategoryId(rule.getSubCategoryId());
                     });
                toAdd.add(imported);
                result.newCount++;
            } else if (matches.size() == 1
                    && !contestedManualIds.contains(matches.get(0).getId())
                    && matches.get(0).getAmountPaise() == imported.getAmountPaise()) {
                // Clean 1:1 match with exact amount — reconcile silently
                reconcile(imported, matches.get(0), store);
                result.reconciledCount++;
            } else {
                // Multiple matches, OR single match is contested by another CSV row
                result.ambiguous.add(new AmbiguousMatch(imported, matches));
            }
        }

        for (Transaction t : toAdd) store.addTransactionInternal(t);
        if (!toAdd.isEmpty()) store.saveTransactionsNow();

        return result;
    }

    // ── Reconcile ─────────────────────────────────────────────────────────────

    /**
     * Merges an imported transaction into an existing manual one in-place.
     * Called both by executeImport (auto-match) and AmbiguousMatchDialog (user choice).
     */
    public static void reconcile(Transaction imported, Transaction manual, DataStore store) {
        manual.setDate(imported.getDate());
        manual.setAmountPaise(imported.getAmountPaise());
        manual.setImportHash(imported.getImportHash());

        // Keep bank description as a note when different from the manual description.
        // Guard against double-appending if the same bank note is already present
        // (e.g. when re-reconciling a previously RECONCILED transaction).
        if (!imported.getDescription().equalsIgnoreCase(manual.getDescription())) {
            String bankNote = "Bank: " + imported.getDescription();
            String existing = manual.getNotes();
            boolean alreadyPresent = existing != null && existing.contains(bankNote);
            if (!alreadyPresent) {
                manual.setNotes(existing == null || existing.isBlank()
                        ? bankNote : existing + " | " + bankNote);
            }
        }
        manual.setSourceIndicator(SourceIndicator.RECONCILED);
        store.saveTransactionsNow();
    }

    // ── Manual-match finder ───────────────────────────────────────────────────

    /**
     * Finds MANUAL transactions for the same account, same amount, within ±1 day
     * of the imported date.
     *
     * Only MANUAL transactions are eligible — RECONCILED transactions were already
     * matched in a previous (or current) import run and must not be re-matched.
     * Allowing RECONCILED transactions as candidates causes false ambiguous-match
     * results when multiple CSV rows share the same date and amount: the first row
     * reconciles the Expense, and subsequent rows find both the now-RECONCILED
     * Expense and the real manual candidate, producing a spurious 2-candidate result.
     * IMPORTED transactions are also excluded — they have no user-entered data to preserve.
     */
    public static List<Transaction> findManualMatches(Transaction imported,
                                                       List<Transaction> existing,
                                                       String accountId) {
        // Determine direction of the imported transaction relative to this account.
        // Debit  = money leaving  (fromAccountId = accountId) → only match manual debits.
        // Credit = money arriving (toAccountId   = accountId) → only match manual credits.
        boolean importedIsDebit = accountId.equals(imported.getFromAccountId());

        return existing.stream()
                .filter(t -> t.getSourceIndicator() == SourceIndicator.MANUAL)
                .filter(t -> importedIsDebit
                        ? accountId.equals(t.getFromAccountId())
                        : accountId.equals(t.getToAccountId()))
                .filter(t -> Math.abs(t.getAmountPaise() - imported.getAmountPaise()) < 100)
                .filter(t -> Math.abs(ChronoUnit.DAYS.between(
                                    t.getDate(), imported.getDate())) <= 1)
                .collect(Collectors.toList());
    }

    // ── Row parsing ───────────────────────────────────────────────────────────

    private static class ParsedRow {
        String    dateStr;
        LocalDate date;
        long      amountPaise;   // positive = credit/income, negative = debit/expense
        String    description;
    }

    private static ParsedRow parseRow(String[] row, Map<String, Integer> hIdx,
                                      ImportMapping m) {
        ParsedRow pr = new ParsedRow();

        // Date
        Integer dI = hIdx.get(m.getColumnDate());
        if (dI == null || dI >= row.length) return null;
        pr.dateStr = row[dI].strip();
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern(m.getDateFormat());
            pr.date = LocalDate.parse(pr.dateStr, fmt);
        } catch (DateTimeParseException e) { return null; }

        // Amount
        if (m.isAmountSplit()) {
            Integer debI = hIdx.get(m.getColumnDebit());
            Integer creI = hIdx.get(m.getColumnCredit());
            // Math.abs() ensures Dr./Cr. suffixes in split columns don't invert the sign
            long debit  = Math.abs(parseAmount(debI != null && debI < row.length  ? row[debI]  : ""));
            long credit = Math.abs(parseAmount(creI != null && creI < row.length  ? row[creI]  : ""));
            pr.amountPaise = credit - debit;   // positive = income
        } else {
            Integer aI = hIdx.get(m.getColumnAmount());
            if (aI == null || aI >= row.length) return null;
            pr.amountPaise = parseAmount(row[aI]);
        }

        // Description
        Integer deI = hIdx.get(m.getColumnDescription());
        if (deI == null || deI >= row.length) return null;
        pr.description = row[deI].strip();
        if (pr.description.isBlank()) return null;
        if (pr.description.length() > 48) pr.description = pr.description.substring(0, 48).strip();

        return pr;
    }

    /**
     * Returns true when the description looks like a CC bill payment rather than a
     * cashback/refund/reversal. Used to distinguish CR entries on a CC statement.
     *
     * Matches: PAYMENT, THANK YOU, NEFT CR, NACH CR, UPI CR, RTGS, IMPS CR.
     * Non-matches (→ cashback/refund): CASHBACK, REFUND, REVERSAL, REWARD, etc.
     */
    private static boolean looksLikeCCPayment(String description) {
        if (description == null) return false;
        String up = description.toUpperCase();
        return up.contains("PAYMENT")
            || up.contains("THANK YOU")
            || up.contains("NEFT CR")
            || up.contains("NACH CR")
            || up.contains("UPI CR")
            || up.contains("RTGS")
            || up.contains("IMPS CR");
    }

    /**
     * Parses an amount string to paise.
     * Handles:
     *   - Plain numbers: "169.00", "-35.95"
     *   - Dr./Cr. suffix (single-column bank statements): "169 Dr." → negative, "35.95 Cr." → positive
     *   - Currency symbols and commas are stripped: "₹1,234.56"
     */
    private static long parseAmount(String s) {
        if (s == null || s.isBlank()) return 0;
        String trimmed = s.strip();
        // Detect Dr. / Cr. suffix (case-insensitive, dot optional)
        int sign = 0;  // 0 = not set by suffix
        if (trimmed.toUpperCase().endsWith("DR.") || trimmed.toUpperCase().endsWith("DR")) {
            sign = -1;
            trimmed = trimmed.replaceAll("(?i)\\s*Dr\\.?$", "").strip();
        } else if (trimmed.toUpperCase().endsWith("CR.") || trimmed.toUpperCase().endsWith("CR")) {
            sign = 1;
            trimmed = trimmed.replaceAll("(?i)\\s*Cr\\.?$", "").strip();
        }
        String clean = trimmed.replaceAll("[,₹$\\s]", "").strip();
        try {
            long paise = Math.round(Double.parseDouble(clean) * 100);
            return sign != 0 ? sign * Math.abs(paise) : paise;
        } catch (NumberFormatException e) { return 0; }
    }
}
