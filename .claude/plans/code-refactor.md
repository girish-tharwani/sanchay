# Sanchay Code Refactoring Plan

> Generated: 2026-04-16 | Branch: dev/Myna
> Based on full audit of 104 Java classes across model, service, and UI packages.

---

## Audit Summary

| Area | Finding | Severity |
|------|---------|----------|
| Oversized classes | 5 classes between 800–1190 lines mixing multiple concerns | High |
| Code duplication | ~1,800 lines of near-identical boilerplate (form grids, dialog setup, table building, account combos, category combos) | Medium |
| Dead code | ~75 lines of commented-out financial-year feature in DataStore | Low |
| Wiring problems | 4 structural patterns: UI↔UI coupling, dialog→DataStore direct writes, tight panel coupling in TransactionDialog, MarkdownRenderer building JavaFX nodes | Medium–High |
| Logic issues | Null unchecked on `transaction.getDate()` in DataStore; silent exception swallow in AppConfig; possibly unreachable null-startDate in RecurringTransaction | Low |
| Dialog hosting | 37 dialogs — all are separate classes ✓ No inline dialog code to extract | None needed |

---

## Phase 0 — Audit (Complete)

Full findings stored in this plan. No code changes in this phase.

Key files by concern:

- **Largest classes:** `TransactionsScreen` (1190), `CashFlowForecastTab` (1047), `EarningsDialog` (914), `AddEditRecurringDialog` (851), `FinancialPlanningScreen` (815)
- **Most duplicated boilerplate:** `UiUtils`, all dialog classes, all transaction panels, all report tabs
- **Most entangled wiring:** `TransactionDialog` ↔ 8 panel classes, `MainWindow` ↔ all screen classes
- **Dead code location:** `DataStore.java` lines 195–279 (commented FY feature)

---

## Phase 1 — Dead Code Removal (Complete)

**Goal:** Remove code that is definitely unused. Zero behavior change.

### Step 1.1 — Remove commented financial-year code from DataStore
- **File:** `DataStore.java` lines 195–279
- **Action:** Delete the large commented-out block (FY getter/setter/parser methods).
- **Risk:** None — these are multi-line comments not executable code.
- **Verify:** Compile successfully after deletion.

### Step 1.2 — Remove commented imports
- **Files:** `TransactionDialog.java` (lines 6, 10, 18), `TransactionsScreen.java` (line 7)
- **Action:** Delete commented-out import lines.
- **Risk:** None.

### Step 1.3 — Review AccountsScreen `showClosed*` CheckBox fields
- **File:** `AccountsScreen.java` lines 28–31
- **Action:** Confirm whether the four `showClosedXxx` CheckBox fields are state-bearing or rebuilt statelessly on each `buildList()` call. If confirmed stateless, remove them and create locally in `buildList()`.
- **Risk:** Low — but requires user confirmation before removal.

---

## Phase 2 — Extract Duplicated Code Into Utilities (Complete)

**Goal:** Consolidate repeated boilerplate into existing or new utility classes. Reduces noise so subsequent class splits operate on clean, minimal code.

### Step 2.1 — Enforce UiUtils form-grid methods universally
- **Duplication:** ~200 lines of raw `GridPane` construction spread across `AccountDialog`, `EarningsDialog`, `AddEditRecurringDialog`, `MajorEventDialog`, and several smaller dialogs.
- **Action:** Replace all manual `GridPane` + `ColumnConstraints` setup with calls to `UiUtils.buildFormGrid()` and `UiUtils.addFormRow()`. These methods already exist in `UiUtils`; callers are inconsistently using them.
- **Risk:** Low — mechanical substitution, behavior identical.
- **Estimated reduction:** ~100 lines.

### Step 2.2 — Extract AccountComboFactory utility
- **Duplication:** Account dropdown building logic (~150 lines total) duplicated across `ExpensePanel`, `IncomePanel`, `TransferPanel`, `CCPaymentPanel`, `LoanPaymentPanel`, `RedeemPanel`, and `AccountDialog`.
- **Action:** Create `ui/common/AccountComboFactory.java` with static methods:
  - `bankCombo(DataStore ds, String prompt)` — returns ComboBox<Account> filtered to BankAccounts
  - `ccCombo(DataStore ds, String prompt)` — returns ComboBox<CreditCardAccount>
  - `loanCombo(DataStore ds, String prompt)` — returns ComboBox<LoanAccount>
  - `investmentCombo(DataStore ds, String prompt)` — returns ComboBox<InvestmentAccount>
  - `anyActiveCombo(DataStore ds, String prompt)` — returns all active accounts
