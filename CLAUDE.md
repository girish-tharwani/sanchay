# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Sanchay** is a JavaFX desktop application for personal finance management (accounts, transactions, investments, loans, forecasting, planning). It is a single-user app with a custom undecorated window chrome.

## Build & Run Commands

```bash
# Run the application
mvn javafx:run

# Run with remote debugger on port 5005
mvn javafx:run -Pdebug

# Build fat JAR (output: target/sanchay-app.jar)
mvn clean package
mvn shade:shade
```
### Coding Style
README.md at project root contains coding commandments for UI styling and functional coding.

### JavaFX CSS Gotchas
- **CSS priority**: Bean setters (`setBackground()`, `setFont()`, etc.) have *lower* priority than author stylesheets and will be silently overridden once CSS loads. Only `.setStyle()` (inline style) beats author CSS. Any background or color that must survive stylesheet loading must be expressed as a CSS class.
- **Dialog scenes**: JavaFX dialogs open in a separate scene and never inherit the main window's stylesheets. Every `Dialog`/`Alert` must call `UiUtils.applyStylesheet()` explicitly, otherwise design tokens and component styles won't resolve.
- **Tokens in inline styles**: CSS looked-up colour tokens (e.g. `-brand-dark`, `-color-error`) work inside `.setStyle()` strings, so data-driven inline styles can reference design tokens instead of raw hex literals.
- **WebView must not live inside a ScrollPane**: JavaFX 21's `ScrollPane` wraps its viewport in an internal `CacheFilter`. When a `WebView` is inside that cached viewport, the WebKit renderer crashes with a `NullPointerException` in `WCPageBackBufferImpl` (RTTexture allocation failure). Likewise, any container with `-fx-effect: dropshadow(...)` as an ancestor of a `WebView` triggers the same crash via `NodeEffectInput`. Always make `WebView` a direct child of an effect-free, non-ScrollPane container; let the WebView scroll its content internally.
- **WebView anchor links with `loadContent()`**: When HTML is loaded via `WebEngine.loadContent()`, the page base URL is `about:blank`. Clicking `<a href="#anchor">` triggers a full navigation to `about:blank#anchor` instead of an in-page scroll. Fix by injecting a JavaScript `click` event listener that calls `preventDefault()` and `element.scrollIntoView()` instead.

### Testing Framework
No test framework or linter is configured.

## Architecture

### Startup Flow
`MainApp.start()` resolves `%APPDATA%\sanchay\app-config.json` (Tier 1 config), which contains the path to the user's data folder (Tier 2). If missing or invalid, `FirstRunWizard` is shown. Otherwise, `DataStore` and `PersistenceService` are initialized, then `MainWindow` launches.

### Key Singletons
- **`DataStore`** — Central in-memory cache of all financial data. All UI screens read/write through `DataStore.getInstance()`.
- **`PersistenceService`** — Serializes/deserializes 11 JSON files (accounts, transactions, recurring, categories, members, settings, import_mappings, category_rules, type_rules, loan_schedules, market_values) using GSON with a custom `LocalDate` adapter. Additional files owned by other services: `forecast_overrides.json` and `forecast_account_selection.json` by `ForecastStateService`; `plan_params.json` by `PlanParamsService`; `report_prefs.json` by `ReportPrefsService`.
- **`AppConfig`** — Manages the two-tier config separation (app install vs. data folder).

### Layers
```
ui/         → Screen classes, dialogs (JavaFX controllers)
service/    → Business logic (pure calculation, no DataStore mutation except PersistenceService)
model/      → Domain data classes (serialized to JSON)
```

### UI Shell
`MainWindow` uses a three-zone layout (top bar / sidebar / main panel) with a Floating Action Button (FAB) for adding transactions. FAB state (callback + context account) is carried by a `NavigationContext` value object created fresh on each navigation. `AccountsScreen` and `TransactionsScreen` receive `NavigationContext` instead of a `MainWindow` reference and write their FAB state into it; `MainWindow` reads it when the FAB is tapped. Screens are rebuilt on each navigation (not cached), except AccountsScreen context which is preserved.

`navigateTo()` creates a fresh `NavigationContext` at the top on every navigation — AccountsScreen re-populates it when needed. This prevents stale account context if the user opens the FAB after navigating away.

`SettingsScreen` is decoupled from `MainWindow` via a `BiConsumer<String, PreferencesSetupDialog.Result>` callback injected via constructor. `MainWindow` passes `this::reloadDataFolder`; `SettingsScreen` never holds a `MainWindow` reference.

### Dialog Utilities (`UiUtils`)
Shared dialog boilerplate is consolidated in `UiUtils`. Always use these instead of duplicating:

