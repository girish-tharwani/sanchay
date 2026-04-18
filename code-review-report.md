Expert Code Review — Sanchay v1.0.0-Myna
Scope: Full codebase — all Java source files, CSS, pom.xml, test suite.
Calibration: Financial desktop app — correctness and data integrity weighted highest.

1. Executive Summary
API Error: Stream idle timeout - partial response received

looks like the flow broke
I have enough source material. Let me now write the full review.

Sanchay — Expert Code Review
1. Executive Summary
Sanchay is a well-structured, carefully-reasoned desktop application. The CSS design system is a standout strength — token-based, consistent, and clearly documented. The import/reconciliation engine is impressively robust for its complexity. The most significant issues cluster in two areas: DataStore is a God Object that has quietly accumulated responsibilities far beyond data access, and several correctness bugs exist in persistence (saveAll missing a file, group-collapse state never saved, clear() vs reset() diverging silently). There are also a handful of subtle logic bugs worth addressing before data can be silently corrupted.

2. Findings
[BLOCKING] saveAll() never saves category_rules.json
File: PersistenceService.java:345-356

Problem: saveAll() calls every saveXxx() method except saveCategoryRules(). Any caller of saveAll() — such as a future backup export — will silently drop all learned categorization rules.


public void saveAll(DataStore store) {
    saveAccounts(store);
    saveTransactions(store);
    saveRecurring(store);
    saveCategories(store);
    saveMembers(store);
    saveSettings(store);
    saveImportMappings(store);
    saveTypeRules(store);       // ← type rules saved
    saveLoanSchedules(store);
    saveMarketValues(store);
    // ← saveCategoryRules() MISSING
}
Fix: Add saveCategoryRules(store); after saveTypeRules(store).

[BLOCKING] Group collapse state is never persisted
File: PersistenceService.java:54-62 and PersistenceService.java:315-323

Problem: AppSettings declares bankGroupCollapsed, ccGroupCollapsed, loanGroupCollapsed, investmentGroupCollapsed — but saveSettings() never writes them:


public void saveSettings(DataStore store) {
    AppSettings s = new AppSettings();
    s.dateFormat  = store.getDateFormat();
    s.currency    = store.getCurrency();
    s.yearFormat  = store.getYearFormat();
    s.expenseForecastAnalysisMonths = ...;
    s.lastBackupFolder = ...;
    // ← s.bankGroupCollapsed etc. never set from DataStore
    atomicWrite(SETTINGS, GSON.toJson(s));
}
Fields are loaded correctly on startup but silently reset to true every run. The user's sidebar collapse preferences never survive an app restart.

Fix: In saveSettings(), add:


s.bankGroupCollapsed       = store.isGroupCollapsed("bank");
s.ccGroupCollapsed         = store.isGroupCollapsed("cc");
s.loanGroupCollapsed       = store.isGroupCollapsed("loan");
s.investmentGroupCollapsed = store.isGroupCollapsed("investment");
Also wire setGroupCollapsed() in DataStore to call persistence.saveSettings(this).

[BLOCKING] DataStore.clear() diverges from reset() — stale data on folder switch
File: DataStore.java:1061-1077 vs DataStore.java:51-68

Problem: clear() (called on data-folder switch) does not clear loanSchedules or marketValues, and does not reset currency, yearFormat, expenseForecastAnalysisMonths, or lastBackupFolder. After switching to an empty data folder, loan schedules and market values from the previous folder remain in memory. Also has a commented-out dead line:


//activeFinancialYear = "FY 2025-26";
Fix: Replace clear() with a call to reset(), or make clear() call reset(). One of these methods should be removed; they serve the same purpose.

[BLOCKING] navigateTo("Transactions") silently falls through to error label
File: MainWindow.java:291-307 and MainWindow.java:323

Problem: navigateToTransactions() sets currentScreen = "Transactions", but the navigateTo() switch has no "Transactions" case. When refreshCurrentScreen() is called (e.g. after FAB creates a transaction from the Transactions view), it calls navigateTo("Transactions") which hits the default branch and displays "Screen not found: Transactions".

Fix: Either add a "Transactions" case to navigateTo() that re-navigates to the correct account, or override refreshCurrentScreen() to call navigateToTransactions(currentAccount) when currentScreen.equals("Transactions"). Requires storing the current account reference in MainWindow.

[IMPORTANT] getMonthlyExpensesPaise() and getMonthlyIncomePaise() crash on null transaction date
File: DataStore.java:1022-1024

Problem: Both methods call t.getDate().getMonth() without null-guarding the date. An auto-recorded or imported transaction with a null date would throw NullPointerException in the Dashboard's stats calculation on every refresh.


