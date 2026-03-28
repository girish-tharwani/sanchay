package com.sanchay.service;

import com.sanchay.model.*;
import com.sanchay.model.ImportMapping;
import com.sanchay.model.BankAccount.SubType;
import com.sanchay.model.InvestmentAccount.InvestmentType;
import com.sanchay.model.LoanAccount.LoanType;
import com.sanchay.model.RecurringTransaction.Frequency;
import com.sanchay.model.Transaction.Type;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** Central in-memory data store (singleton). */
public class DataStore {

    private static DataStore instance;

    private final List<Account>              accounts       = new ArrayList<>();
    private final List<Transaction>          transactions   = new ArrayList<>();
    private final List<RecurringTransaction> recurring      = new ArrayList<>();
    private final List<Category>             categories     = new ArrayList<>();
    private final List<FamilyMember>         familyMembers  = new ArrayList<>();
    private final List<ImportMapping>        importMappings = new ArrayList<>();
    private final List<CategoryRule>         categoryRules  = new ArrayList<>();
    private final List<TypeRule>             typeRules      = new ArrayList<>();
    private final Map<String, List<AmortizationEntry>> loanSchedules = new HashMap<>();

    private String activeFinancialYear = "FY 2025-26";
    private String dateFormat = "DD/MM/YYYY";
    private String currency   = "INR";
    private String yearFormat = "Indian Financial Year";
    private final Map<String, Boolean> groupCollapsed = new HashMap<>(
            Map.of("bank", true, "cc", true, "loan", true, "investment", true));

    /** Set by MainApp after the data folder is confirmed. May be null during wizard. */
    private PersistenceService persistence;
    /** Stored independently so SettingsScreen can display it without calling PersistenceService. */
    private String dataFolderPath = "Not configured";

    private DataStore() {}

    public static DataStore getInstance() {
        if (instance == null) instance = new DataStore();
        return instance;
    }

    /**
     * Clears all in-memory data and resets settings to defaults.
     * Call before wiring a new PersistenceService and calling loadAll().
     */
    public void reset() {
        accounts.clear();
        transactions.clear();
        recurring.clear();
        categories.clear();
        familyMembers.clear();
        importMappings.clear();
        categoryRules.clear();
        typeRules.clear();
        loanSchedules.clear();
        activeFinancialYear = "FY 2025-26";
        dateFormat  = "DD/MM/YYYY";
        currency    = "INR";
        yearFormat  = "Indian Financial Year";
        groupCollapsed.replaceAll((k, v) -> true);
        persistence    = null;
        dataFolderPath = "Not configured";
    }

    /** Called by MainApp once the data folder path is known. */
    public void setPersistence(PersistenceService ps) { this.persistence = ps; }

    /**
     * Called by MainApp after setPersistence() so SettingsScreen can display the path
     * without any dependency on PersistenceService.getDataFolderPath().
     */
    public void setDataFolderPath(String path) {
        this.dataFolderPath = (path != null) ? path : "Not configured";
    }

    public PersistenceService getPersistence() { return persistence; }

    /** Returns the data folder path for display in Settings. */
    public String getDataFolderPath() { return dataFolderPath; }

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<Account>              getAccounts()            { return Collections.unmodifiableList(accounts); }
    public List<Transaction>          getTransactions()        { return Collections.unmodifiableList(transactions); }
    public List<RecurringTransaction> getRecurring()           { return Collections.unmodifiableList(recurring); }

