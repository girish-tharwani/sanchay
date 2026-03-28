package com.sanchay.service;

import com.sanchay.model.*;
import com.sanchay.model.Transaction.SourceIndicator;

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
import java.util.stream.Stream;

/**
 * Stateless service: CSV parsing, column-mapping lookup, dedup hashing,
 * import execution and reconciliation logic.
 */
public class ImportService {

    // ── Public result types ───────────────────────────────────────────────────

    public static class ImportResult {
        public int newCount               = 0;
        public int reconciledCount        = 0;
        public int recurringReconciledCount = 0;
        public int skippedCount           = 0;
        public final List<AmbiguousMatch>  ambiguous        = new ArrayList<>();
        public final List<RecurringMatch>  recurringMatches = new ArrayList<>();
    }

    public static class AmbiguousMatch {
        public final Transaction       imported;
        public final List<Transaction> candidates;
        public AmbiguousMatch(Transaction imp, List<Transaction> cands) {
            this.imported   = imp;
            this.candidates = cands;
        }
    }

    public static class RecurringMatch {
        public final Transaction              imported;
        public final List<RecurringTransaction> candidates;
        public RecurringMatch(Transaction imp, List<RecurringTransaction> cands) {
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
                          || t.getSourceIndicator() == SourceIndicator.AUTO_CATEGORIZED
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
                // No individual match — try group match (e.g. REDEEM group summing to CSV amount)
                List<List<Transaction>> groupMatches = findGroupMatches(
                        imported, store.getTransactions(), account.getId());
                if (groupMatches.size() == 1) {
                    reconcileGroup(imported, groupMatches.get(0), store);
                    result.reconciledCount++;
                } else if (groupMatches.size() > 1) {
                    // Multiple groups match — ambiguous; show first member of each group
                    List<Transaction> reps = groupMatches.stream()
                            .map(g -> g.get(0)).collect(Collectors.toList());
                    result.ambiguous.add(new AmbiguousMatch(imported, reps));
                } else {
                    // For bank accounts: try type rule first, then category rule.
                    boolean typeSuggested = false;
                    if (account instanceof BankAccount) {
                        typeSuggested = store.suggestTypeForDescription(
                                imported.getDescription(), imported.getType())
                                .map(rule -> {
                                    Transaction.Type target =
                                            Transaction.Type.valueOf(rule.getTargetType());
                                    imported.setType(target);
                                    String secondId = rule.getSecondAccountId();
                                    if (secondId != null && accountExists(secondId, store)) {
                                        applySecondAccount(imported, rule.getSourceType(),
                                                target, secondId);
                                    }
                                    return true;
                                }).orElse(false);
                    }

                    boolean categorized = false;
                    if (!typeSuggested) {
                        categorized = store.suggestCategoryForDescription(
                                imported.getDescription(), imported.getType())
                                .map(rule -> {
                                    Transaction.Classification cl = new Transaction.Classification();
                                    cl.setCategoryId(rule.getCategoryId());
                                    cl.setSubCategoryId(rule.getSubCategoryId());
                                    imported.setClassification(cl);
                                    return true;
                                }).orElse(false);
                    }

                    // Check if this matches a pending recurring occurrence before adding as new.
                    List<RecurringTransaction> recurringCandidates = findRecurringMatches(
                            imported, store.getRecurring(), account.getId());
                    if (!recurringCandidates.isEmpty()) {
                        result.recurringMatches.add(new RecurringMatch(imported, recurringCandidates));
                    } else {
                        imported.setSourceIndicator(typeSuggested || categorized
                                ? SourceIndicator.AUTO_CATEGORIZED
                                : SourceIndicator.IMPORTED);
                        toAdd.add(imported);
                        result.newCount++;
                    }
                }
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

    // ── Recurring-match finder ────────────────────────────────────────────────

    /**
     * Finds ACTIVE recurring schedules for the same account and direction whose
     * next due date is within ±2 days of the imported date and whose description
     * has a token-overlap similarity ≥ 0.3 with the imported description.
     *
     * Amount check: if the recurring amount is non-zero, it must be within ±5% of
     * the imported amount. Zero-amount recurrings (e.g. CC payment reminders) skip
     * the amount check.
     */
    public static List<RecurringTransaction> findRecurringMatches(
            Transaction imported,
            List<RecurringTransaction> recurring,
            String accountId) {

        boolean importedIsDebit = accountId.equals(imported.getFromAccountId());

        return recurring.stream()
                .filter(r -> r.getStatus() == RecurringTransaction.Status.ACTIVE)
                .filter(r -> importedIsDebit
                        ? accountId.equals(r.getFromAccountId())
                        : accountId.equals(r.getToAccountId()))
                .filter(r -> {
                    LocalDate nextDue = r.getNextDueDate();
                    return nextDue != null
                            && Math.abs(ChronoUnit.DAYS.between(nextDue, imported.getDate())) <= 2;
                })
                .filter(r -> {
                    if (r.getAmountPaise() == 0) return true;  // variable amount — skip check
                    long tolerance = Math.max(1L, Math.round(imported.getAmountPaise() * 0.05));
                    return Math.abs(r.getAmountPaise() - imported.getAmountPaise()) <= tolerance;
                })
                .filter(r -> descriptionSimilarity(r.getDescription(),
                                                   imported.getDescription()) >= 0.3)
                .collect(Collectors.toList());
    }

    /**
     * Reconciles an imported transaction against a chosen recurring schedule.
     *
     * - Copies categoryId and subCategoryId from the recurring (if not already set).
     * - Marks the transaction as fromRecurring, sets recurringId.
     * - Sets sourceIndicator to RECONCILED.
     * - Adds the transaction to the store and saves transactions.
     * - Calls markRecorded() on the recurring and saves recurring.json.
     */
    public static void reconcileWithRecurring(Transaction imported,
                                              RecurringTransaction recurring,
                                              DataStore store) {
        String importedCatId = imported.getClassification() != null
                ? imported.getClassification().getCategoryId() : null;
        String importedSubCatId = imported.getClassification() != null
                ? imported.getClassification().getSubCategoryId() : null;
        if (importedCatId == null && recurring.getCategoryId() != null) {
            if (imported.getClassification() == null)
                imported.setClassification(new Transaction.Classification());
            imported.getClassification().setCategoryId(recurring.getCategoryId());
        }
        if (importedSubCatId == null && recurring.getSubCategoryId() != null) {
            if (imported.getClassification() == null)
                imported.setClassification(new Transaction.Classification());
            imported.getClassification().setSubCategoryId(recurring.getSubCategoryId());
        }

        // Copy To Account from the recurring schedule
        if (imported.getToAccountId() == null && recurring.getToAccountId() != null)
            imported.setToAccountId(recurring.getToAccountId());

        // Copy transaction type from recurring (e.g. INVESTMENT for RD)
        if (recurring.getTransactionType() != null)
            imported.setType(recurring.getTransactionType());

        // For RD schedules: copy RD Ref from the recurring schedule into the transaction notes
        String rdRef = recurring.getRdRef();
        if (rdRef != null && !rdRef.isBlank()) {
            String existing = imported.getNotes();
            String rdNote = "RD Ref: " + rdRef;
            if (existing == null || existing.isBlank())
                imported.setNotes(rdNote);
            else if (!existing.contains(rdNote))
                imported.setNotes(existing + " | " + rdNote);
        }

        imported.setRecurring(new Transaction.Recurring(recurring.getId()));
        imported.setSourceIndicator(SourceIndicator.RECONCILED);

        store.addTransactionInternal(imported);
        store.saveTransactionsNow();

        recurring.incrementPaymentsMade();
        recurring.setLastRecordedDate(imported.getDate());
        if (recurring.isPaymentLimitReached()) {
            store.deleteRecurring(recurring.getId());
        } else {
            store.saveRecurringNow();
        }
    }

    // ── Description similarity ────────────────────────────────────────────────

    /**
     * Token-overlap similarity between two description strings.
     * Normalises to lowercase, splits on non-alphanumeric runs, ignores tokens shorter
     * than 3 characters, then returns sharedTokenCount / min(|tokensA|, |tokensB|).
     */
    private static double descriptionSimilarity(String a, String b) {
        Set<String> tokA = tokenize(a);
        Set<String> tokB = tokenize(b);
        if (tokA.isEmpty() || tokB.isEmpty()) return 0.0;

        // Use the smaller set as the reference so the score is normalised correctly.
        Set<String> smaller = tokA.size() <= tokB.size() ? tokA : tokB;
        Set<String> larger  = tokA.size() <= tokB.size() ? tokB : tokA;

        long hits = smaller.stream().filter(t -> isHit(t, larger)).count();
        return (double) hits / smaller.size();
    }

    /**
     * A token counts as a hit when:
     *   1. It exactly exists in the other set, OR
     *   2. It (length ≥ 4) appears as a substring inside any token of the other set, OR
     *   3. Any token of the other set (length ≥ 4) appears as a substring inside it.
     * The length-4 guard prevents short tokens like "emi" from spuriously matching
     * inside unrelated longer words (e.g. "pr-emi-um").
     */
    private static boolean isHit(String t, Set<String> others) {
        for (String o : others) {
            if (t.equals(o)) return true;
            if (t.length() >= 4 && o.contains(t)) return true;
            if (o.length() >= 4 && t.contains(o)) return true;
        }
        return false;
    }

    private static Set<String> tokenize(String s) {
        if (s == null || s.isBlank()) return Collections.emptySet();
        return Stream.of(s.toLowerCase().split("[^a-z0-9]+"))
                .filter(t -> t.length() >= 3)
                .collect(Collectors.toSet());
    }

    // ── Manual-match finder ───────────────────────────────────────────────────

    /**
     * Finds unreconciled transactions for the same account, same amount, within ±1 day
     * of the imported date.
     *
     * MANUAL, IMPORTED, and AUTO_CATEGORIZED transactions are all eligible candidates.
     * IMPORTED/AUTO_CATEGORIZED are included so that a transaction imported from one
     * account's CSV (e.g. a bank EXPENSE later edited to CC_PAYMENT) can be reconciled
     * when the other account's CSV (e.g. the CC statement) is imported.  The account-
     * direction filter below prevents cross-account false positives: a bank-side entry
     * (fromAccountId=bankId) will never be selected as a candidate for a CC-side credit
     * lookup (toAccountId=ccId).
     *
     * RECONCILED transactions are excluded — they were already matched in a prior import
     * run and must not be re-matched (allowing them produces spurious ambiguous-match
     * results when multiple CSV rows share the same date and amount).
     */
    public static List<Transaction> findManualMatches(Transaction imported,
                                                       List<Transaction> existing,
                                                       String accountId) {
        // Determine direction of the imported transaction relative to this account.
        // Debit  = money leaving  (fromAccountId = accountId) → only match debits.
        // Credit = money arriving (toAccountId   = accountId) → only match credits.
        boolean importedIsDebit = accountId.equals(imported.getFromAccountId());

        return existing.stream()
                .filter(t -> t.getSourceIndicator() == SourceIndicator.MANUAL
                          || t.getSourceIndicator() == SourceIndicator.IMPORTED
                          || t.getSourceIndicator() == SourceIndicator.AUTO_CATEGORIZED)
                .filter(t -> importedIsDebit
                        ? accountId.equals(t.getFromAccountId())
                        : accountId.equals(t.getToAccountId()))
                .filter(t -> Math.abs(t.getAmountPaise() - imported.getAmountPaise()) < 100)
                .filter(t -> Math.abs(ChronoUnit.DAYS.between(
                                    t.getDate(), imported.getDate())) <= 1)
                .collect(Collectors.toList());
    }

    /**
     * Finds MANUAL transaction groups for this account whose combined amount for this
     * account equals the imported amount (within ±1 paise) and whose date is within ±1 day.
     * Used to reconcile a single bank CSV row against a REDEEM group
     * (e.g. a +₹2100 entry matching a REDEEM ₹2000 + GAIN ₹100 group).
     */
    public static List<List<Transaction>> findGroupMatches(Transaction imported,
                                                           List<Transaction> existing,
                                                           String accountId) {
        boolean importedIsCredit = accountId.equals(imported.getToAccountId());

        // Collect MANUAL transactions for this account that have a groupTransactionId
        Map<String, List<Transaction>> byGroup = new LinkedHashMap<>();
        for (Transaction t : existing) {
            if (t.getSourceIndicator() != SourceIndicator.MANUAL) continue;
            if (t.getGroupTransactionId() == null) continue;
            boolean forAccount = accountId.equals(t.getFromAccountId())
                              || accountId.equals(t.getToAccountId());
            if (!forAccount) continue;
            byGroup.computeIfAbsent(t.getGroupTransactionId(), k -> new ArrayList<>()).add(t);
        }

        List<List<Transaction>> result = new ArrayList<>();
        for (List<Transaction> group : byGroup.values()) {
            // Sum only the legs that belong to this account in the correct direction
            long sum = 0;
            for (Transaction t : group) {
                if (importedIsCredit && accountId.equals(t.getToAccountId()))
                    sum += t.getAmountPaise();
                else if (!importedIsCredit && accountId.equals(t.getFromAccountId()))
                    sum += t.getAmountPaise();
            }
            if (Math.abs(sum - imported.getAmountPaise()) >= 100) continue;
            // All members share the same date; check against first member
            long dateDiff = Math.abs(ChronoUnit.DAYS.between(group.get(0).getDate(), imported.getDate()));
            if (dateDiff > 1) continue;
            result.add(group);
        }
        return result;
    }

    /**
     * Reconciles a single imported CSV row against a group of linked manual transactions.
     * Marks all group members as RECONCILED and sets the importHash on each.
     * The bank description is appended to notes on the first group member only.
     */
    public static void reconcileGroup(Transaction imported, List<Transaction> group,
                                      DataStore store) {
        boolean first = true;
        for (Transaction manual : group) {
            manual.setImportHash(imported.getImportHash());
            if (first && !imported.getDescription().equalsIgnoreCase(manual.getDescription())) {
                String bankNote = "Bank: " + imported.getDescription();
                String existing = manual.getNotes();
                if (existing == null || !existing.contains(bankNote))
                    manual.setNotes(existing == null || existing.isBlank()
                            ? bankNote : existing + " | " + bankNote);
                first = false;
            }
            manual.setSourceIndicator(SourceIndicator.RECONCILED);
        }
        store.saveTransactionsNow();
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

    /** Returns true if an account with the given id currently exists in the store. */
    private static boolean accountExists(String id, DataStore store) {
        return store.getAccounts().stream().anyMatch(a -> a.getId().equals(id));
    }

    /**
     * Sets the second (non-bank) account on a transaction being reclassified by a type rule.
     * For INCOME→TRANSFER the secondAccount is fromAccountId (the sending bank).
     * For all EXPENSE-source types it is toAccountId.
     */
    private static void applySecondAccount(Transaction t, String sourceType,
                                            Transaction.Type targetType, String secondAccountId) {
        if ("INCOME".equals(sourceType) && targetType == Transaction.Type.TRANSFER) {
            t.setFromAccountId(secondAccountId);
        } else {
            t.setToAccountId(secondAccountId);
        }
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
