package com.sanchay.service;

import com.sanchay.model.*;
import com.sanchay.model.ImportMapping;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Handles reading and writing all data JSON files.
 */
public class PersistenceService {

    private static final String ACCOUNTS        = "accounts.json";
    private static final String TRANSACTIONS    = "transactions.json";
    private static final String RECURRING       = "recurring.json";
    private static final String CATEGORIES      = "categories.json";
    private static final String MEMBERS         = "members.json";
    private static final String SETTINGS        = "settings.json";
    private static final String IMPORT_MAPPINGS  = "import_mappings.json";
    private static final String CATEGORY_RULES   = "category_rules.json";
    private static final String TYPE_RULES       = "type_rules.json";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDate.class, (JsonSerializer<LocalDate>)
                    (src, t, ctx) -> new JsonPrimitive(src.toString()))
            .registerTypeAdapter(LocalDate.class, (JsonDeserializer<LocalDate>)
                    (json, t, ctx) -> LocalDate.parse(json.getAsString()))
            .create();

    private final Path dataFolder;

    public PersistenceService(String dataFolderPath) {
        this.dataFolder = Paths.get(dataFolderPath);
    }

    // ── Inner models ──────────────────────────────────────────────────────────

    private static class CategoriesFile {
        List<Category> categories = new ArrayList<>();
    }

    public static class AppSettings {
        public String activeFinancialYear = "FY 2025-26";
        public String dateFormat          = "DD/MM/YYYY";
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    public void loadAll(DataStore store) {
        loadSettings(store);
        loadCategories(store);
        loadMembers(store);
        loadAccounts(store);
        loadTransactions(store);
        loadRecurring(store);
        loadImportMappings(store);
        loadCategoryRules(store);
        loadTypeRules(store);
    }

    private void loadSettings(DataStore store) {
        Path p = dataFolder.resolve(SETTINGS);
        if (!Files.exists(p)) return;
        try {
            AppSettings s = GSON.fromJson(readFile(p), AppSettings.class);
            if (s != null) {
                if (s.activeFinancialYear != null)
                    store.setActiveFinancialYearInternal(s.activeFinancialYear);
                if (s.dateFormat != null)
                    store.setDateFormatInternal(s.dateFormat);
            }
        } catch (Exception e) {
            System.err.println("Sanchay: failed to load " + SETTINGS + ": " + e.getMessage());
        }
    }

    private void loadCategories(DataStore store) {
        Path p = dataFolder.resolve(CATEGORIES);
        if (!Files.exists(p)) {
            seedDefaultCategories(store);
            saveCategories(store);
            return;
        }
        try {
            CategoriesFile cf = GSON.fromJson(readFile(p), CategoriesFile.class);
            if (cf == null) { seedDefaultCategories(store); saveCategories(store); return; }
            if (cf.categories != null) cf.categories.forEach(store::addCategoryInternal);
            if (store.getCategories().isEmpty()) { seedDefaultCategories(store); saveCategories(store); }
        } catch (Exception e) { seedDefaultCategories(store); saveCategories(store); }
    }

    private void loadMembers(DataStore store) {
        Path p = dataFolder.resolve(MEMBERS);
        if (!Files.exists(p)) return;
        try {
            Type listType = new TypeToken<List<FamilyMember>>(){}.getType();
            List<FamilyMember> list = GSON.fromJson(readFile(p), listType);
            if (list != null) list.forEach(store::addFamilyMemberInternal);
        } catch (Exception e) {
            System.err.println("Sanchay: failed to load " + MEMBERS + ": " + e.getMessage());
        }
    }

    private void loadAccounts(DataStore store) {
        Path p = dataFolder.resolve(ACCOUNTS);
        if (!Files.exists(p)) {
            seedDefaultAccounts(store);
            saveAccounts(store);
            return;
        }
        try {
            JsonArray arr = JsonParser.parseString(readFile(p)).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                String category = resolveAccountCategory(obj);
                Account acc = switch (category) {
                    case "BANK"        -> GSON.fromJson(obj, BankAccount.class);
                    case "CREDIT_CARD" -> GSON.fromJson(obj, CreditCardAccount.class);
                    case "LOAN"        -> GSON.fromJson(obj, LoanAccount.class);
                    case "INVESTMENT"  -> GSON.fromJson(obj, InvestmentAccount.class);
                    default            -> null;
                };
                if (acc != null) store.addAccountInternal(acc);
            }
        } catch (Exception e) {
            seedDefaultAccounts(store);
            saveAccounts(store);
        }
    }

    /**
     * Returns the accountCategory string for a JSON account object.
     * Reads the new "accountCategory" field if present; otherwise migrates from the
     * legacy "accountType" display string so that old accounts.json files still load.
     */
    private static String resolveAccountCategory(JsonObject obj) {
        if (obj.has("accountCategory")) return obj.get("accountCategory").getAsString();
        // ── Legacy migration ──────────────────────────────────────────────────
        String legacy = obj.has("accountType") ? obj.get("accountType").getAsString() : "";
        return switch (legacy) {
            case "Savings Account", "Current Account"                        -> "BANK";
            case "Credit Card"                                               -> "CREDIT_CARD";
            case "Home Loan", "Vehicle Loan", "Personal Loan"               -> "LOAN";
            case "Debt Bonds", "Mutual Funds", "Equity",
                 "Fixed Deposit (FD)", "Recurring Deposit (RD)",
                 "Provident Fund"                                            -> "INVESTMENT";
            default                                                          -> "";
        };
    }

    /**
     * Seeds default investment accounts on first run (when accounts.json is absent or corrupt).
     */
    private void seedDefaultAccounts(DataStore store) {
        Object[][] defs = {
            { "All Equities",    InvestmentAccount.InvestmentType.EQUITY,
              "This is where you record all your equity transactions." },
            { "All Mutual Funds", InvestmentAccount.InvestmentType.MUTUAL_FUNDS,
              "This is where you record all your mutual funds transactions." },
            { "All Bonds",       InvestmentAccount.InvestmentType.DEBT_BONDS,
              "This is where you record all your bonds transactions." },
            { "All FDs",         InvestmentAccount.InvestmentType.FIXED_DEPOSIT,
              "This is where you record all your FD transactions." },
            { "All RDs",         InvestmentAccount.InvestmentType.RECURRING_DEPOSIT,
              "This is where you record all your RD transactions." },
        };
        for (Object[] def : defs) {
            InvestmentAccount acc = new InvestmentAccount(
                    (String) def[0],
                    (InvestmentAccount.InvestmentType) def[1]);
            acc.setDescription((String) def[2]);
            store.addAccountInternal(acc);
        }
    }

    private void loadTransactions(DataStore store) {
        Path p = dataFolder.resolve(TRANSACTIONS);
        if (!Files.exists(p)) return;
        try {
            Type listType = new TypeToken<List<Transaction>>(){}.getType();
            List<Transaction> list = GSON.fromJson(readFile(p), listType);
            if (list != null) list.forEach(store::addTransactionInternal);
        } catch (Exception e) {
            System.err.println("Sanchay: failed to load " + TRANSACTIONS + ": " + e.getMessage());
        }
    }

    private void loadRecurring(DataStore store) {
        Path p = dataFolder.resolve(RECURRING);
        if (!Files.exists(p)) return;
        try {
            Type listType = new TypeToken<List<RecurringTransaction>>(){}.getType();
            List<RecurringTransaction> list = GSON.fromJson(readFile(p), listType);
            if (list != null) list.forEach(store::addRecurringInternal);
        } catch (Exception e) {
            System.err.println("Sanchay: failed to load " + RECURRING + ": " + e.getMessage());
        }
    }

    private void loadImportMappings(DataStore store) {
        Path p = dataFolder.resolve(IMPORT_MAPPINGS);
        if (!Files.exists(p)) return;
        try {
            Type listType = new TypeToken<List<ImportMapping>>(){}.getType();
            List<ImportMapping> list = GSON.fromJson(readFile(p), listType);
            if (list != null) list.forEach(store::addImportMappingInternal);
        } catch (Exception e) {
            System.err.println("Sanchay: failed to load " + IMPORT_MAPPINGS + ": " + e.getMessage());
        }
    }

    private void loadCategoryRules(DataStore store) {
        Path p = dataFolder.resolve(CATEGORY_RULES);
        if (!Files.exists(p)) return;
        try {
            Type listType = new TypeToken<List<CategoryRule>>(){}.getType();
            List<CategoryRule> list = GSON.fromJson(readFile(p), listType);
            if (list != null) list.forEach(store::addCategoryRuleInternal);
        } catch (Exception e) {
            System.err.println("Sanchay: failed to load " + CATEGORY_RULES + ": " + e.getMessage());
        }
    }

    private void loadTypeRules(DataStore store) {
        Path p = dataFolder.resolve(TYPE_RULES);
        if (!Files.exists(p)) return;
        try {
            Type listType = new TypeToken<List<TypeRule>>(){}.getType();
            List<TypeRule> list = GSON.fromJson(readFile(p), listType);
            if (list != null) list.forEach(store::addTypeRuleInternal);
        } catch (Exception e) {
            System.err.println("Sanchay: failed to load " + TYPE_RULES + ": " + e.getMessage());
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    public void saveAccounts(DataStore store) {
        JsonArray arr = new JsonArray();
        for (Account a : store.getAccounts()) {
            JsonObject obj = GSON.toJsonTree(a).getAsJsonObject();
            obj.addProperty("accountCategory", a.getAccountCategory().name());
            arr.add(obj);
        }
        atomicWrite(ACCOUNTS, GSON.toJson(arr));
    }

    public void saveTransactions(DataStore store) {
        atomicWrite(TRANSACTIONS, GSON.toJson(store.getTransactions()));
    }

    public void saveRecurring(DataStore store) {
        atomicWrite(RECURRING, GSON.toJson(store.getRecurring()));
    }

    public void saveCategories(DataStore store) {
        CategoriesFile cf = new CategoriesFile();
        cf.categories = new ArrayList<>(store.getCategories());
        atomicWrite(CATEGORIES, GSON.toJson(cf));
    }

    public void saveMembers(DataStore store) {
        atomicWrite(MEMBERS, GSON.toJson(store.getFamilyMembers()));
    }

    public void saveSettings(DataStore store) {
        AppSettings s = new AppSettings();
        s.activeFinancialYear = store.getActiveFinancialYear();
        s.dateFormat = store.getDateFormat();
        atomicWrite(SETTINGS, GSON.toJson(s));
    }

    public void saveImportMappings(DataStore store) {
        atomicWrite(IMPORT_MAPPINGS, GSON.toJson(store.getImportMappings()));
    }

    public void saveCategoryRules(DataStore store) {
        atomicWrite(CATEGORY_RULES, GSON.toJson(store.getCategoryRules()));
    }

    public void saveTypeRules(DataStore store) {
        atomicWrite(TYPE_RULES, GSON.toJson(store.getTypeRules()));
    }

    public void saveAll(DataStore store) {
        saveAccounts(store);
        saveTransactions(store);
        saveRecurring(store);
        saveCategories(store);
        saveMembers(store);
        saveSettings(store);
        saveImportMappings(store);
        saveTypeRules(store);
    }

    // ── Atomic write ──────────────────────────────────────────────────────────

    private void atomicWrite(String fileName, String json) {
        Path target = dataFolder.resolve(fileName);
        Path tmp    = dataFolder.resolve(fileName + ".tmp");
        try {
            Files.createDirectories(dataFolder);
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            try { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException ex) { ex.printStackTrace(); }
        } catch (IOException e) {
            e.printStackTrace();
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    private String readFile(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    // ── Default seed ──────────────────────────────────────────────────────────

    /**
     * Seeds default categories and sub-categories.
     * Called only when categories.json does not exist or is unreadable.
     */
    public static void seedDefaultCategories(DataStore store) {
        String[][] expenseDefs = {
                {"Automobile",
                        "Driver", "Fuel", "Maintenance"},
                {"Automobile Loan",
                        "Bank Charges", "EMI Payment", "Prepayment"},
                {"Bills",
                        "Cell Phone", "Electricity", "Internet Service",
                        "LPG/PNG", "Newspaper", "OTT Subscriptions",
                        "Society Maintenance", "Telephone"},
                {"Capital Market",
                        "Charges and Fees", "Mutual Funds Loss", "Trading Loss"},
                {"Education",
                        "Books", "Fees", "Food", "Miscellaneous", "Transport", "Tuition"},
                {"Food/Dining",
                        "Drink", "Food Delivery", "Restaurants"},
                {"Healthcare",
                        "Dental", "Eyecare", "Hospital", "Physician", "Prescriptions"},
                {"Hobbies/Leisure",
                        "Books/Magazines", "Hobby Classes",
                        "Photography", "Shows/Events", "Sporting Goods", "Toys/Games"},
                {"Home Loan",
                        "Bank Charges", "EMI Payment", "Prepayment"},
                {"Household",
                        "Appliances", "Furnishings", "Grocery",
                        "House Cleaning", "Maintenance", "Miscellaneous", "Rent"},
                {"Insurance",
                        "Automobile", "Health", "Home", "Life"},
                {"Lifestyle",
                        "Clothing", "Cosmetics", "Gadgets", "Personal Care/Salon", "Wellness/Spa/Gym"},
                {"Miscellaneous",
                        "Auto/Cab", "Bank Charges", "Donations", "Gifts", "Others"},
                {"Taxes",
                        "Income Tax", "Property Tax"},
                {"Travel/Vacation",
                        "Food", "Hotels", "Miscellaneous", "Taxis", "Tickets"},
        };

        for (String[] def : expenseDefs) {
            Category parent = new Category(def[0], Category.CategoryType.EXPENSE, null);
            store.addCategoryInternal(parent);
            for (int i = 1; i < def.length; i++) {
                store.addCategoryInternal(
                        new Category(def[i], Category.CategoryType.EXPENSE, parent.getId()));
            }
        }

        String[] incomeDefs = {
                "Bonus", "Business", "Interest",
                "Miscellaneous", "Rental Income", "Salary"
        };
        for (String name : incomeDefs) {
            store.addCategoryInternal(new Category(name, Category.CategoryType.INCOME, null));
        }

        String[][] incomeWithSubDefs = {
                {"Capital Market",
                        "Dividend", "Mutual Funds", "Trading"},
        };
        for (String[] def : incomeWithSubDefs) {
            Category parent = new Category(def[0], Category.CategoryType.INCOME, null);
            store.addCategoryInternal(parent);
            for (int i = 1; i < def.length; i++) {
                store.addCategoryInternal(
                        new Category(def[i], Category.CategoryType.INCOME, parent.getId()));
            }
        }
    }

    /** Returns true if the data folder has at least one data file already. */
    public boolean hasExistingData() {
        return Files.exists(dataFolder.resolve(ACCOUNTS))
                || Files.exists(dataFolder.resolve(TRANSACTIONS))
                || Files.exists(dataFolder.resolve(CATEGORIES));
    }
}