.filter(t -> t.getType() == Type.EXPENSE
        && t.getDate().getMonth() == now.getMonth()   // ← NPE if date is null
Fix: Add t.getDate() != null && before each date access in these filters. The same pattern exists in getRecentTransactions() sorting — guard it there too.

[IMPORTANT] ExpensePatternAnalyzer divides by analysisMonths not months-with-data
File: ExpensePatternAnalyzer.java:57

Problem:


long avgMonthly = totalSpent / analysisMonths;  // always divides by 12 (or configured window)
If a sub-category has data for only 4 of the 12 analysis months (sparse spender, new user, seasonal category), the average is divided by 12 instead of 4. This makes the forecast 3× too low and can drop below the ≤ 10000 threshold, silently excluding the category from forecasts entirely.

Fix: Use the number of months that actually have data:


long avgMonthly = totalSpent / Math.max(1, monthlyData.size());
[IMPORTANT] reassignCategory() null-checks after already dereferencing getClassification()
File: DataStore.java:358-362

Problem:


String tCatId    = t.getClassification() != null ? t.getClassification().getCategoryId() : null;
String tSubCatId = t.getClassification() != null ? t.getClassification().getSubCategoryId() : null;
if (fromCategoryId.equals(tCatId) || fromCategoryId.equals(tSubCatId)) {
    if (t.getClassification() == null) t.setClassification(new Transaction.Classification()); // ← dead
The inner if (t.getClassification() == null) can never be true: the outer condition requires tCatId or tSubCatId to equal fromCategoryId, which requires classification to be non-null. This is dead, misleading code — remove it.

[IMPORTANT] applySecondAccount() is copy-pasted in both DataStore and ImportService
File: DataStore.java:614-621, ImportService.java:734-741

Problem: Identical private static method in two classes. When logic needs updating (e.g. a new transaction type), both must be changed in sync.

Fix: Move to a package-accessible helper, or make DataStore's version package-private and have ImportService call it.

[IMPORTANT] DataStore is a God Object — 1100 lines, 7 distinct responsibilities
File: DataStore.java

Problem: The class itself acknowledges this with three // ── candidate for extraction comments. It currently owns: in-memory cache, CRUD+persistence delegation, balance calculations, category rule learning, type rule learning, description normalization delegation, recurring schedule auto-record, and family member management. This makes the class hard to test, slow to scan, and a merge-conflict magnet.

Recommended extractions (lowest risk first):

BalanceCalculator — getTotalBankBalancePaise(), getBankBalancePaise(), getCreditCardOutstandingPaise(), getLoanOutstandingPaise(), getNetWorthPaise(), getBaseInvestedPaise(), getInvestedPaiseAsOf(), getForecastStartingBalancePaise()
RuleLearner — learnFromTransaction(), learnTypeRule(), suggestCategoryForDescription(), suggestTypeForDescription(), getCategoryRulesFor(), reapplyRulesToImported(), isUsableRuleKey(), sortCategoriesByUsage()
RecurringScheduler — getPendingRecurring(), autoRecordPending()
[IMPORTANT] reapplyRulesToImported() scans all transactions on every transaction save
File: DataStore.java:556-603

Problem: learnFromTransaction() and learnTypeRule() — both called after every user-confirmed save — call reapplyRulesToImported(), which iterates all transactions. With 5,000 transactions, every save walks 5,000 entries. This silently gets slower as data grows.

Fix: Only iterate IMPORTED/AUTO_CATEGORIZED transactions (a much smaller set), or maintain a separate index of unreviewed imports.

[IMPORTANT] Balance calculations walk all transactions per account per call — O(n×m)
File: DataStore.java:917-953

Problem: getBankBalancePaise() and related methods loop through all transactions for every account on every Dashboard refresh. With 10 bank accounts and 10,000 transactions, a single Dashboard render walks 100,000 entries. The Dashboard calls getTotalBankBalancePaise(), getTotalCreditCardOutstandingPaise(), getNetWorthPaise(), and getMonthlyExpensesPaise() — each of which repeats the full scan.

Fix: Consider computing balances once per refresh into a snapshot map, or maintain a running balance updated incrementally when transactions are added/deleted.

[IMPORTANT] Two listeners registered for the same property in MainWindow
File: MainWindow.java:89-91


stage.maximizedProperty().addListener((obs, wasMax, isMax) -> customMaximized = isMax);
stage.maximizedProperty().addListener((obs, wasMax, isMax) -> maxBtn.setText(isMax ? "❐" : "□"));
These can be one listener. No functional bug currently but a maintenance smell — future readers may add a third.

[SUGGESTION] DataStore.dateFormat uses an uppercase sentinel as a display string
File: DataStore.java:27, DataStore.java:204-207

Problem: The stored value "DD/MM/YYYY" is uppercase (display style) but JavaFX DateTimeFormatter requires lowercase dd/MM/yyyy. The code correctly special-cases this with a string comparison, but any future developer who adds a third format string must remember this asymmetry. It would be cleaner to store a proper enum (DATE_FORMAT_DMY, DATE_FORMAT_ISO) and derive both the display label and the formatter from it.

[SUGGESTION] ImportService.parseCsv() hardcodes UTF-8 — may garble Indian bank CSVs
File: ImportService.java:93

Many Indian bank statement exports (HDFC, Axis) use UTF-8 but some (older ICICI, SBI) export ISO-8859-1. A BOM-skipping UTF-8 reader with an ISO-8859-1 fallback, or detecting encoding via CharsetDecoder with CodingErrorAction.REPORT, would handle more banks without user-visible garbling.

[SUGGESTION] updateSidebarHighlight() uses text-content string matching
File: MainWindow.java:315-320


if (node instanceof Button btn && btn.getText().contains(active))
This is fragile: a label change or emoji update to a nav button breaks highlighting silently. Use btn.setUserData(key) and match against getUserData() instead.

[SUGGESTION] ImportService.parseCsv() does not handle UTF-8 BOM
File: ImportService.java:91-100

Excel-saved CSVs on Windows often include a 3-byte UTF-8 BOM (\uFEFF) at the start. The BOM becomes part of the first cell of the first row (the header), corrupting the column name lookup. Add BOM stripping at the top of parseCsv():


if (line.startsWith("\uFEFF")) line = line.substring(1);
[PRAISE] Atomic write pattern is excellent
File: PersistenceService.java:360-376

Writing to .tmp then moving atomically guarantees no half-written JSON file can corrupt data on crash or power loss. The fallback to REPLACE_EXISTING on AtomicMoveNotSupportedException is correct. This is production-quality I/O code.

[PRAISE] Import reconciliation two-pass algorithm
File: ImportService.java:229-289

The contested-manual detection (counting how many CSV rows reference the same manual entry, then routing multi-contested matches to the ambiguous queue) is subtle and correct. Most import engines don't handle this case.

[PRAISE] DescriptionNormalizer is well-thought-out
File: DescriptionNormalizer.java

The layered approach — payment-rail-specific normalization → generic cleanup → city suffix strip → UPI VPA extraction — handles Indian bank statement format diversity cleanly. The extractUpiMerchant() gateway detection list (Paytm, Razorpay, Pine Labs) is a nice practical touch.

3. Summary Table
#	Severity	File	Finding
1	BLOCKING	PersistenceService.java:345	saveAll() silently omits saveCategoryRules()
2	BLOCKING	PersistenceService.java:315	Group collapse state declared but never written to settings.json
3	BLOCKING	DataStore.java:1061	clear() leaves loan schedules, market values, and settings from previous folder
4	BLOCKING	MainWindow.java:323	refreshCurrentScreen() with currentScreen="Transactions" shows error label
5	IMPORTANT	DataStore.java:1022	Null date on transaction crashes Dashboard stats with NPE
6	IMPORTANT	ExpensePatternAnalyzer.java:57	Average divides by window size not data size — under-forecasts sparse categories
7	IMPORTANT	DataStore.java:362	Dead null-check after already dereferencing classification
8	IMPORTANT	DataStore.java:614 / ImportService.java:734	applySecondAccount() duplicated verbatim
9	IMPORTANT	DataStore.java (whole file)	God Object: 7 responsibilities, 3 self-noted extraction candidates
10	IMPORTANT	DataStore.java:556	reapplyRulesToImported() full scan on every save — grows with data
11	IMPORTANT	DataStore.java:917	Balance calculations O(n×m) on every Dashboard refresh
12	IMPORTANT	MainWindow.java:89	Two separate listeners on same property
13	SUGGESTION	DataStore.java:27	Uppercase date format sentinel is asymmetric — consider enum
14	SUGGESTION	ImportService.java:93	UTF-8 only — may garble some Indian bank CSV exports
15	SUGGESTION	ImportService.java:91	No UTF-8 BOM stripping — breaks Excel-exported headers
16	SUGGESTION	MainWindow.java:317	Sidebar highlight uses text-content matching — fragile
4. Recommended Action Order
Fix these first — they silently corrupt or lose data:

saveAll() missing saveCategoryRules() (5-minute fix, high impact)
Group collapse state not persisted (10-minute fix — add 4 lines to saveSettings(), wire setGroupCollapsed() to save)
clear() vs reset() divergence (replace clear() with a call to reset(), delete clear())
Null date NPE in Dashboard stats (add t.getDate() != null && guards in both monthly methods)
navigateTo("Transactions") fallthrough (store current account in MainWindow, handle in switch)
Fix these next — correctness and forecasting accuracy:

ExpensePatternAnalyzer average denominator — affects every user who has sparse expense history
Dead null-check in reassignCategory() — cosmetic but misleading
Address these as capacity allows:

DataStore God Object — extract BalanceCalculator, RuleLearner, RecurringScheduler in three separate PRs, lowest risk first
Balance calculation O(n×m) — memoize per-refresh into a snapshot
BOM stripping and encoding fallback in ImportService.parseCsv()