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