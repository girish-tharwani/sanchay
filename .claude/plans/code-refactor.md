# Code Refactoring Plan — Sanchay JavaFX Application

**Prepared:** 2026-04-04  
**Scope:** Structural cleanup only — no behavior changes, no visual/CSS changes  
**Approach:** Phases from lowest-risk to highest-risk; compilable after each phase

---

## Audit Summary

The codebase has a solid three-layer architecture (model / service / ui) and the service layer is clean (no JavaFX dependencies). The main structural debt is concentrated in the UI layer:

- **~700 LOC** of dialog code embedded inline in parent screen classes (CategoriesScreen, TransactionsScreen, FinancialPlanningScreen)
- **~450–600 LOC** of repeated boilerplate across 20+ dialog classes (GridPane setup, dialog initialization, date pickers, amount fields, button/result-converter pattern)
- **Two monolithic dialog classes**: TransactionDialog (2074 LOC), EarningsDialog (822 LOC)
- **MainWindow god-object** wiring: screens communicate through static mutable fields on MainWindow; no event/callback abstraction
- **Small amount of dead/stub code** (~50–100 LOC)

---

## Phase 0 — Full Audit (COMPLETE)

Findings have been catalogued above. No code changes in this phase.

---

## Phase 1 — Dead Code Removal
*Risk: Very Low | Impact: Low | Prerequisite: None*

Remove definitively unused code that cannot affect behavior.

1. ~~**Remove `Transaction.groupTransactionId`**~~ — **RETRACTED.** This field is actively used to link the three transactions in a REDEEM group (REDEEM + GAIN/LOSE + bank principal). `DataStore.deleteTransaction()` uses it to cascade-delete the whole group; `TransactionDialog` sets and reads it for edit/prefill; `TransactionsScreen` uses it to resolve counterpart account names. Do not remove.
2. ~~**Remove `RecurringTransaction.persistenceSaveHook`**~~ — **RETRACTED.** `markRecorded()` calls this hook to trigger a save without holding a `DataStore` reference. `markRecorded()` is called from `RecordRecurringDialog`, `SkipRecurringDialog`, `DataStore.autoRecordDueRecurring()`, and `ImportService.reconcileWithRecurring()`. The hook is an intentional design decoupling pattern.
3. ~~**Remove `LoanAccount.defaultPrepaymentMode`**~~ — **RETRACTED.** `TransactionDialog` reads it to skip the prepayment-mode prompt when the user has already chosen a preference, and writes it when the user checks "Remember my choice". It is user-preference persistence.
4. ~~**Remove `EarningSource.computeMonthlyInHandPaise()`**~~ — **RETRACTED.** `FamilyMember.computeInHandPaise()` calls it via a stream to sum total household monthly in-hand income. It is not superseded.

> **Phase 1 result: No dead code found that is safe to remove.** The audit tool produced false positives on all four candidates. Phase 1 is effectively a no-op — proceed directly to Phase 2.

---

## Phase 2 — Extract Shared UI Boilerplate to Utilities
*Risk: Low | Impact: High | Prerequisite: Phase 1 complete*

Add utility methods to `UiUtils` (already exists at ~515 LOC) or a new `DialogUtils` class to eliminate the most widespread copy-paste patterns. Each extraction step: add the utility method → replace all call sites → confirm compilation.

6. **`UiUtils.createStyledDialog(String title, String iconCode)`** — extract the 7-line dialog initialization block (title, setHeaderText(null), setPrefWidth, applyStylesheet, setDialogHeader) that appears in every dialog class (~25 instances).
7. **`UiUtils.addSaveCancel(DialogPane pane)` → returns the Save ButtonType** — extract the 4-line Button/ButtonType + addAll pattern repeated in every dialog.
8. **`UiUtils.createStyledDatePicker()`** — extract the 3-line DatePicker + applySmartDateConverter + styleOnShow pattern repeated in 20+ dialogs.
9. **`UiUtils.createAmountField(String promptText)` → returns TextField** — extract the amount-field setup pattern (prompt, numeric-only listener, currency symbol) duplicated across 10+ dialogs.
10. **`UiUtils.buildFormGrid()`** — extract the standard GridPane construction (hgap=12, vgap=10, two-column ColumnConstraints) duplicated in every form-based dialog.
11. **`UiUtils.populateAccountCombo(ComboBox<Account> cb, Predicate<Account> filter)`** — extract account-list population pattern duplicated across TransactionDialog, RecordRecurringDialog, AddEditRecurringDialog, and AccountDialog.
12. **`UiUtils.wireCategorySubCategoryCombo(ComboBox<Category> catCb, ComboBox<Category> subCatCb)`** — extract the category→sub-category listener pattern that appears in TransactionDialog, EarningsDialog, and CategoriesScreen. **Caveat:** the CategoriesScreen version has extra filtering logic (excludes source category, only shows subs of selected parent) while TransactionDialog filters by transaction type. Verify during implementation that a single method with parameters can handle all cases cleanly; if not, extract only the TransactionDialog/EarningsDialog variant.
13. **`UiUtils.addValidationFilter(Dialog<?> dlg, ButtonType btn, BooleanSupplier isValid, String errorMessage)`** — extract the Platform.runLater → lookupButton → addEventFilter(ACTION) validation pattern repeated in 3–4 dialogs.

