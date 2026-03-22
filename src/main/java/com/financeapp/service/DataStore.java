package com.financeapp.service;

import com.financeapp.model.*;
import com.financeapp.model.ImportMapping;
import com.financeapp.model.BankAccount.SubType;
import com.financeapp.model.InvestmentAccount.InvestmentType;
import com.financeapp.model.LoanAccount.LoanType;
import com.financeapp.model.RecurringTransaction.Frequency;
import com.financeapp.model.Transaction.Type;

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

    private String activeFinancialYear = "FY 2025-26";
    private String dateFormat = "DD/MM/YYYY";

    /** Set by MainApp after the data folder is confirmed. May be null during wizard. */
    private PersistenceService persistence;
    /** Stored independently so SettingsScreen can display it without calling PersistenceService. */
    private String dataFolderPath = "Not configured";

    private DataStore() {}

    public static DataStore getInstance() {
        if (instance == null) instance = new DataStore();
        return instance;
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
    public List<Category>             getCategories()          { return Collections.unmodifiableList(categories); }
    public List<FamilyMember>         getFamilyMembers()       { return Collections.unmodifiableList(familyMembers); }
    public List<ImportMapping>        getImportMappings()      { return Collections.unmodifiableList(importMappings); }
    public List<CategoryRule>         getCategoryRules()       { return Collections.unmodifiableList(categoryRules); }
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
        if (persistence != null) persistence.saveAccounts(this);
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
        transactions.removeIf(t -> t.getId().equals(id));
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

    // ── Category rules (learn + suggest) ─────────────────────────────────────

    /** For internal load use only — does not trigger save. */
    public void addCategoryRuleInternal(CategoryRule r) { categoryRules.add(r); }

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
                    return typeMatches && descriptionMatches(needle, r.getNormalizedKey());
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
        long distinctCats = matching.stream().map(CategoryRule::getCategoryId).distinct().count();
        if (distinctCats != 1) return Optional.empty();   // ambiguous
        return matching.stream().max(Comparator.comparingInt(CategoryRule::getCount));
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

    // ── Description normalisation helpers ─────────────────────────────────────

    /**
     * Extracts a stable, human-meaningful key from a raw bank transaction description.
     * Strips payment-rail prefixes (UPI/, NEFT-, BIL/INFT/, etc.), transaction IDs,
     * reference hashes, bank names, and other noise so that repeated transactions
     * to the same merchant produce the same key.
     *
     * Examples:
     *   "UPI/Swiggy Lim/swiggy1online.@axb/…"  → "swiggy"
     *   "UPI/GCMHATRE A/q478084833@ybl/…"       → "gcmhatre a"   (generic VPA → use display)
     *   "NEFT-BARCN…-BARCLAYS GLOBAL SERVICE…"  → "barclays global service centre salary"
     *   "BIL/INFT/EJ…/INFT//VIDHI THARWANI"    → "vidhi tharwani"
     *   "BIL/Home Loan XX47458 EMI Girish T"    → "home loan emi girish t"
     *   "CAM/09852OAR/CASH WDL/27-09-25"        → "cash withdrawal atm"
     */
    public static String normalizeDesc(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String s = raw.trim();
        String u = s.toUpperCase();

        // ── UPI: UPI/{display}/{vpa}/{remark}/{bank}/{txnId}/{ref}/
        if (u.startsWith("UPI/")) {
            String[] p = s.split("/", -1);
            String display = p.length > 1 ? p[1].trim() : "";
            String vpa     = p.length > 2 ? p[2].trim() : "";
            return cleanDesc(extractUpiMerchant(display, vpa));
        }

        // ── UPL: UPL/{txnId}/UPI/{account}/{bank}/{ref} — linked-account auto-debit
        if (u.startsWith("UPL/")) {
            String[] p = s.split("/", -1);
            String account = p.length > 3 ? p[3].replaceAll("[^0-9]", "") : "";
            String suffix  = account.length() >= 4 ? account.substring(account.length() - 4) : account;
            return cleanDesc("auto debit " + suffix);
        }

        // ── NEFT: NEFT-{ref}-{counterparty}-{more}-…
        if (u.startsWith("NEFT")) {
            String body = s.replaceFirst("(?i)^NEFT[-/\\s]*", "");
            String[] parts = body.split("[-/]+");
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                String p = part.trim();
                if (p.isEmpty()
                        || p.matches("\\d+")               // pure number (ref/amount)
                        || p.matches("[A-Z0-9]{10,}"))     // long alphanumeric ref code
                    continue;
                sb.append(p).append(" ");
            }
            return cleanDesc(sb.toString());
        }

        // ── BIL/NEFT/{ref}/{party}/{bank}
        if (u.startsWith("BIL/NEFT/")) {
            String[] p = s.split("/", -1);
            String party = p.length > 3 ? p[3].trim() : s;
            return cleanDesc(party);
        }

        // ── BIL/INFT/{ref}/{desc}/{party} (ref may be "INFT" or alphanumeric)
        if (u.startsWith("BIL/INFT/")) {
            String[] p = s.split("/", -1);
            StringBuilder sb = new StringBuilder();
            for (int i = 3; i < p.length; i++) {
                String part = p[i].trim();
                if (!part.isEmpty()
                        && !part.equalsIgnoreCase("INFT")
                        && !part.matches("[A-Z0-9]{6,}")   // skip ref codes
                        && !part.matches("\\d+")) {
                    sb.append(part).append(" ");
                }
            }
            return cleanDesc(sb.toString());
        }

        // ── BIL/{other}: e.g. "BIL/Home Loan XX47458 EMI Girish T"
        if (u.startsWith("BIL/")) {
            String rest = s.substring(4).replaceAll("(?i)XX\\d+", "").trim();
            return cleanDesc(rest);
        }

        // ── CAM: ATM cash withdrawal
        if (u.startsWith("CAM/")) return "cash withdrawal atm";

        // ── ACH: NACH/ECS mandate debit
        if (u.startsWith("ACH/")) {
            String[] p = s.split("/", -1);
            String code = p.length > 1 ? p[1].replaceAll("\\d+$", "").trim() : "";
            return cleanDesc("nach " + code);
        }

        // ── Interest credit
        if (s.matches("(?i).*int\\.?pd.*") || u.contains("INTEREST")) return "interest credit";

        // ── Internal transfers
        if (u.startsWith("TRF TO FD")) return "transfer to fd";
        if (u.startsWith("TO RD AC"))  return "rd account transfer";

        // ── Fallback: clean the whole string
        return cleanDesc(s);
    }

    /** Picks the stable merchant identifier from a UPI display name + VPA pair. */
    private static String extractUpiMerchant(String display, String vpa) {
        // Extract the handle portion (before @) from VPA
        String handle = vpa.contains("@") ? vpa.substring(0, vpa.indexOf('@')) : vpa;
        // Strip trailing punctuation and digits (e.g. "mokobara-12410" → "mokobara")
        handle = handle.replaceAll("[.\\-_]+$", "").replaceAll("\\d+$", "").trim();

        // Generic payment-gateway VPAs carry no merchant identity → fall back to display name.
        // length <= 2: phone-based UPI VPAs strip to a single prefix char (e.g. q406442473@ybl → "q")
        boolean isGenericGateway = handle.isEmpty()
                || handle.length() <= 2
                || handle.matches("\\d+")
                || handle.toLowerCase().matches("paytm.*")  // paytmqr…, paytm.s1…, paytm-…
                || handle.toLowerCase().startsWith("vyapar")
                || handle.toLowerCase().startsWith("pinelabs")
                || handle.toLowerCase().startsWith("razorpay");

        if (isGenericGateway) {
            String fallback = display.replaceAll("[^a-zA-Z0-9 ]+", " ").trim();
            // Strip trailing single-char tokens ("zomatoorder1 g" → "zomatoorder1")
            fallback = fallback.replaceAll("\\s+\\S$", "").trim();
            // Strip trailing digits from display name ("swiggy1" → "swiggy")
            fallback = fallback.replaceAll("\\d+$", "").trim();
            return fallback;
        }

        // Trim common merchant name suffixes that add noise
        handle = handle.replaceAll("(?i)(online|pay|app|store|\\d+)$", "").trim();
        return handle.isEmpty() ? display : handle;
    }

    /** Lowercases, removes non-alphanumeric characters, and collapses whitespace. */
    private static String cleanDesc(String s) {
        return s.trim().toLowerCase()
                .replaceAll("[^a-z0-9 ]+", " ")
                .replaceAll("\\s{2,}", " ")
                .strip();
    }

    private static boolean descriptionMatches(String needle, String haystack) {
        if (needle.equals(haystack)) return true;
        // Build word sets for each side — only words of 5+ chars are significant.
        // Compare whole words only (not substrings) to avoid false matches like
        // "vidhitharwani" matching "ansh tharwani" because "tharwani" is a suffix.
        Set<String> nw = Arrays.stream(needle.split(" "))
                .filter(w -> w.length() >= 5).collect(Collectors.toSet());
        Set<String> hw = Arrays.stream(haystack.split(" "))
                .filter(w -> w.length() >= 5).collect(Collectors.toSet());
        return nw.stream().anyMatch(hw::contains);
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
                .filter(c -> c.getType() == Category.CategoryType.INCOME && c.isActive())
                .collect(Collectors.toList());
    }

    /** Returns active sub-categories for a given parent category ID. */
    public List<Category> getSubCategories(String parentId) {
        if (parentId == null) return Collections.emptyList();
        return categories.stream()
                .filter(c -> parentId.equals(c.getParentId()) && c.isActive())
                .collect(Collectors.toList());
    }

    public List<RecurringTransaction> getPendingRecurring() {
        return recurring.stream()
                .filter(RecurringTransaction::hasPendingOccurrence)
                .collect(Collectors.toList());
    }

    public long getCategoryUsageCount(String categoryId) {
        if (categoryId == null) return 0;
        return transactions.stream()
                .filter(t -> categoryId.equals(t.getCategoryId())
                        || categoryId.equals(t.getSubCategoryId()))
                .count();
    }

    // ── Balance calculations ──────────────────────────────────────────────────

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

    public long getNetWorthPaise() {
        long totalInvested = getInvestmentAccounts().stream().mapToLong(ia -> {
            long invested = ia.getInvestedAmountPaise();
            for (Transaction t : transactions) {
                if (t.getType() == Type.INVESTMENT && ia.getId().equals(t.getToAccountId()))
                    invested += t.getAmountPaise();
                if (t.getType() == Type.TRANSFER && ia.getId().equals(t.getFromAccountId()))
                    invested -= t.getAmountPaise();
                if (t.getType() == Type.REDEEM && ia.getId().equals(t.getFromAccountId()))
                    invested -= t.getPrincipalPaise();
            }
            return Math.max(0, invested);
        }).sum();
        long totalLoanOutstanding = getActiveLoanAccounts().stream().mapToLong(la -> {
            long paid = transactions.stream()
                    .filter(t -> t.getType() == Type.TRANSFER && la.getId().equals(t.getToAccountId()))
                    .mapToLong(Transaction::getAmountPaise).sum();
            return Math.max(0, la.getOutstandingPrincipalPaise() - paid);
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
        activeFinancialYear = "FY 2025-26";
        dateFormat = "DD/MM/YYYY";
        dataFolderPath = "Not configured";
    }
}