- **Replace all callers** with calls to `AccountComboFactory`.
- **Risk:** Low — static utility, no side effects.
- **Estimated reduction:** ~120 lines.

### Step 2.3 — Consolidate category/sub-category cascading combo pattern
- **Duplication:** The category→sub-category wiring pattern is repeated ~8 times across `ExpensePanel`, `IncomePanel`, `RefundPanel`, and `TransactionDialog`.
- **Action:** Extract a `CategoryComboWiring` utility in `ui/common/` with a single static method:
  `wire(ComboBox<Category> catCb, ComboBox<Category> subCatCb, List<Category> allCategories)`
  that registers the change listener and populates sub-categories reactively.
- **Risk:** Low — same logic, just moved.
- **Estimated reduction:** ~120 lines.

### Step 2.4 — Consolidate transaction table column setup
- **Duplication:** `TableView<Transaction>` with date/description/amount/category columns built identically in `TransactionsScreen`, `CategoryTransactionsDialog`, and `UncategorizedReviewDialog`.
- **Action:** Extract `TransactionTableBuilder` utility in `ui/common/` with static factory:
  `buildStandardColumns(boolean showCategory, boolean showAccount)` returning a list of `TableColumn<Transaction, ?>`.
- **Risk:** Low — no logic change, just column creation extracted.
- **Estimated reduction:** ~120 lines.

---

## Phase 2.5 — Split Oversized Classes (skipped - to be done later)

Work lowest-risk first. All splits follow this pattern: extract → wire via constructor parameter → verify no behavior change.

### Step 2.5.1 — Split FinancialPlanningScreen (815 → ~215 lines) [Low Risk]

**Rationale:** This class has four completely independent section cards that do not share fields. Clean seams.

**Extract:**
1. `ui/planning/PlanParametersPanel.java` (~150 lines) — the input parameters form (retirement age, inflation, tax rates). Constructor takes `PlanParameters`; exposes `getPlanParameters()`.
2. `ui/planning/CorpusSectionCard.java` (~150 lines) — corpus breakdown display. Constructor takes computed result object.
3. `ui/planning/EarningsExpensesSectionCard.java` (~150 lines) — earnings and expense projection display.
4. `ui/planning/PostRetirementSectionCard.java` (~150 lines) — post-retirement balance projection display.

**Result:** `FinancialPlanningScreen` reduced to ~215 lines — layout composition + calculate button orchestration.

**Risk:** Low — no cross-section shared state detected.

---

### Step 2.5.2 — Split EarningsDialog (914 → ~340 lines) [Low Risk]

**Rationale:** SIMPLE and SALARY earnings have entirely separate form layouts. The dialog is a type-selector + dispatcher.

**Extract:**
1. `ui/profile/SimpleEarningsPanel.java` (~150 lines) — form fields for SIMPLE income type. Constructor takes optional prefill `EarningSource`; exposes `buildPanel()` and `collectValues()`.
2. `ui/profile/SalaryEarningsPanel.java` (~150 lines) — form fields for SALARY type with tax/PF calculator. Same interface.
3. `ui/profile/EarningScheduleBuilder.java` (~100 lines) — logic for auto-creating a recurring schedule from a new `EarningSource`. Extracted from save handler.

**Result:** `EarningsDialog` reduced to ~340 lines — type selector, panel switching, dialog chrome.

**Risk:** Low — panel classes need `EarningSource` data in but produce `EarningSource` data out; no back-reference to dialog needed.

---

### Step 2.5.3 — Split AddEditRecurringDialog (851 → ~490 lines) [Medium Risk]

**Rationale:** Investment-type recurring (FD, RD, Bond) requires a large set of extra fields that are invisible for all other types. Clean separation.

**Extract:**
1. `ui/recurring/InvestmentRecurringPanel.java` (~200 lines) — fields for RD/FD/Bond recurring: scheme, units/NAV, maturity date, interest rate, source account link. Shown/hidden via `setVisible()`.
2. `ui/recurring/AutoRecordSettingsPanel.java` (~80 lines) — the auto-record toggle + day-of-month picker section.

**Result:** `AddEditRecurringDialog` reduced to ~490 lines.

**Risk:** Medium — `InvestmentRecurringPanel` needs read access to the account combo selection in the parent; pass account ID via listener/property rather than back-reference.

---

### Step 2.5.4 — Split CashFlowForecastTab (1047 → ~520 lines) [Medium Risk]

**Rationale:** Chart building, override UI, and pattern analysis are distinct concerns with minimal cross-talk once the data model is defined.

