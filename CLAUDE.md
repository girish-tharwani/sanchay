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
- **`PersistenceService`** — Serializes/deserializes 11 JSON files (accounts, transactions, recurring, categories, members, settings, import_mappings, category_rules, type_rules, loan_schedules, market_values) using GSON with a custom `LocalDate` adapter.
- **`AppConfig`** — Manages the two-tier config separation (app install vs. data folder).

### Layers
```
ui/         → Screen classes, dialogs (JavaFX controllers)
service/    → Business logic (pure calculation, no DataStore mutation except PersistenceService)
model/      → Domain data classes (serialized to JSON)
```

### UI Shell
`MainWindow` uses a three-zone layout (top bar / sidebar / main panel) with a Floating Action Button (FAB) for adding transactions. It manages `postTransactionCallback` and `transactionContextAccount` so the FAB knows which account context to use. Screens are rebuilt on each navigation (not cached), except AccountsScreen context which is preserved.

`navigateTo()` clears both FAB fields unconditionally at the top on every navigation — AccountsScreen re-sets them when needed. This prevents stale account context if the user opens the FAB after navigating away.

`SettingsScreen` is decoupled from `MainWindow` via a `BiConsumer<String, PreferencesSetupDialog.Result>` callback injected via constructor. `MainWindow` passes `this::reloadDataFolder`; `SettingsScreen` never holds a `MainWindow` reference.

### Dialog Utilities (`UiUtils`)
Shared dialog boilerplate is consolidated in `UiUtils`. Always use these instead of duplicating:

| Method | Purpose |
|---|---|
| `initDialog(dlg, title, icon, width)` | Sets title, applies stylesheet, calls `setDialogHeader`, sets pref width |
| `initDialog(dlg, title, icon, width, height)` | Same + sets pref height |
| `addSaveCancel(dialogPane)` | Adds Save + Cancel `ButtonType`s, returns the Save `ButtonType` |
| `createDatePicker(initialDate)` | Creates a `DatePicker` with smart converter + styleOnShow wired |
| `buildFormGrid(labelColWidth)` | Returns a standard 2-column `GridPane` (hgap=12, vgap=10) |
| `addFormRow(grid, row, label, control)` | Adds a label+control row to a `buildFormGrid` grid |
| `setDialogHeader(dlg, icon, heading)` | Sets the custom `DialogPane` header (icon box + heading label) |
| `applyStylesheet(dialog)` | Copies main window stylesheets into a dialog's scene |
| `wireAutoComplete(comboBox, masterList)` | Wires type-ahead filtering on an editable `ComboBox` |
| `wireDescriptionAutocomplete(textField, suggestions)` | Wires autocomplete on a description `TextField` |
| `styleOnShow(datePicker)` | Applies popup-open style class to a `DatePicker` |

### Dialog Classes
Each dialog has its own class. Extracted dialog classes by package:

**`ui/transactions/`**
- `TransactionDialog` — coordinator for all transaction types; delegates per-type UI/save/prefill to `*Panel` classes (see below)
- `ImportCompleteDialog` — read-only import result summary
- `ImportMappingDialog`, `AmbiguousMatchDialog`, `RecurringMatchDialog` — import workflow dialogs

**`ui/categories/`**
- `ReassignCategoryDialog` — reassign transactions from one category to another
- `MoveSubCategoryDialog` — move a sub-category to a different parent
- `CategoryTransactionsDialog` — read/edit transactions for a category

**`ui/accounts/`**
- `AccountDialog` — create/edit any account type
- `LoanScheduleDialog` — view amortization schedule for a loan
- `MarketValueHistoryDialog` / `RecordMarketValueDialog` — investment market value management

**`ui/recurring/`**
- `AddEditRecurringDialog` — create/edit recurring schedules
- `RecordRecurringDialog` — record an occurrence of a recurring transaction
- `SkipRecurringDialog` — skip a recurring occurrence

**`ui/planning/`**
- `MajorEventDialog` — add/edit a major life event

**`ui/common/`**
- `SingleInputDialog` — generic single text-field dialog

**`ui/help/`**
- `HelpScreen` — full Help & Support screen; WebView renders USER-GUIDE.md via `MarkdownConverter`; must not be wrapped in a ScrollPane (see JavaFX CSS Gotchas)
- `MarkdownConverter` — line-by-line Markdown→HTML converter with embedded CSS matching the app's design tokens

**`ui/wizard/`**
- `PreferencesSetupDialog` — first-run preferences (also shown when switching to an empty data folder)

### TransactionDialog Panel Architecture
`TransactionDialog` is a coordinator: it owns shared fields (type selector, date, description, amount, notes) and delegates all type-specific UI, save logic, and prefill logic to package-private `*Panel` classes in `ui/transactions/`:

| Panel class | Transaction type(s) |
|---|---|
| `ExpensePanel` | `EXPENSE` |
| `IncomePanel` | `INCOME` |
| `TransferPanel` | `TRANSFER` |
| `RefundPanel` | `REFUND` |
| `CCPaymentPanel` | `CC_PAYMENT` |
| `LoanPaymentPanel` | `LOAN_PAYMENT` (includes prepayment detection and schedule recalculation) |
| `RedeemPanel` | `REDEEM`, `GAIN`, `LOSE` |
| `InvestmentPanel` | `INVESTMENT` (includes dynamic fields per investment type and the FD/Bond preview panel) |

Each panel receives a `TransactionDialog parent` reference in its constructor and accesses shared fields and helper methods through it. Panel fields are package-private so `TransactionDialog` can read them for auto-suggest routing, context-account pre-population, and focus management. To add a new transaction type: add a `*Panel` class, add it to `typeCb.getItems()`, wire `panelNodeFor()`, `save()`, `prefillFromTransaction()`, `applyContextAccount()`, `setContextAccount()`, and `focusFirstEmpty()` in `TransactionDialog`.

### Business Logic Services
- **`CashFlowProjectionService`** — Projects account balances month-by-month using recurring transactions, maturity events, overrides, and seasonality factors. Used by Reports/Forecasting screens.
- **`ImportService`** — Parses clipboard CSV/TSV, auto-detects delimiters, matches imports to existing transactions, and auto-categorizes via rules.
- **`AmortizationService`** — Generates reducing-balance loan schedules; handles mid-loan interest rate changes.

### Data Model Conventions
- **Amounts are stored in paise** (long integers, 1/100th of the base currency unit) — never use floating point for money.
- All entities use `UUID.randomUUID()` for IDs.
- Transactions carry a `Source` enum (`MANUAL`, `IMPORTED`, `AUTO_CATEGORIZED`, `RECONCILED`) tracking their origin.
- `Account` is an abstract base; concrete subtypes are `BankAccount`, `CreditCardAccount`, `LoanAccount`, `InvestmentAccount`.
- `Transaction.Type` values include `EXPENSE`, `INCOME`, `TRANSFER`, `INVESTMENT`, `CC_PAYMENT`, `REFUND`, `REDEEM`, `LOAN_PAYMENT`, `GAIN`, `LOSE`.

### Configuration Hierarchy
1. **Tier 1** — `%APPDATA%\sanchay\app-config.json`: points to the data folder and stores app version/build name.
2. **Tier 2** — User-chosen data folder: contains all JSON data files plus `settings.json` (currency, date format, active year).