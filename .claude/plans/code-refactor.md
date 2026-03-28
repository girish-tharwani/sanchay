# Sanchay — Code Refactoring Plan

## Context

The Sanchay codebase has a sound layered architecture (model → service → UI) but has accumulated
structural debt through organic growth: several screen classes have become very large by hosting
inline dialog code, a few utility methods are defined in the wrong class causing spurious
cross-package dependencies, and two "Get Started" guide blocks are copy-pasted rather than shared.

This plan addresses those issues in five phases — auditing first, then removing dead code,
extracting duplicated code, standardising dialog implementation (the largest chunk), and finally
fixing cross-class wiring and doing a cleanup pass. No behaviour changes are intended; every step
must leave the app compiling and running correctly.

**Constraints**
- No FXML (all UI is programmatic — keep it that way)
- No new Maven dependencies
- No DI framework
- CSS is untouched
- Existing dialog pattern: `Dialog<T>` with programmatic UI construction → continue this pattern

---

## Phase 0 — Audit Summary (complete — no code changes needed)

Key findings:

| File | Lines | Primary Issue |
|------|-------|---------------|
| `ui/accounts/AccountsScreen.java` | 1,648 | Massive: 3 concerns; static `lastExportDir`; `typeBadge()` called by other packages |
| `ui/transactions/TransactionDialog.java` | 1,431 | Massive: 8 type panels, 40+ fields, repeated wireCatSubCat pattern ×5 |
| `ui/recurring/RecurringScreen.java` | 867 | Inline add/edit form (~480 lines) |
| `ui/categories/CategoriesScreen.java` | 836 | Inline `styledInput()` dialog builder |
| `ui/MainWindow.java` | 500+ | `recordRecurring()` + `skipRecurring()` ~150 lines inline |
| `service/DataStore.java` | 832 | God object: queries, mutations, rules, calculations all mixed |
| `ui/HelpDialog.java` | 137 | `buildSteps()` duplicates `DashboardScreen.buildGetStartedCard()` |
| `ui/dashboard/DashboardScreen.java` | 373 | `buildGetStartedCard()` duplicates HelpDialog step code |

**Dialog hosting inconsistencies**

| Trigger | Dialog | Hosting |
|---------|--------|---------|
| Dashboard / RecurringScreen | Record recurring | **INLINE** in `MainWindow.java` (~110 lines) |
| Dashboard / RecurringScreen | Skip recurring | **INLINE** in `MainWindow.java` (~40 lines) |
| AccountsScreen | Add / edit account | **INLINE** in `AccountsScreen.java` (~350 lines) |
| RecurringScreen | Add / edit schedule | **INLINE** in `RecurringScreen.java` (~480 lines) |
| CategoriesScreen | Single-field input | **INLINE** via `styledInput()` (~40 lines) |
| AccountsScreen (import) | Ambiguous match, ImportMapping, RecurringMatch | Separate class ✓ |
| TransactionDialog, EarningsDialog | All transaction / earnings types | Separate class ✓ |

**Cross-class wiring issues**

1. `MainWindow` calls `AccountsScreen.typeBadge()` — static UI utility method in the wrong class.
2. `CategoriesScreen` also calls `AccountsScreen.typeBadge()` — same problem.
3. `MainWindow.recordRecurring()` is called by both `DashboardScreen` and `RecurringScreen`; the
   dialog should not live in the navigation shell.

**Duplication clusters**

1. 3-step "Get Started" guide: ~28 lines duplicated between `HelpDialog` and `DashboardScreen`.
2. `wireCatSubCat` + `UiUtils.wireAutoComplete` triple-call repeated 5× in `TransactionDialog`.
3. Private `makeAutoComplete()` wrapper in `TransactionDialog` is a 1-line delegate to
   `UiUtils.wireAutoComplete()` — needless indirection.

**Dead code**: None significant detected.

---

## Phase 1 — Dead Code Removal

### Step 1.1 — Remove `makeAutoComplete` wrapper in TransactionDialog

**What:** Delete the private `makeAutoComplete(ComboBox<Category>, List<Category>)` method in
`TransactionDialog` (it is a 1-liner delegating to `UiUtils.wireAutoComplete`). Replace all 10
call sites with direct calls to `UiUtils.wireAutoComplete(combo, masterList)`.