---

## Phase 3 — Extract Inline Dialogs to Own Classes
*Risk: Medium | Impact: Medium | Prerequisite: Phase 2 complete (utility methods available)*

For each inline dialog, create a dedicated class, move all dialog code into it, and replace the inline block in the parent screen with: instantiate → pass data via constructor → showAndWait() → handle result.

14. ~~**Extract `CategoriesScreen` inline "Edit Category" dialog**~~ — **RETRACTED.** There is no inline Dialog<> for edit/rename. Renames go through `SingleInputDialog.show()`, which is already the correct pattern. No extraction needed.
15. ~~**Extract `CategoriesScreen` inline "Delete Category" dialog**~~ — **RETRACTED.** Deletes use `Alert.CONFIRMATION`, which is already the correct pattern for simple confirmations. No extraction needed.
16. **Extract `CategoriesScreen.showReassignDialog()`** → `ReassignCategoryDialog` class (~105 LOC of actual Dialog<> code). Takes source category, type, usageCount, confirmLabel, and an `onComplete` Runnable via constructor. Returns Boolean result via `setResultConverter`.
17. **Extract `CategoriesScreen.showMoveSubCategoryDialog()`** → `MoveSubCategoryDialog` class (~70 LOC). Takes sub-category, currentParent, and type via constructor. Parent caller handles the `ds.moveSubCategoryParent()` call after `showAndWait()`.
18. **Extract `CategoriesScreen.showTransactionsForCategory()`** → `CategoryTransactionsDialog` class (~130 LOC). Currently inline in CategoriesScreen; takes the Category and the list of category IDs via constructor. Opens a read/edit transaction list. *(This dialog was missed by the initial audit.)*
19. **Extract `TransactionsScreen.showImportCompleteDialog()`** → `ImportCompleteDialog` class (~75 LOC). Takes `ImportService.ImportResult` via constructor; read-only summary display.
20. **FinancialPlanningScreen inline profile-incomplete dialog** — do NOT simplify to `UiUtils.showError()`. The dialog calls `navigateToProfile.run()` after OK, which `showError()` cannot do. Leave the dialog inline but move it into a private `showProfileIncompleteError()` method with a clear comment. No class extraction warranted.
21. **Extract `CashFlowForecastTab` override-edit inline `TextInputDialog`** → investigate and decide during this step whether a `UiUtils.showInputDialog()` helper or a small `EditOverrideDialog` class is appropriate.

*(Step numbers have shifted — steps 14 and 15 retracted; a new step 18 added for the missed dialog.)*

*Pattern to follow for each extraction:*
- New class receives all required data via constructor (no reference back to the parent screen)
- Data flows out via `setResultConverter()` returning a typed result object, or via a getter after `showAndWait()`
- Dialog must call `UiUtils.applyStylesheet()` in its constructor
- Parent screen handles all model mutations in response to the returned result

---

## Phase 4 — Reduce Monolithic Dialog Classes
*Risk: Medium-High | Impact: Medium | Prerequisite: Phase 3 complete*

The two oversized dialogs are candidates for decomposition into sub-components.

21. **Split `TransactionDialog` (2074 LOC) by type group.** The type-dropdown currently shows 10+ types with a large conditional visibility block. Proposed approach:
    - Keep `TransactionDialog` as the top-level coordinator (type selector + shared fields)
    - Extract each type-specific sub-form panel into a private inner class or a package-private `*Panel` class: `ExpenseIncomePanel`, `TransferPanel`, `InvestmentPanel`, `LoanPaymentPanel`, `CCPaymentPanel`
    - `TransactionDialog` swaps the active panel when the type dropdown changes
    - This is a pure refactoring: same fields, same validation, same save path — just reorganized