**Extract:**
1. `service/ExpensePatternAnalyzer.java` (~100 lines) — pure logic: seasonality scoring, trend detection from transaction history. No UI dependencies.
2. `ui/reports/ForecastChartBuilder.java` (~180 lines) — builds the `LineChart`, sets data series, manages tooltip nodes. Receives computed projection data; returns the chart node.
3. `ui/reports/ForecastOverridesPanel.java` (~150 lines) — the checkbox/text-field grid for per-category override editing. Exposes `getOverrides()`.

**Result:** `CashFlowForecastTab` reduced to ~520 lines — result table + orchestration.

**Risk:** Medium — chart builder needs a callback to the tab for data refresh; use `Consumer<List<ProjectedMonth>>` rather than a direct tab reference.

---

### Step 2.5.5 — Split TransactionsScreen (1190 → ~640 lines) [High Risk]

**Rationale:** Largest class in the codebase. Mixes view layout, table rendering, import/export orchestration, and reconciliation flow. Tackle last because it touches the most collaborators.

**Extract:**
1. `ui/transactions/TransactionStatsPanel.java` (~100 lines) — the header stats row (bank balance, CC outstanding, investment value). Exposes `refresh(Account)` to update displayed values.
2. `ui/transactions/ImportOrchestrator.java` (~250 lines) — the entire clipboard-paste → column-mapping → parse → reconciliation → results flow. Receives account and a `Consumer<List<Transaction>>` import-complete callback.
3. `ui/transactions/TransactionContextMenu.java` (~100 lines) — the right-click context menu for table rows (edit, delete, reconcile, split). Receives callbacks for each action.

**Result:** `TransactionsScreen` reduced to ~640 lines — table setup, filtering, sorting, and screen composition.

**Risk:** High — `ImportOrchestrator` needs access to several DataStore query results. Pass them via constructor. Do not pass `TransactionsScreen` reference into `ImportOrchestrator`.

---

## Phase 3 — Standardize Dialog Implementation (Complete)

**Status:** All 37 dialogs are already in separate classes with consistent `Dialog<T>` + `setResultConverter()` + `showAndWait()` patterns. No dialog extraction needed.

**Remaining inconsistency to address:**