| Method                                                | Purpose                                                                  |
|-------------------------------------------------------|--------------------------------------------------------------------------|
| `initDialog(dlg, title, icon, width)`                 | Sets title, applies stylesheet, calls `setDialogHeader`, sets pref width |
| `initDialog(dlg, title, icon, width, height)`         | Same + sets pref height                                                  |
| `addSaveCancel(dialogPane)`                           | Adds Save + Cancel `ButtonType`s, returns the Save `ButtonType`          |
| `createDatePicker(initialDate)`                       | Creates a `DatePicker` with smart converter + styleOnShow wired          |
| `buildFormGrid(labelColWidth)`                        | Returns a standard 2-column `GridPane` (hgap=12, vgap=10)                |
| `addFormRow(grid, row, label, control)`               | Adds a label+control row to a `buildFormGrid` grid                       |
| `setDialogHeader(dlg, icon, heading)`                 | Sets the custom `DialogPane` header (icon box + heading label)           |
| `applyStylesheet(dialog)`                             | Copies main window stylesheets into a dialog's scene                     |
| `wireAutoComplete(comboBox, masterList)`              | Wires type-ahead filtering on an editable `ComboBox`                     |
| `wireDescriptionAutocomplete(textField, suggestions)` | Wires autocomplete on a description `TextField`                          |
| `styleOnShow(datePicker)`                             | Applies popup-open style class to a `DatePicker`                         |

### Dialog Classes
Each dialog has its own class. Extracted dialog classes by package:

**`ui/transactions/`**
- `TransactionDialog` — coordinator for all transaction types; delegates per-type UI/save/prefill to `*Panel` classes (see below)
- `TransactionStatsPanel` — account-type-specific stats header (balance, outstanding, market value); exposes `addToLayout(header, panel)` and `refresh()`
- `ImportOrchestrator` — full CSV-file and clipboard import flow; receives account and a `Runnable` refresh callback; exposes `doImportCsv()` and `doImportRows()`
- `TransactionContextMenu` — builds the source-indicator badge column (with merge/reconcile context menus) and the delete action column via `buildSrcCol()` and `buildActionsCol()`
- `ImportCompleteDialog` — read-only import result summary
- `ImportMappingDialog`, `AmbiguousMatchDialog`, `RecurringMatchDialog` — import workflow dialogs

**`ui/categories/`**
- `ReassignCategoryDialog` — reassign transactions from one category to another
- `MoveSubCategoryDialog` — move a sub-category to a different parent
- `CategoryTransactionsDialog` — read/edit transactions for a category
- `UncategorizedReviewDialog` — bulk-categorize uncategorized EXPENSE/INCOME/REFUND transactions; inline category+sub-category selectors per row, interim save, auto-closes when all rows are done

**`ui/accounts/`**
- `AccountDialog` — create/edit any account type
- `LoanScheduleDialog` — view amortization schedule for a loan
- `MarketValueHistoryDialog` / `RecordMarketValueDialog` — investment market value management

**`ui/recurring/`**
- `AddEditRecurringDialog` — create/edit recurring schedules; delegates investment fields to `InvestmentRecurringPanel` and auto-record settings to `AutoRecordSettingsPanel`
- `InvestmentRecurringPanel` — investment destination combo, type-hint label, and type-specific sub-fields (MF/Equity, FD/Bond, RD); exposes `applyTo()` for save
- `AutoRecordSettingsPanel` — auto-record checkbox + overdue-days spinner row; exposes `getAutoRecordDays()` for save
- `RecordRecurringDialog` — record an occurrence of a recurring transaction
- `SkipRecurringDialog` — skip a recurring occurrence

**`ui/planning/`**
- `MajorEventDialog` — add/edit a major life event

**`ui/common/`**
- `SingleInputDialog` — generic single text-field dialog
- `AccountCombos` — static `style(cb)` utility; applies name cell factory + button cell to any `ComboBox<? extends Account>`; use on every account ComboBox instead of inline cell factory
- `CategoryComboWiring` — static `wire(catCb, subCatCb, subMaster, ds)` and `styleSubCatCombo(cb)`; handles "└ name" display and the category→sub-category cascade listener; use instead of inline duplication
- `TransactionTableBuilder` — static `buildStandardColumns(ds, showAccount, showSubCategory)`; returns the standard DATE/DESCRIPTION/TYPE/ACCOUNT/SUB-CATEGORY/AMOUNT column list for `TableView<Transaction>`