**Why:** Needless indirection; makes the code at each call site read as if a local (non-standard)
thing is happening when it is just the shared wire method.

**Files:** `ui/transactions/TransactionDialog.java`
**Risk:** None — mechanical substitution.
**After:** App compiles and runs correctly.

---

## Phase 2 — Extract Duplicated Code

### Step 2.1 — Move `typeBadge()` from AccountsScreen to UiUtils

**What:** `AccountsScreen.java` defines:
```java
public static Label typeBadge(Transaction.Type type) {
    Label lbl = new Label(UiUtils.badgeText(type));
    lbl.getStyleClass().addAll(UiUtils.badgeStyle(type), "badge-sm");
    return lbl;
}
```
Add this identical method as `public static` in `UiUtils.java`. Update every call site:
- `MainWindow.java` — `AccountsScreen.typeBadge(...)` → `UiUtils.typeBadge(...)`
- `CategoriesScreen.java` — same replacement
- `AccountsScreen.java` internal usage — same replacement

Then delete the method from `AccountsScreen`. Remove `import com.sanchay.ui.accounts.AccountsScreen`
from `MainWindow` and `CategoriesScreen` (now unused).

**Why:** `AccountsScreen` is a screen, not a utility library. Having other packages import it just
for a badge factory creates spurious coupling. `UiUtils` already owns `badgeText()` and `badgeStyle()`.

**Files:** `ui/UiUtils.java`, `ui/MainWindow.java`, `ui/categories/CategoriesScreen.java`,
`ui/accounts/AccountsScreen.java`
**Risk:** Verify CSS class `"badge-sm"` exists in `app.css` (it must already exist since the code
currently works — this is just a sanity check).
**After:** App compiles and runs correctly.

---

### Step 2.2 — Unify "Get Started" guide steps via UiUtils

**What:** Add `public static VBox buildGetStartedSteps()` to `UiUtils.java` returning the three
step rows (HBox each containing icon, title, nav hint). The method uses the full (HelpDialog)
step descriptions.

Add `static Text navArrow()` to `UiUtils.java` as well (both `HelpDialog.arrow()` and
`DashboardScreen.navArrow()` are identical 1-liners).

Update `HelpDialog.show()` to call `UiUtils.buildGetStartedSteps()` and delete the private methods
`buildSteps()`, `stepRow()`, `divider()`, `arrow()` from `HelpDialog`.

Update `DashboardScreen.buildGetStartedCard()` to call `UiUtils.buildGetStartedSteps()` and
delete `navArrow()` from `DashboardScreen`.

**Design decision:** Dashboard currently shows slightly shorter step descriptions than HelpDialog.
Unify to the HelpDialog (full) descriptions — they remain concise and fit the welcome card.

**Why:** The 3-step setup guide is domain knowledge; duplicating it means any edit must happen in
two places.

**Files:** `ui/UiUtils.java`, `ui/HelpDialog.java`, `ui/dashboard/DashboardScreen.java`
**Risk:** Low — layout difference: HelpDialog adds `Region` separators between steps; DashboardScreen
uses VBox spacing. The shared method returns only step rows; callers add separators themselves.
**After:** App compiles and runs. Both Help dialog and Dashboard welcome banner show the same steps.

---

## Phase 3 — Extract Inline Dialogs

All five extractions follow the same pattern as the existing `AmbiguousMatchDialog`,
`ImportMappingDialog`, and `RecurringMatchDialog`: a standalone class, `Dialog<T>` or a static
`show()` method, programmatic UI construction, no back-reference to the parent screen.

---

### Step 3.1 — Extract `RecordRecurringDialog` from MainWindow

**What:** Create `ui/recurring/RecordRecurringDialog.java` in package `com.sanchay.ui.recurring`.

Move all dialog code currently in `MainWindow.recordRecurring()` (~110 lines) into the new class.
Constructor receives `RecurringTransaction r`. A `show(Runnable onComplete, Runnable postRefresh)`
method builds and shows the dialog, calls `DataStore` directly, invokes both runnables on success.

The three private helpers used exclusively by this method — `showStyledError()`, `addDialogLabel()`,
`addDialogField()` — move into the new class as private static methods. After Step 2.1 the
`UiUtils.typeBadge()` call in this dialog resolves correctly.