### Step 3.1 — Standardize AccountDialog static-factory pattern
- **Issue:** `AccountDialog` uses 4 static factory methods (~80 lines each) that build dialogs inline. Pattern works but is harder to test or subclass.
- **Action:** Keep the static factory API (it's the public contract), but refactor each factory method body to delegate to a private `buildDialogPane(AccountType type, Account existing)` method, reducing repetition.
- **Risk:** Low — same public interface, just DRY internal implementation.

### Step 3.2 — Confirm all dialogs call UiUtils.applyStylesheet()
- **Action:** Audit all Dialog subclasses to confirm they call `UiUtils.applyStylesheet(dlg)` in their constructor (per the CSS gotcha in CLAUDE.md). Flag any that don't.
- **Risk:** Low — adding a missing stylesheet call never breaks layout; it only fixes missing styles.

---

## Phase 4 — Fix Cross-Class Wiring (in-progress)

**Goal:** Break tight coupling patterns without redesigning the architecture.

### Step 4.1 — Define Panel interface for TransactionDialog
- **Issue:** `TransactionDialog` directly instantiates and manages 8 panel classes. Panels call back into the parent via direct method calls (`parent.makeCatCb()`, `parent.accountCombo()`), creating tight bidirectional coupling.
- **Action:**
  1. Extract a `TransactionPanel` interface with methods: `buildNode()`, `collectTransaction(TransactionDialog.CommonFields)`, `prefill(Transaction)`, `applyContextAccount(Account)`, `focusFirstEmpty()`.
  2. Move the `makeCatCb()` / `accountCombo()` helpers that panels call into `AccountComboFactory` and `CategoryComboWiring` (already extracted in Phase 2), removing the back-reference to parent.
  3. `TransactionDialog` manages panels via the interface, not concrete classes.
- **Risk:** Medium — requires touching all 8 panel classes. Do after Phase 2 utilities are in place.

### Step 4.2 — Decouple MainWindow callbacks from Screen classes
- **Issue:** `MainWindow` passes itself or its private fields (`postTransactionCallback`, `transactionContextAccount`) into screen constructors. Screens can mutate these fields.
- **Action:** Replace with a lightweight `NavigationContext` value object:
  ```java
  record NavigationContext(Runnable onTransactionSaved, Account contextAccount) {}
  ```
  Screens receive `NavigationContext` via constructor and call only the callback — no MainWindow reference.
- **Risk:** Low — local refactoring within the UI shell layer.

### Step 4.3 — Remove Dialog→DataStore direct write pattern (optional — decided not to do it)
- **Issue:** Several dialogs (`AccountDialog`, `LoanScheduleDialog`) write directly to `DataStore` instead of returning data to the parent screen to persist.
- **Note:** This is a deeper architectural change. Dialogs currently return `void` and save internally; changing return types would require touching all callers.
- **Action:** Discuss with user whether to pursue. If yes, convert affected dialogs from `Dialog<Void>` to `Dialog<T>` where `T` is the saved entity. Let the calling screen handle persistence.
- **Risk:** High if changed — affects 8+ dialogs and their callers. Flag for discussion before implementing.

---

## Phase 5 — Final Cleanup and Consistency Pass (pending)

### Step 5.1 — Access modifier audit
- Review all `public` methods across split and extracted classes. Methods only used within a package should be package-private. Methods only used within their class should be `private`.

### Step 5.2 — Null safety fixes
- **File:** `DataStore.java` — add null check on `t.getDate()` inside `getInvestedPaiseAsOf()`.
- **File:** `AppConfig.java` — add `System.err.println` logging inside the currently swallowed `catch (Exception ignored)` in `loadVersion()`.

### Step 5.3 — Remove trivial comments
- Sweep all modified files: remove comments that restate what the code says, remove changelog/version references from inline comments.
- Keep only non-obvious design decisions and workarounds.

### Step 5.4 — Method ordering standardization
- Within all modified classes, order methods: constructors → public API → package-private API → private helpers → inner classes.

### Step 5.5 — CSS stylesheet audit
- After all structural changes, verify every dialog and screen still renders correctly. Specifically check that no new class introduced in Phase 2.5 creates a dialog without calling `UiUtils.applyStylesheet()`.

---

## Phase 6 — Update CLAUDE.md and README.md (pending)

### Step 6.1 — Update CLAUDE.md
- Add the new utility classes (`AccountComboFactory`, `CategoryComboWiring`, `TransactionTableBuilder`) to the Dialog Utilities table.
- Update the TransactionDialog Panel Architecture section to reflect the new `TransactionPanel` interface.
- Add the new split screen/dialog classes to the Dialog Classes section.

### Step 6.2 — Update README.md
- Remove any version or changelog references.
- Update architecture section to reflect new class structure.

---

## Execution Order Summary

| Priority | Step | Files Affected | Risk | Lines Saved |
|----------|------|----------------|------|-------------|
| 1 | 1.1 Remove dead FY code | DataStore.java | None | 75 |
| 2 | 1.2 Remove commented imports | 2 files | None | 10 |
| 3 | 2.1 Enforce UiUtils form grids | ~8 dialogs | Low | ~100 |
| 4 | 2.2 AccountComboFactory | ~7 panel classes | Low | ~120 |
| 5 | 2.3 CategoryComboWiring | ~4 panel classes | Low | ~120 |
| 6 | 2.4 TransactionTableBuilder | 3 classes | Low | ~120 |
| 7 | 2.5.1 Split FinancialPlanningScreen | 1 → 5 files | Low | ~600 |
| 8 | 2.5.2 Split EarningsDialog | 1 → 4 files | Low | ~574 |
| 9 | 2.5.3 Split AddEditRecurringDialog | 1 → 3 files | Medium | ~361 |
| 10 | 2.5.4 Split CashFlowForecastTab | 1 → 4 files | Medium | ~527 |
| 11 | 3.1 AccountDialog DRY internals | 1 file | Low | ~200 |
| 12 | 3.2 applyStylesheet audit | All dialogs | Low | 0 |
| 13 | 4.1 TransactionPanel interface | 9 files | Medium | ~150 |
| 14 | 4.2 NavigationContext decoupling | MainWindow + 3 screens | Low | ~30 |
| 15 | 4.3 Dialog→DataStore (discuss) | 8+ dialogs | High | TBD |
| 16 | 2.5.5 Split TransactionsScreen | 1 → 4 files | High | ~550 |
| 17 | 5.1–5.5 Final cleanup | All modified files | Low | ~50 |
| 18 | 6.1–6.2 Docs update | CLAUDE.md, README.md | None | — |

---

## Pre-Conditions Before Coding

- Confirm Step 1.3 (AccountsScreen CheckBox fields): user confirms whether state preservation matters.
- Confirm Step 4.3 (Dialog→DataStore): user decides whether to pursue architectural change.
- Do not begin Phase 2.5 splits until Phase 1 + Phase 2 utility extractions are complete.
- Do not begin Phase 4.1 (Panel interface) until Phase 2.2 (AccountComboFactory) and 2.3 (CategoryComboWiring) are done.
- Compile must succeed after every step before proceeding to the next.