**`ui/reports/`**
- `ReportsScreen` — thin coordinator; owns the `TabPane` and calls `refresh()` on each tab class on every navigation
- `ExpenseReportTab` — Expense Report tab: category-by-month chart and table, FY/CY year picker, category multi-select filter (persisted via `ReportPrefsService`), sub-category toggle, CSV export
- `ExpenseTrendTab` — Expense Trend tab: year-over-year net expense grid (EXPENSE minus REFUND) by category/sub-category; past years picker; category multi-select filter (persisted via `ReportPrefsService`); Total row; CSV export
- `CashFlowForecastTab` — Cash Flow Forecast tab: orchestrates chart, override table, and cash-flow table; delegates to `ForecastChartBuilder` and `ForecastOverridesPanel`
- `ForecastChartBuilder` — builds and updates the `LineChart` and custom legend for the cash-flow forecast; owns the chart node
- `ForecastOverridesPanel` — provides `AmountCell`, `ActionCell`, and `buildExcludedAwareCell()` for the forecast table; owns all override prompt dialogs
- `ForecastTableRow` — package-private record for forecast expense table rows
- `AccountSelectionDialog` — modal dialog for picking which accounts appear in the cash flow chart; up to 10 accounts in four labelled groups with tri-state group checkboxes; MF/Equity accounts shown as permanently disabled; includes "Show sum of all accounts" toggle

**`ui/help/`**
- `HelpScreen` — full Help & Support screen; renders USER-GUIDE.md via `MarkdownRenderer` inside a `ScrollPane`
- `MarkdownRenderer` — converts Markdown to a native JavaFX node tree (H1–H4, paragraphs, inline bold/code, lists, code blocks, horizontal rules); no WebView or javafx-web dependency

**`ui/wizard/`**
- `PreferencesSetupDialog` — first-run preferences (also shown when switching to an empty data folder)

### TransactionDialog Panel Architecture
`TransactionDialog` is a coordinator: it owns shared fields (type selector, date, description, amount, notes) and delegates all type-specific UI, save logic, and prefill logic to package-private `*Panel` classes in `ui/transactions/`:

| Panel class        | Transaction type(s)                                                                      |
|--------------------|------------------------------------------------------------------------------------------|
| `ExpensePanel`     | `EXPENSE`                                                                                |
| `IncomePanel`      | `INCOME`                                                                                 |
| `TransferPanel`    | `TRANSFER`                                                                               |
| `RefundPanel`      | `REFUND`                                                                                 |
| `CCPaymentPanel`   | `CC_PAYMENT`                                                                             |
| `LoanPaymentPanel` | `LOAN_PAYMENT` (includes prepayment detection and schedule recalculation)                |
| `RedeemPanel`      | `REDEEM`, `GAIN`, `LOSE`                                                                 |
| `InvestmentPanel`  | `INVESTMENT` (includes dynamic fields per investment type and the FD/Bond preview panel) |

Each panel receives a `TransactionDialog parent` reference in its constructor and accesses shared fields and helper methods through it. Panel fields are package-private so `TransactionDialog` can read them for auto-suggest routing, context-account pre-population, and focus management. To add a new transaction type: add a `*Panel` class, add it to `typeCb.getItems()`, wire `panelNodeFor()`, `save()`, `prefillFromTransaction()`, `applyContextAccount()`, `setContextAccount()`, and `focusFirstEmpty()` in `TransactionDialog`.

### Business Logic Services
- **`CashFlowProjectionService`** — Projects account balances month-by-month using recurring transactions, maturity events, overrides, and seasonality factors. Used by `CashFlowForecastTab`.
- **`ExpensePatternAnalyzer`** — Analyzes historical expense transactions to compute per-sub-category monthly averages, seasonal factors, and trend slope. Called by `CashFlowProjectionService`.
- **`ForecastStateService`** — Loads and saves forecast overrides (`forecast_overrides.json`) and account selection state (`forecast_account_selection.json`). Override uniqueness key is `(categoryId, subCategoryId, month)`.
- **`ImportService`** — Parses clipboard CSV/TSV, auto-detects delimiters, matches imports to existing transactions, and auto-categorizes via rules.
- **`AmortizationService`** — Generates reducing-balance loan schedules; handles mid-loan interest rate changes.
- **`PlanParamsService`** — Loads and saves financial planning parameters to `plan_params.json`.
- **`ReportPrefsService`** — Loads and saves hidden-category selections for Expense Report and Expense Trend tabs to `report_prefs.json`.

### Data Model Conventions
- **Amounts are stored in paise** (long integers, 1/100th of the base currency unit) — never use floating point for money.
- All entities use `UUID.randomUUID()` for IDs.
- Transactions carry a `Source` enum (`MANUAL`, `IMPORTED`, `AUTO_CATEGORIZED`, `RECONCILED`) tracking their origin.
- `Account` is an abstract base; concrete subtypes are `BankAccount`, `CreditCardAccount`, `LoanAccount`, `InvestmentAccount`.
- `Transaction.Type` values include `EXPENSE`, `INCOME`, `TRANSFER`, `INVESTMENT`, `CC_PAYMENT`, `REFUND`, `REDEEM`, `LOAN_PAYMENT`, `GAIN`, `LOSE`.

### Configuration Hierarchy
1. **Tier 1** — `%APPDATA%\sanchay\app-config.json`: points to the data folder and stores app version/build name.
2. **Tier 2** — User-chosen data folder: contains all JSON data files plus `settings.json` (currency, date format, active year).