Update `MainWindow.recordRecurring(RecurringTransaction, Runnable)` to a 2-line delegation:
```java
public void recordRecurring(RecurringTransaction r, Runnable onComplete) {
    new RecordRecurringDialog(r).show(onComplete, this::refreshDashboard);
}
```

**Why:** Navigation shell code should not contain transaction-recording UI logic. After extraction,
any change to the record dialog is isolated to `RecordRecurringDialog.java`.

**Files:** `ui/recurring/RecordRecurringDialog.java` (NEW), `ui/MainWindow.java`
**Risk:** Pass `this::refreshDashboard` as `Runnable` so the dialog has no MainWindow import.
**After:** Record button on Dashboard and RecurringScreen works correctly.

---

### Step 3.2 — Extract `SkipRecurringDialog` from MainWindow

**What:** Create `ui/recurring/SkipRecurringDialog.java`. Move `MainWindow.skipRecurring()` body
(~40 lines) into the new class following the same pattern as Step 3.1.

Update MainWindow:
```java
public void skipRecurring(RecurringTransaction r, Runnable onComplete) {
    new SkipRecurringDialog(r).show(onComplete, this::refreshDashboard);
}
```

After this step the private helpers `showStyledError()`, `addDialogLabel()`, `addDialogField()`
have no remaining callers in `MainWindow`. Delete them from `MainWindow`.

**Why:** Consistent with Step 3.1.

**Files:** `ui/recurring/SkipRecurringDialog.java` (NEW), `ui/MainWindow.java`
**Risk:** None — skip logic is self-contained.
**After:** Skip button works correctly. `MainWindow.java` no longer contains any dialog-building code.

---

### Step 3.3 — Extract `AccountDialog` from AccountsScreen

**What:** Create `ui/accounts/AccountDialog.java` in package `com.sanchay.ui.accounts`.

Move the four inline dialog builders from `AccountsScreen`:
- `openBankAccountDialog(BankAccount existing)` (~83 lines)
- `openCreditCardDialog(CreditCardAccount existing)` (~81 lines)
- `openLoanDialog(LoanAccount existing)` (~101 lines)
- `openInvestmentDialog(InvestmentAccount existing)` (~58 lines)

Structure as four static factory methods:
```java
public class AccountDialog {
    public static void showForBank(BankAccount existing) { ... }
    public static void showForCreditCard(CreditCardAccount existing) { ... }
    public static void showForLoan(LoanAccount existing) { ... }
    public static void showForInvestment(InvestmentAccount existing) { ... }
}
```

Move into `AccountDialog` as private static helpers: `formGrid()`, `scrolled()`, `dialog()`,
`memberCombo()`, `tf()`, `addRow()`, `nvl()`, plus all the enum parse/format helpers
(`formatInvestmentType`, `parseInvestmentType`, `formatCardStatus`, `parseCardStatus`,
`formatLoanType`, `formatLoanStatus`, `parseLoanStatus`, `formatInvestmentStatus`,
`parseInvestmentStatus`) — **only if** they are exclusively used by the dialog methods.

**Critical checks before moving:**
- Grep `info()` across `AccountsScreen` — if it is also called from import code, keep it in
  `AccountsScreen` (or add a 3-line copy in `AccountDialog`).
- Grep `formatAccountStatus()` — it is used in account cards, so it stays in `AccountsScreen`.

Update the two dispatch methods in `AccountsScreen` to delegate to `AccountDialog`.

**Why:** AccountsScreen currently mixes three concerns: account list display, account details,
and account CRUD dialogs. The CRUD dialogs are ~350 lines. Extracting them drops AccountsScreen
from 1,648 to ~1,250 lines.

**Files:** `ui/accounts/AccountDialog.java` (NEW), `ui/accounts/AccountsScreen.java`
**Risk:** Medium — grep helper method usages carefully before moving.
**After:** Add and edit account dialogs work for all four account types.

---

### Step 3.4 — Extract `AddEditRecurringDialog` from RecurringScreen

**What:** Create `ui/recurring/AddEditRecurringDialog.java`. Move
`RecurringScreen.openRecurringForm(RecurringTransaction existing)` (~480 lines) into the new class.

```java
public class AddEditRecurringDialog {
    public static void show(RecurringTransaction existing) { ... }
}
```