    /** Distinct non-blank transaction descriptions, sorted alphabetically. */
    public List<String> getDistinctTransactionDescriptions() {
        return transactions.stream()
                .map(Transaction::getDescription)
                .filter(d -> d != null && !d.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    /**
     * Returns distinct RD reference numbers from recurring schedules whose
     * toAccountId matches the given account, sorted alphabetically.
     */
    public List<String> getRdRefsForAccount(String accountId) {
        return recurring.stream()
                .filter(r -> accountId.equals(r.getToAccountId()))
                .map(RecurringTransaction::getRdRef)
                .filter(ref -> ref != null && !ref.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /** Distinct non-blank recurring schedule descriptions, sorted alphabetically. */
    public List<String> getDistinctScheduleDescriptions() {
        return recurring.stream()
                .map(RecurringTransaction::getDescription)
                .filter(d -> d != null && !d.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }
    public List<Category>             getCategories()          { return Collections.unmodifiableList(categories); }
    public List<FamilyMember>         getFamilyMembers()       { return Collections.unmodifiableList(familyMembers); }
    public List<ImportMapping>        getImportMappings()      { return Collections.unmodifiableList(importMappings); }
    public List<CategoryRule>         getCategoryRules()       { return Collections.unmodifiableList(categoryRules); }
    public List<TypeRule>             getTypeRules()           { return Collections.unmodifiableList(typeRules); }

    // ── Loan schedule accessors ───────────────────────────────────────────────

    public Map<String, List<AmortizationEntry>> getLoanSchedules() { return loanSchedules; }

    public List<AmortizationEntry> getSchedule(String loanAccountId) {
        return loanSchedules.getOrDefault(loanAccountId, Collections.emptyList());
    }

    public void saveSchedule(String loanAccountId, List<AmortizationEntry> entries) {
        loanSchedules.put(loanAccountId, entries);
        if (persistence != null) persistence.saveLoanSchedules(this);
    }

    /** For internal load use only — does not trigger save. */
    public void putScheduleInternal(String loanAccountId, List<AmortizationEntry> entries) {
        loanSchedules.put(loanAccountId, entries);
    }

    public void deleteSchedule(String loanAccountId) {
        loanSchedules.remove(loanAccountId);
        if (persistence != null) persistence.saveLoanSchedules(this);
    }
    public String                     getActiveFinancialYear() { return activeFinancialYear; }

    /** Returns the user-selected date format string: "DD/MM/YYYY" or "YYYY-MM-DD". */
    public String getDateFormat() { return dateFormat; }

    /**
     * Returns a DateTimeFormatter matching the user's date format preference.
     * Use this everywhere a date is displayed to the user.
     */
    public DateTimeFormatter getDateFormatter() {
        return "YYYY-MM-DD".equals(dateFormat)
                ? DateTimeFormatter.ofPattern("yyyy-MM-dd")
                : DateTimeFormatter.ofPattern("dd/MM/yyyy");
    }

    public void setDateFormat(String fmt) {
        this.dateFormat = fmt;
        if (persistence != null) persistence.saveSettings(this);
    }

    /** For internal load use only — does not trigger save. */
    public void setDateFormatInternal(String fmt) { this.dateFormat = fmt; }

    public String getCurrency() { return currency; }
    public void setCurrency(String c) { this.currency = c; if (persistence != null) persistence.saveSettings(this); }
    public void setCurrencyInternal(String c) { this.currency = c; }

    public String getYearFormat() { return yearFormat; }
    public void setYearFormat(String yf) { this.yearFormat = yf; if (persistence != null) persistence.saveSettings(this); }
    public void setYearFormatInternal(String yf) { this.yearFormat = yf; }

    // ── Account group collapse state ──────────────────────────────────────────

    public boolean isGroupCollapsed(String type) {
        return groupCollapsed.getOrDefault(type, true);
    }

    /** For internal load use only — does not trigger save. */
    public void setGroupCollapsedInternal(String type, boolean val) {
        groupCollapsed.put(type, val);
    }

    public void setGroupCollapsed(String type, boolean val) {
        groupCollapsed.put(type, val);
        if (persistence != null) persistence.saveSettings(this);
    }

    // ── Financial Year helpers ────────────────────────────────────────────────

    /**
     * Returns the start date (1 April) of the active financial year.
     * e.g. "FY 2025-26" → 2025-04-01
     */
    public LocalDate getActiveFYStart() {
        int startYear = parseFYStartYear(activeFinancialYear);
        return LocalDate.of(startYear, 4, 1);
    }

    /**
     * Returns the end date (31 March) of the active financial year.
     * e.g. "FY 2025-26" → 2026-03-31
     */
    public LocalDate getActiveFYEnd() {
        int startYear = parseFYStartYear(activeFinancialYear);
        return LocalDate.of(startYear + 1, 3, 31);
    }

    private int parseFYStartYear(String fy) {
        // Format: "FY YYYY-YY" e.g. "FY 2025-26"
        try {
            String[] parts = fy.replace("FY ", "").split("-");
            return Integer.parseInt(parts[0]);
        } catch (Exception e) {
            return LocalDate.now().getYear();
        }
    }

    // ── FY persistence ────────────────────────────────────────────────────────

    public void setActiveFinancialYear(String y) {
        this.activeFinancialYear = y;
        if (persistence != null) persistence.saveSettings(this);
    }

    /** For internal load use only — does not trigger save. */
    public void setActiveFinancialYearInternal(String y) { this.activeFinancialYear = y; }

    // ── Mutations ─────────────────────────────────────────────────────────────

    public void addAccount(Account a) {
        accounts.add(a);
        if (a instanceof LoanAccount la) {
            List<AmortizationEntry> schedule = AmortizationService.generateSchedule(la);
            loanSchedules.put(la.getId(), schedule);
        }
        if (persistence != null) {
            persistence.saveAccounts(this);
            if (a instanceof LoanAccount) persistence.saveLoanSchedules(this);
        }
    }

    /** For internal load use only — does not trigger save. */
    public void addAccountInternal(Account a) { accounts.add(a); }

    public void addTransaction(Transaction t) {
        transactions.add(t);
        if (persistence != null) persistence.saveTransactions(this);
    }

    /** For internal load use only — does not trigger save. */
    public void addTransactionInternal(Transaction t) { transactions.add(t); }

    public void addRecurring(RecurringTransaction r) {
        recurring.add(r);
        if (persistence != null) persistence.saveRecurring(this);
    }

    /** For internal load use only — does not trigger save. */
    public void addRecurringInternal(RecurringTransaction r) { recurring.add(r); }

    public void deleteTransaction(String id) {
        Transaction target = transactions.stream()
                .filter(t -> t.getId().equals(id)).findFirst().orElse(null);
        if (target != null && target.getGroupTransactionId() != null) {
            String groupId = target.getGroupTransactionId();
            transactions.removeIf(t -> groupId.equals(t.getGroupTransactionId()));
        } else {
            transactions.removeIf(t -> t.getId().equals(id));
        }
        if (persistence != null) persistence.saveTransactions(this);
    }

    /** Deletes all transactions in a group without saving (caller must save). */
    public void deleteTransactionGroupInternal(String groupId) {
        transactions.removeIf(t -> groupId.equals(t.getGroupTransactionId()));
    }

    /** Deletes a single transaction by id without saving (caller must save). */
    public void deleteTransactionByIdInternal(String id) {
        transactions.removeIf(t -> t.getId().equals(id));
    }

    /**
     * Replaces the transaction with the given oldId in-place (preserving its list position),
     * then saves. Used by TransactionDialog when editing, so the table sort order is stable.
     */
    public void updateTransactionInPlace(String oldId, Transaction newT) {
        int idx = -1;
        for (int i = 0; i < transactions.size(); i++) {
            if (transactions.get(i).getId().equals(oldId)) { idx = i; break; }
        }
        if (idx >= 0) {
            transactions.set(idx, newT);
        } else {
            transactions.add(newT);
        }
        if (persistence != null) persistence.saveTransactions(this);
    }

    public void addCategory(Category c) {
        categories.add(c);
        if (persistence != null) persistence.saveCategories(this);
    }

    /** For internal load use only — does not trigger save. */
    public void addCategoryInternal(Category c) { categories.add(c); }

    public boolean deleteCategory(String categoryId) {
        if (getCategoryUsageCount(categoryId) > 0) return false;
        categories.removeIf(c -> c.getId().equals(categoryId));
        if (persistence != null) persistence.saveCategories(this);
        return true;
    }

    /**
     * Moves a sub-category to a new parent.
     * Updates the category's parentId and also updates the categoryId on every
     * transaction that references this sub-category, keeping the data consistent.
     */
    public void moveSubCategoryParent(String subId, String newParentId) {
        categories.stream()
                .filter(c -> c.getId().equals(subId))
                .findFirst()
                .ifPresent(c -> c.setParentId(newParentId));
        if (persistence != null) persistence.saveCategories(this);
        boolean changed = false;
        for (Transaction t : transactions) {
            if (subId.equals(t.getSubCategoryId())) {
                t.setCategoryId(newParentId);
                changed = true;
            }
        }
        if (changed) saveTransactionsNow();
    }

    /**
     * Bulk-reassigns all transactions that reference fromCategoryId (in either
     * categoryId or subCategoryId) to the given target category and sub-category.
     * After this call, getCategoryUsageCount(fromCategoryId) returns 0,
     * so deleteCategory() will succeed.
     */
    public void reassignCategory(String fromCategoryId, String toCategoryId, String toSubCategoryId) {
        boolean changed = false;
        for (Transaction t : transactions) {
            if (fromCategoryId.equals(t.getCategoryId())
                    || fromCategoryId.equals(t.getSubCategoryId())) {
                t.setCategoryId(toCategoryId);
                t.setSubCategoryId(toSubCategoryId);
                changed = true;
            }
        }
        if (changed) saveTransactionsNow();
    }

    /** Saves or replaces the import mapping for the given account (one per account). */
    public void saveOrUpdateImportMapping(ImportMapping mapping) {
        importMappings.removeIf(m -> mapping.getAccountId().equals(m.getAccountId()));
        importMappings.add(mapping);
        if (persistence != null) persistence.saveImportMappings(this);
    }

    /** For internal load use only — does not trigger save. */
    public void addImportMappingInternal(ImportMapping m) { importMappings.add(m); }

    // ── RuleLearner — candidate for extraction to service/RuleLearner.java ───────────────────────
    // ── Category rules (learn + suggest) ─────────────────────────────────────

    /** For internal load use only — does not trigger save. */
    public void addCategoryRuleInternal(CategoryRule r) { categoryRules.add(r); }

    // ── Type rules (learn + suggest) ──────────────────────────────────────────

    /** For internal load use only — does not trigger save. */
    public void addTypeRuleInternal(TypeRule r) { typeRules.add(r); }

    /**
     * Called after a transaction is saved by the user to update the learned rules.
     * Returns true if a normalised key is worth storing as a category rule.
     * Rejects keys that: are blank/too short; contain no word long enough to
     * participate in the fuzzy matcher (which requires ≥ 5-char words); or are
     * more than 40% digit characters (exchange rates, reference numbers, amount
     * breakdowns — these change every transaction and will never match again).
     */
    private static boolean isUsableRuleKey(String key) {
        if (key.isBlank() || key.length() < 4) return false;
        boolean hasMatchableWord = Arrays.stream(key.split(" "))
                .anyMatch(w -> w.length() >= 5);
        if (!hasMatchableWord) return false;
        long nonSpace = key.chars().filter(c -> c != ' ').count();
        long digits   = key.chars().filter(Character::isDigit).count();
        return nonSpace == 0 || (digits * 100 / nonSpace) <= 40;
    }

    /**
     * Finds an existing rule matching (normalizedKey, type, categoryId, subCategoryId)
     * and increments its count, or creates a new rule entry.
     */
    public void learnFromTransaction(Transaction t) {
        if (t.getCategoryId() == null || t.getDescription() == null) return;
        String key     = normalizeDesc(t.getDescription());
        if (!isUsableRuleKey(key)) return;
        String typeStr = t.getType().name();
        categoryRules.stream()
                .filter(r -> r.getNormalizedKey().equals(key)
                          && r.getTransactionType().equals(typeStr)
                          && r.getCategoryId().equals(t.getCategoryId())
                          && Objects.equals(r.getSubCategoryId(), t.getSubCategoryId()))
                .findFirst()
                .ifPresentOrElse(
                        CategoryRule::incrementCount,
                        () -> categoryRules.add(new CategoryRule(
                                key, typeStr, t.getCategoryId(), t.getSubCategoryId())));
        if (persistence != null) persistence.saveCategoryRules(this);
        reapplyRulesToImported();
    }

    /**
     * Returns all rules whose normalizedKey fuzzy-matches the given description
     * and whose transaction type matches.
     *
     * EXPENSE and TRANSFER are treated as interchangeable: imported bank
     * withdrawals always arrive as EXPENSE, but users frequently reclassify some
     * (e.g. loan EMIs, rent) as TRANSFER. Rules saved under either type should
     * surface for both, so the category suggestion is still offered.
     */
    public List<CategoryRule> getCategoryRulesFor(String description, Transaction.Type type) {
        if (description == null || description.isBlank()) return Collections.emptyList();
        String needle  = normalizeDesc(description);
        String typeStr = type.name();
        boolean expenseOrTransfer = type == Transaction.Type.EXPENSE
                                 || type == Transaction.Type.TRANSFER;
        return categoryRules.stream()
                .filter(r -> {
                    boolean typeMatches = r.getTransactionType().equals(typeStr)
                            || (expenseOrTransfer
                                && (r.getTransactionType().equals("EXPENSE")
                                    || r.getTransactionType().equals("TRANSFER")));
                    return typeMatches && DescriptionNormalizer.matches(needle, r.getNormalizedKey());
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns the best category suggestion for a description + type, or empty if:
     * – no matching rules exist, or
     * – matching rules disagree on the categoryId (ambiguous, e.g. Amazon).
     */
    public Optional<CategoryRule> suggestCategoryForDescription(String description, Transaction.Type type) {
        List<CategoryRule> matching = getCategoryRulesFor(description, type);
        if (matching.isEmpty()) return Optional.empty();
        // Exact key match always wins — don't let fuzzy co-matches from other rules
        // trigger the ambiguity guard.
        String needle = normalizeDesc(description);
        Optional<CategoryRule> exact = matching.stream()
                .filter(r -> r.getNormalizedKey().equals(needle))
                .max(Comparator.comparingInt(CategoryRule::getCount));
        if (exact.isPresent()) return exact;
        long distinctCats = matching.stream().map(CategoryRule::getCategoryId).distinct().count();
        if (distinctCats != 1) return Optional.empty();   // ambiguous
        return matching.stream().max(Comparator.comparingInt(CategoryRule::getCount));
    }

    /**
     * Learns a type rule when the user edits an imported EXPENSE or INCOME transaction
     * and changes its type to something more specific (e.g. CC_PAYMENT, LOAN_PAYMENT).
     * Only valid source→target combinations are stored (see plan).
     * After learning, triggers a retroactive pass over unreviewed imports.
     */
    public void learnTypeRule(Transaction.Type originalType, Transaction saved) {
        if (saved.getDescription() == null) return;
        String key = normalizeDesc(saved.getDescription());
        if (!isUsableRuleKey(key)) return;

        Transaction.Type newType = saved.getType();
        boolean validChange =
                (originalType == Transaction.Type.EXPENSE
                        && (newType == Transaction.Type.CC_PAYMENT
                         || newType == Transaction.Type.LOAN_PAYMENT
                         || newType == Transaction.Type.INVESTMENT
                         || newType == Transaction.Type.TRANSFER))
             || (originalType == Transaction.Type.INCOME
                        && (newType == Transaction.Type.REFUND
                         || newType == Transaction.Type.TRANSFER));
        if (!validChange) return;

        // secondAccountId: for INCOME→TRANSFER the other bank is fromAccountId;
        // for all EXPENSE-source types it is toAccountId;
        // for INCOME→REFUND no second account is needed.
        String secondAccountId = null;
        if (originalType == Transaction.Type.INCOME && newType == Transaction.Type.TRANSFER) {
            secondAccountId = saved.getFromAccountId();
        } else if (newType != Transaction.Type.REFUND) {
            secondAccountId = saved.getToAccountId();
        }

        final String srcType = originalType.name();
        final String tgtType = newType.name();
        final String secAcct = secondAccountId;
        typeRules.stream()
                .filter(r -> r.getNormalizedKey().equals(key)
                          && r.getSourceType().equals(srcType)
                          && r.getTargetType().equals(tgtType)
                          && Objects.equals(r.getSecondAccountId(), secAcct))
                .findFirst()
                .ifPresentOrElse(
                        TypeRule::incrementCount,
                        () -> typeRules.add(new TypeRule(key, srcType, tgtType, secAcct)));
        if (persistence != null) persistence.saveTypeRules(this);
        reapplyRulesToImported();
    }

    /**
     * Returns the best type rule for a description + imported type, or empty if:
     * – no matching rules exist, or
     * – matching rules disagree on targetType (ambiguous).
     */
    public Optional<TypeRule> suggestTypeForDescription(String description,
                                                         Transaction.Type importedType) {
        if (description == null || description.isBlank()) return Optional.empty();
        String needle  = normalizeDesc(description);
        String srcType = importedType.name();
        List<TypeRule> matching = typeRules.stream()
                .filter(r -> r.getSourceType().equals(srcType)
                          && DescriptionNormalizer.matches(needle, r.getNormalizedKey()))
                .collect(Collectors.toList());
        if (matching.isEmpty()) return Optional.empty();
        long distinctTargets = matching.stream().map(TypeRule::getTargetType).distinct().count();
        if (distinctTargets != 1) return Optional.empty();   // ambiguous
        return matching.stream().max(Comparator.comparingInt(TypeRule::getCount));
    }

    /**
     * Retroactively applies type rules and category rules to all IMPORTED bank-account
     * transactions that are still typed as EXPENSE or INCOME (raw, unreviewed imports).
     * Also applies category rules to IMPORTED transactions that have no category yet.
     *
     * Called whenever a new rule is learned so that existing imports benefit immediately.
     */
    public void reapplyRulesToImported() {
        Set<String> bankIds = getBankAccounts().stream()
                .map(Account::getId).collect(Collectors.toSet());
        boolean changed = false;

        for (Transaction t : transactions) {
            if (t.getSourceIndicator() != Transaction.SourceIndicator.IMPORTED) continue;

            boolean isBankDebitOrCredit =
                    (t.getType() == Transaction.Type.EXPENSE || t.getType() == Transaction.Type.INCOME)
                    && (bankIds.contains(t.getFromAccountId()) || bankIds.contains(t.getToAccountId()));

            boolean wasChanged = false;

            // Type rule — bank EXPENSE/INCOME only
            if (isBankDebitOrCredit) {
                Optional<TypeRule> typeRule = suggestTypeForDescription(t.getDescription(), t.getType());
                if (typeRule.isPresent()) {
                    TypeRule rule = typeRule.get();
                    Transaction.Type target = Transaction.Type.valueOf(rule.getTargetType());
                    t.setType(target);
                    if (rule.getSecondAccountId() != null && accountExists(rule.getSecondAccountId())) {
                        applySecondAccount(t, rule.getSourceType(), target, rule.getSecondAccountId());
                    }
                    t.setSourceIndicator(Transaction.SourceIndicator.AUTO_CATEGORIZED);
                    wasChanged = true;
                }
            }

            // Category rule — only if no type rule applied and no category yet
            if (!wasChanged && t.getCategoryId() == null) {
                Optional<CategoryRule> catRule = suggestCategoryForDescription(t.getDescription(), t.getType());
                if (catRule.isPresent()) {
                    t.setCategoryId(catRule.get().getCategoryId());
                    t.setSubCategoryId(catRule.get().getSubCategoryId());
                    t.setSourceIndicator(Transaction.SourceIndicator.AUTO_CATEGORIZED);
                    wasChanged = true;
                }
            }

            if (wasChanged) changed = true;
        }

        if (changed && persistence != null) persistence.saveTransactions(this);
    }

    private boolean accountExists(String id) {
        return accounts.stream().anyMatch(a -> a.getId().equals(id));
    }

    /**
     * Sets the second (non-bank) account on a transaction being reclassified by a type rule.
     * For INCOME→TRANSFER the secondAccount is the fromAccountId (the sending bank).
     * For all EXPENSE-source types it is the toAccountId.
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
     * Returns a copy of the given category list sorted so that categories
     * most frequently used for this description appear first, then alphabetically.
     * Returns the original list unchanged if no rules exist for the description.
     */
    public List<Category> sortCategoriesByUsage(List<Category> all,
                                                 String description, Transaction.Type type) {
        List<CategoryRule> matching = getCategoryRulesFor(description, type);
        if (matching.isEmpty()) return all;
        Map<String, Integer> catCount = new HashMap<>();
        for (CategoryRule r : matching)
            catCount.merge(r.getCategoryId(), r.getCount(), Integer::sum);
        List<Category> sorted = new ArrayList<>(all);
        sorted.sort((a, b) -> {
            int ca = catCount.getOrDefault(a.getId(), 0);
            int cb = catCount.getOrDefault(b.getId(), 0);
            if (ca != cb) return Integer.compare(cb, ca);
            return a.getName().compareTo(b.getName());
        });
        return sorted;
    }

    // ── Description normalisation (delegates to DescriptionNormalizer) ────────

    /** Delegates to {@link DescriptionNormalizer#normalize(String)}. */
    public static String normalizeDesc(String raw) {
        return DescriptionNormalizer.normalize(raw);
    }

    // ── Convenience in-place save triggers ───────────────────────────────────

    public void saveCategoriesNow() {
        if (persistence != null) persistence.saveCategories(this);
    }

    public void saveTransactionsNow() {
        if (persistence != null) persistence.saveTransactions(this);
    }

    public void saveRecurringNow() {
        if (persistence != null) persistence.saveRecurring(this);
    }

    public RecurringTransaction findRecurringById(String id) {
        if (id == null) return null;
        return recurring.stream().filter(r -> id.equals(r.getId())).findFirst().orElse(null);
    }

    public void deleteRecurring(String id) {
        recurring.removeIf(r -> id.equals(r.getId()));
        if (persistence != null) persistence.saveRecurring(this);
    }

    // ── Family Members ────────────────────────────────────────────────────────

    /**
     * Adds a new family member. Returns false if name is blank or already exists
     * (case-insensitive).
     */
    public boolean addFamilyMember(FamilyMember m) {
        if (m == null || m.getName() == null || m.getName().isBlank()) return false;
        if (familyMembers.stream()
                .anyMatch(x -> x.getName().equalsIgnoreCase(m.getName().trim()))) return false;
        familyMembers.add(m);
        if (persistence != null) persistence.saveMembers(this);
        return true;
    }

    /** For internal load use only — does not trigger save. */
    public void addFamilyMemberInternal(FamilyMember m) {
        if (m != null) familyMembers.add(m);
    }

    /**
     * Updates an existing member in-place (matched by id). Returns false if the
     * new name is blank or already taken by a different member.
     */
    public boolean updateFamilyMember(FamilyMember updated) {
        if (updated == null || updated.getName() == null || updated.getName().isBlank())
            return false;
        String trimmed = updated.getName().trim();
        boolean nameTaken = familyMembers.stream()
                .anyMatch(x -> !x.getId().equals(updated.getId())
                        && x.getName().equalsIgnoreCase(trimmed));
        if (nameTaken) return false;
        for (int i = 0; i < familyMembers.size(); i++) {
            if (familyMembers.get(i).getId().equals(updated.getId())) {
                familyMembers.set(i, updated);
                if (persistence != null) persistence.saveMembers(this);
                return true;
            }
        }
        return false;
    }

    public void removeFamilyMember(String memberId) {
        familyMembers.removeIf(m -> m.getId().equals(memberId));
        if (persistence != null) persistence.saveMembers(this);
    }

    // ── Lookup helpers ────────────────────────────────────────────────────────

    public String getCategoryName(String id) {
        if (id == null) return "";
        return categories.stream().filter(c -> c.getId().equals(id))
                .map(Category::getName).findFirst().orElse("");
    }

    public String getAccountName(String id) {
        if (id == null) return "—";
        return accounts.stream().filter(a -> a.getId().equals(id))
                .map(Account::getName).findFirst().orElse("—");
    }

    // ── Filtered account lists ────────────────────────────────────────────────

    public List<BankAccount> getBankAccounts() {
        return accounts.stream()
                .filter(a -> a instanceof BankAccount && a.isActive())
                .map(a -> (BankAccount) a).collect(Collectors.toList());
    }

    public List<CreditCardAccount> getCreditCardAccounts() {
        return accounts.stream()
                .filter(a -> a instanceof CreditCardAccount && a.isActive())
                .map(a -> (CreditCardAccount) a).collect(Collectors.toList());
    }

    public List<LoanAccount> getActiveLoanAccounts() {
        return accounts.stream()
                .filter(a -> a instanceof LoanAccount
                        && ((LoanAccount) a).getLoanStatus() == LoanAccount.LoanStatus.ACTIVE
                        && a.isActive())
                .map(a -> (LoanAccount) a).collect(Collectors.toList());
    }

    public List<BankAccount> getAllBankAccounts() {
        return accounts.stream()
                .filter(a -> a instanceof BankAccount)
                .map(a -> (BankAccount) a).collect(Collectors.toList());
    }

    public List<CreditCardAccount> getAllCreditCardAccounts() {
        return accounts.stream()
                .filter(a -> a instanceof CreditCardAccount)
                .map(a -> (CreditCardAccount) a).collect(Collectors.toList());
    }

    public List<LoanAccount> getAllLoanAccounts() {
        return accounts.stream()
                .filter(a -> a instanceof LoanAccount)
                .map(a -> (LoanAccount) a).collect(Collectors.toList());
    }

    public List<InvestmentAccount> getAllInvestmentAccounts() {
        return accounts.stream()
                .filter(a -> a instanceof InvestmentAccount)
                .map(a -> (InvestmentAccount) a).collect(Collectors.toList());
    }

    public List<InvestmentAccount> getInvestmentAccounts() {
        return accounts.stream()
                .filter(a -> a instanceof InvestmentAccount && a.isActive())
                .map(a -> (InvestmentAccount) a).collect(Collectors.toList());
    }

    public List<Category> getExpenseCategories() {
        return categories.stream()
                .filter(c -> c.getType() == Category.CategoryType.EXPENSE
                        && c.isActive() && c.getParentId() == null)
                .collect(Collectors.toList());
    }

    public List<Category> getIncomeCategories() {
        return categories.stream()
                .filter(c -> c.getType() == Category.CategoryType.INCOME
                        && c.isActive() && c.getParentId() == null)
                .collect(Collectors.toList());
    }

    /** Returns active sub-categories for a given parent category ID. */
    public List<Category> getSubCategories(String parentId) {
        if (parentId == null) return Collections.emptyList();
        return categories.stream()
                .filter(c -> parentId.equals(c.getParentId()) && c.isActive())
                .collect(Collectors.toList());
    }

    // ── RecurringScheduler — candidate for extraction to service/RecurringScheduler.java ─────────
    public List<RecurringTransaction> getPendingRecurring() {
        return recurring.stream()
                .filter(RecurringTransaction::hasPendingOccurrence)
                .collect(Collectors.toList());
    }

    /**
     * Called at app startup. For any active schedule with autoRecordAfterDays > 0
     * and a fixed amount, silently posts transactions for all occurrences that are
     * overdue by at least that many days, advancing lastRecordedDate each time.
     */
    public void autoRecordPending() {
        LocalDate today = LocalDate.now();
        boolean anyRecorded = false;
        for (RecurringTransaction r : new ArrayList<>(recurring)) {
            if (r.getStatus() != RecurringTransaction.Status.ACTIVE) continue;
            if (r.getAutoRecordAfterDays() <= 0 || r.getAmountPaise() <= 0) continue;
            LocalDate nextDue = r.getNextDueDate();
            while (nextDue != null
                    && !today.isBefore(nextDue.plusDays(r.getAutoRecordAfterDays()))) {
                Transaction t = new Transaction(
                        r.getTransactionType(), nextDue, r.getDescription(), r.getAmountPaise());
                t.setFromAccountId(r.getFromAccountId());
                t.setToAccountId(r.getToAccountId());
                t.setCategoryId(r.getCategoryId());
                t.setSubCategoryId(r.getSubCategoryId());
                t.setFromRecurring(true);
                t.setRecurringId(r.getId());
                t.setSourceIndicator(Transaction.SourceIndicator.MANUAL);
                transactions.add(t);
                r.markRecorded(nextDue);
                anyRecorded = true;
                nextDue = r.getNextDueDate();
            }
        }
        if (anyRecorded && persistence != null) {
            persistence.saveTransactions(this);
            persistence.saveRecurring(this);
        }
    }

    public long getCategoryUsageCount(String categoryId) {
        if (categoryId == null) return 0;
        return transactions.stream()
                .filter(t -> categoryId.equals(t.getCategoryId())
                        || categoryId.equals(t.getSubCategoryId()))
                .count();
    }

    // ── BalanceCalculator — candidate for extraction to service/BalanceCalculator.java ──────────
    // ── Balance calculations ──────────────────────────────────────────────────

    /**
     * Returns the current outstanding principal for a loan.
     * Uses principalPaise on LOAN_PAYMENT transactions when set (new behaviour);
     * falls back to the full amountPaise for TRANSFER payments and legacy data.
     */
    public long getLoanOutstandingPaise(LoanAccount la) {
        long paid = transactions.stream()
                .filter(t -> (t.getType() == Type.TRANSFER || t.getType() == Type.LOAN_PAYMENT)
                        && la.getId().equals(t.getToAccountId()))
                .mapToLong(t -> t.getType() == Type.LOAN_PAYMENT && t.getPrincipalPaise() > 0
                        ? t.getPrincipalPaise() : t.getAmountPaise())
                .sum();
        return Math.max(0, la.getOutstandingPrincipalPaise() - paid);
    }

    public long getTotalBankBalancePaise() {
        return getBankAccounts().stream().mapToLong(ba -> {
            long bal = ba.getOpeningBalancePaise();
            for (Transaction t : transactions) {
                if (ba.getId().equals(t.getFromAccountId())) bal -= t.getAmountPaise();
                if (ba.getId().equals(t.getToAccountId()))   bal += t.getAmountPaise();
            }
            return bal;
        }).sum();
    }

    public long getCreditCardOutstandingPaise(String cardId) {
        long charged = transactions.stream()
                .filter(t -> t.getType() == Type.EXPENSE && cardId.equals(t.getFromAccountId()))
                .mapToLong(Transaction::getAmountPaise).sum();
        long paid = transactions.stream()
                .filter(t -> t.getType() == Type.CC_PAYMENT && cardId.equals(t.getToAccountId()))
                .mapToLong(Transaction::getAmountPaise).sum();
        return charged - paid;
    }

    public long getTotalCreditCardOutstandingPaise() {
        return getCreditCardAccounts().stream()
                .mapToLong(cc -> getCreditCardOutstandingPaise(cc.getId())).sum();
    }

    /**
     * Base invested amount for an investment account, including the opening balance
     * of any associated RD recurring schedules (amounts paid before app setup).
     * Use this as the starting point whenever computing the current invested value.
     */
    public long getBaseInvestedPaise(InvestmentAccount ia) {
        long base = ia.getInvestedAmountPaise();
        if (ia.getInvestmentType() == InvestmentAccount.InvestmentType.RECURRING_DEPOSIT) {
            base += getRecurring().stream()
                    .filter(r -> ia.getId().equals(r.getToAccountId()))
                    .mapToLong(com.sanchay.model.RecurringTransaction::getRdOpeningBalancePaise)
                    .sum();
        }
        return base;
    }

    public long getNetWorthPaise() {
        long totalInvested = getInvestmentAccounts().stream().mapToLong(ia -> {
            long invested = getBaseInvestedPaise(ia);
            for (Transaction t : transactions) {
                if (t.getType() == Type.INVESTMENT && ia.getId().equals(t.getToAccountId()))
                    invested += t.getAmountPaise();
                if (t.getType() == Type.TRANSFER && ia.getId().equals(t.getFromAccountId()))
                    invested -= t.getAmountPaise();
                if (t.getType() == Type.REDEEM && ia.getId().equals(t.getFromAccountId()))
                    // Old format: principalPaise set separately. New format: amountPaise == principal.
                    invested -= t.getPrincipalPaise() > 0 ? t.getPrincipalPaise() : t.getAmountPaise();
            }
            return Math.max(0, invested);
        }).sum();
        long totalLoanOutstanding = getActiveLoanAccounts().stream().mapToLong(la -> {
            return getLoanOutstandingPaise(la);
        }).sum();
        return getTotalBankBalancePaise()
                + totalInvested
                - totalLoanOutstanding
                - getTotalCreditCardOutstandingPaise();
    }

    public long getMonthlyExpensesPaise() {
        LocalDate now = LocalDate.now();
        return transactions.stream()
                .filter(t -> t.getType() == Type.EXPENSE
                        && t.getDate().getMonth() == now.getMonth()
                        && t.getDate().getYear()  == now.getYear())
                .mapToLong(Transaction::getAmountPaise).sum();
    }

    public long getMonthlyIncomePaise() {
        LocalDate now = LocalDate.now();
        return transactions.stream()
                .filter(t -> t.getType() == Type.INCOME
                        && t.getDate().getMonth() == now.getMonth()
                        && t.getDate().getYear()  == now.getYear())
                .mapToLong(Transaction::getAmountPaise).sum();
    }

    public List<Transaction> getRecentTransactions(int n) {
        return transactions.stream()
                .sorted(Comparator.comparing(Transaction::getDate).reversed())
                .limit(n).collect(Collectors.toList());
    }

    // ── Clear (used when switching data folders) ──────────────────────────────

    public void clear() {
        accounts.clear();
        transactions.clear();
        recurring.clear();
        categories.clear();
        familyMembers.clear();
        importMappings.clear();
        categoryRules.clear();
        typeRules.clear();
        activeFinancialYear = "FY 2025-26";
        dateFormat = "DD/MM/YYYY";
        groupCollapsed.put("bank", true);
        groupCollapsed.put("cc", true);
        groupCollapsed.put("loan", true);
        groupCollapsed.put("investment", true);
        dataFolderPath = "Not configured";
    }
}