22. **Simplify `EarningsDialog` (822 LOC).** The SIMPLE/SALARY toggle and nested investment-account creation are the main complexity drivers:
    - Extract the PF account selection inner flow into a private helper method or a small `SelectPFAccountDialog`
    - Extract the SALARY deduction table into a `SalaryDeductionPanel` private inner class
    - Keep `EarningsDialog` as the coordinator

*Note: Both steps in this phase require careful testing — the dialogs contain dense business logic. Proceed only with user sign-off.*

---

## Phase 5 — Fix Cross-Class Wiring in MainWindow
*Risk: High | Impact: Medium | Prerequisite: Phase 3 complete*

Address the two anti-pattern fields on `MainWindow` that couple `AccountsScreen` to the FAB behavior.

23. **Fix stale `postTransactionCallback` / `transactionContextAccount` in `MainWindow`.** `AccountsScreen.buildList()` already clears both fields (lines 48–49), but they are NOT cleared when the user navigates to any other screen (Dashboard, Recurring, etc.). If the FAB is clicked after navigating away from a transaction list, it still uses the old account context and callback. **Fix:** at the top of `navigateTo()`, clear both fields unconditionally before switching screens. This is a 2-line change, not a new interface — the existing `Runnable` / `Account` types are already correct. No new class or interface is needed.

24. **Decouple `SettingsScreen` → `MainWindow`.** `SettingsScreen` currently holds a direct `MainWindow` reference and calls `mainWindow.reloadDataFolder(newPath, prefs)`. Replace the `MainWindow` field with a `BiConsumer<String, PreferencesSetupDialog.Result>` callback passed into the constructor. `MainWindow` provides the lambda (calling its own `reloadDataFolder`); `SettingsScreen` calls the callback. **Note:** a plain `Runnable` will not work here — both `newPath` (String) and `prefs` (`PreferencesSetupDialog.Result`, which can be null) must be passed through the callback.

*Note: This phase does not introduce an event bus or DI framework — only simple field clearing and a single callback replacement.*

---

## Phase 6 — Final Cleanup and Consistency Pass
*Risk: Very Low | Impact: Low | Prerequisite: All prior phases complete*

25. **Standardize access modifiers.** Audit all `public` methods in dialog and screen classes; downgrade to `private` or package-private anything not called from outside its own class.
26. **Standardize method ordering within files.** Follow: constructors / initialize() → public methods → private helpers → inner classes.
27. **Clean up comments.** Remove trivial/obvious comments, commented-out code blocks (after confirming with user), and version/changelog references in code comments.
28. **Verify CSS still applies.** After all structural changes, manually walk through every screen and dialog to confirm visual appearance is unchanged.

---

## Phase 7 — Update CLAUDE.md and README.md
*Risk: None | Prerequisite: Phase 6 complete*

29. **Update CLAUDE.md** with:
    - New dialog utility methods added to UiUtils
    - New dialog class names and their packages
    - Updated wiring patterns (callback interface names)
    - Any new packages added (e.g., `ui.dialog` sub-packages)

30. **Update README.md** with:
    - Accurate architecture description reflecting refactored structure
    - Removal of changelog/version references from architecture section
    - Updated class responsibility table if present

---

## Recommended Execution Order

| Phase | Steps | Risk | Est. Scope |
|-------|-------|------|-----------|
| 1 — Dead Code | 1–5 | Very Low | < 1 hr |
| 2 — Utility Extraction | 6–13 | Low | 3–4 hrs |
| 3 — Extract Inline Dialogs | 14–20 | Medium | 3–4 hrs |
| 4 — Monolithic Dialog Split | 21–22 | Medium-High | 4–6 hrs |
| 5 — Wiring Fix | 23–24 | High | 2–3 hrs |
| 6 — Final Cleanup | 25–28 | Very Low | 1–2 hrs |
| 7 — Docs | 29–30 | None | 1 hr |

**Total estimated scope: ~15–20 hrs of implementation work (not including review)**

---

## Files NOT in Scope

- All CSS / FXML files (visual appearance must not change)
- All model classes (clean; no changes needed)
- All service classes (clean; no changes needed)
- `module-info.java` (do not touch exports/opens unless a new package genuinely requires it)

---

## Key Decisions Requiring User Sign-Off Before Proceeding

- **Phase 4 (TransactionDialog split)** — Decomposition approach: inner classes vs. package-private `*Panel` classes
- **Phase 4 (EarningsDialog split)** — Decomposition boundary: where exactly to split the SIMPLE/SALARY toggle and PF account flow
- **Phase 5, Step 24** — Whether to use `BiConsumer<String, PreferencesSetupDialog.Result>` or a named single-method interface for the SettingsScreen callback
- **Phase 6** — Any commented-out code blocks longer than a few lines: confirm before removing