Move into the new class as private helpers: `miniGrid()`, `buildInvNotes()`, `appendNote()`,
`formRow()` — but **only after** confirming they are used exclusively inside `openRecurringForm`.
`formatFrequency()` and `formatStatus()` are used by the schedule table in `buildView()` — they
stay in `RecurringScreen`.

Update `RecurringScreen`: replace `openRecurringForm(null)` with `AddEditRecurringDialog.show(null)`,
and the double-click handler with `AddEditRecurringDialog.show(item)`.

**Why:** RecurringScreen mixes three concerns: pending section, all-schedules table, and the
full add/edit form. The form is ~480 lines. After extraction RecurringScreen drops from 867 to
~380 lines.

**Files:** `ui/recurring/AddEditRecurringDialog.java` (NEW), `ui/recurring/RecurringScreen.java`
**Risk:** Medium — verify helper ownership before moving. The form is self-contained (all
DataStore/UiUtils calls are via static methods; no callback into RecurringScreen).
**After:** Adding and editing recurring schedules continues to work.

---

### Step 3.5 — Extract `SingleInputDialog` from CategoriesScreen

**What:** Create `ui/SingleInputDialog.java` in package `com.sanchay.ui`. Move
`CategoriesScreen.styledInput()` (~40 lines) verbatim:

```java
public class SingleInputDialog {
    public static String show(String title, String labelText,
                              String subtitle, String initialValue) { ... }
}
```

Update the 4 call sites in `CategoriesScreen` to `SingleInputDialog.show(...)` and delete
the private `styledInput()` method.

**Why:** A single-field styled input dialog is a generic UI pattern with no logical ownership by
the categories screen.

**Files:** `ui/SingleInputDialog.java` (NEW), `ui/categories/CategoriesScreen.java`
**Risk:** None — the method has no dependencies beyond `UiUtils` static calls.
**After:** Category add, rename, subcategory add/rename dialogs all work.

---

## Phase 4 — Fix Cross-Class Wiring

### Step 4.1 — Fix `static lastExportDir` in AccountsScreen

**What:** `AccountsScreen` has `private static String lastExportDir = null`. AccountsScreen is
rebuilt on every Accounts navigation, so the static field currently provides within-session
directory persistence. The correct fix is to move the field to `MainWindow` as a regular instance
field (`private String lastAccountExportDir`) with a getter/setter, and have AccountsScreen
receive/update it via the `MainWindow` reference it already holds for callbacks.

Change the field in `AccountsScreen` to a regular instance field, read/write it through the
MainWindow accessor, and add the field to MainWindow.

**Files:** `ui/accounts/AccountsScreen.java`, `ui/MainWindow.java`
**Risk:** Low — behaviour (directory remembered within a session) is preserved.
**After:** App compiles and runs.

---

### Step 4.2 — Remove stale AccountsScreen imports from MainWindow and CategoriesScreen

**What:** After Step 2.1, neither `MainWindow.java` nor `CategoriesScreen.java` should need to
import `com.sanchay.ui.accounts.AccountsScreen`. Verify and remove the import from both files.

**Files:** `ui/MainWindow.java`, `ui/categories/CategoriesScreen.java`
**Risk:** None.
**After:** No spurious cross-package dependencies remain.

---

## Phase 5 — Final Cleanup

### Step 5.1 — Log silent catch blocks in PersistenceService

**What:** Replace the ~8 instances of empty/near-empty `catch` blocks in `PersistenceService.java`
with `System.err.println("Sanchay: failed to load [file]: " + e.getMessage())`. Do not add stack
traces for expected malformed-input cases.

**Files:** `service/PersistenceService.java`
**Risk:** None — output only.

---

### Step 5.2 — Consolidate `wireCategory` triple-call pattern in TransactionDialog

**What:** Add a private helper to `TransactionDialog`:
```java
private void wireCategory(ComboBox<Category> catCb, List<Category> catMaster,
                           ComboBox<Category> subCatCb, List<Category> subMaster) {
    wireCatSubCat(catCb, subCatCb, subMaster);
    UiUtils.wireAutoComplete(catCb,    catMaster);
    UiUtils.wireAutoComplete(subCatCb, subMaster);
}
```
Replace the 5 locations where the triple-call pattern appears with `wireCategory(...)`.

**Why:** If wiring logic changes, it must currently be updated in 5 places.

**Files:** `ui/transactions/TransactionDialog.java`
**Risk:** Low — verify call order is preserved (wireCatSubCat first, then the two wireAutoComplete calls).
**After:** Category autocomplete and sub-category cascading work correctly.

---

### Step 5.3 — Document DataStore concern seams (comment only)

**What:** Add structured comments in `DataStore.java` marking the three separable concerns
embedded in the 832-line god object:
- **RuleLearner** — `suggestCategoryForDescription()`, rule learning/application methods
- **BalanceCalculator** — `getNetWorthPaise()`, `getTotalBankBalancePaise()`, monthly totals
- **RecurringScheduler** — `getPendingRecurring()`, `autoRecordPending()`

Use the pattern:
```
// ── [ConcernName] — candidate for extraction to service/[ClassName].java ──
```

**Files:** `service/DataStore.java`
**Risk:** None — comments only.

---

## Execution Order & Dependencies

```
1.1  Remove makeAutoComplete wrapper in TransactionDialog
  └─► 5.2  Add wireCategory() helper (can follow immediately after 1.1)

2.1  Move typeBadge() to UiUtils; fix 3 call sites
  └─► 4.2  Remove stale AccountsScreen imports (do immediately after 2.1)
  └─► 3.1  RecordRecurringDialog (needs typeBadge in UiUtils)
        └─► 3.2  SkipRecurringDialog (delete shared helpers from MainWindow after 3.2)

2.2  Unify buildGetStartedSteps in UiUtils  [independent of 2.1]

3.3  AccountDialog           [independent of 3.1/3.2]
3.4  AddEditRecurringDialog  [independent]
3.5  SingleInputDialog       [independent]

4.1  Fix static lastExportDir  [independent]

5.1  PersistenceService logging  [independent]
5.3  DataStore comment seams     [independent]
```

Steps 3.1–3.5 are mutually independent and can be done in parallel.

---

## New Files to Create

| New Class | Package | Extracted From |
|-----------|---------|----------------|
| `ui/recurring/RecordRecurringDialog.java` | `com.sanchay.ui.recurring` | `MainWindow.recordRecurring()` |
| `ui/recurring/SkipRecurringDialog.java` | `com.sanchay.ui.recurring` | `MainWindow.skipRecurring()` |
| `ui/accounts/AccountDialog.java` | `com.sanchay.ui.accounts` | `AccountsScreen` dialog methods |
| `ui/recurring/AddEditRecurringDialog.java` | `com.sanchay.ui.recurring` | `RecurringScreen.openRecurringForm()` |
| `ui/SingleInputDialog.java` | `com.sanchay.ui` | `CategoriesScreen.styledInput()` |

---

## Critical Files (read before implementing each step)

- `src/main/java/com/sanchay/ui/MainWindow.java`
- `src/main/java/com/sanchay/ui/accounts/AccountsScreen.java`
- `src/main/java/com/sanchay/ui/UiUtils.java`
- `src/main/java/com/sanchay/ui/recurring/RecurringScreen.java`
- `src/main/java/com/sanchay/ui/transactions/TransactionDialog.java`
- `src/main/java/com/sanchay/ui/HelpDialog.java`
- `src/main/java/com/sanchay/ui/dashboard/DashboardScreen.java`
- `src/main/java/com/sanchay/ui/categories/CategoriesScreen.java`

---

## Verification After All Steps

1. **Build:** `bash build.sh compile` — must succeed with zero errors.
2. **Launch:** `mvn javafx:run` — walk through every screen.
3. **Checklist:**
   - [ ] Dashboard welcome banner shows 3 steps
   - [ ] Help button opens HelpDialog with same 3 steps
   - [ ] Record and Skip buttons on Dashboard recurring cards work
   - [ ] Record and Skip buttons on RecurringScreen work
   - [ ] Add/edit dialogs for all 4 account types work
   - [ ] Add/edit dialogs for recurring schedules work
   - [ ] Category add, rename, subcategory add/rename work
   - [ ] Transaction dialog (expense, income, transfer, redeem, CC payment, loan, investment) — all type panels work
   - [ ] Transaction type badges appear in RecurringScreen, CategoriesScreen, MainWindow record dialog
   - [ ] CSV import flow (ImportMappingDialog → AmbiguousMatchDialog / RecurringMatchDialog) works
