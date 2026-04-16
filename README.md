# Sanchay — Personal Finance

**Sanchay** (meaning *savings* in Hindi) is a desktop application for comprehensive personal finance management. It covers day-to-day transaction tracking, investment and loan management, recurring schedule automation, CSV import and reconciliation, cash flow forecasting, and long-range retirement planning — all stored locally as JSON files with no cloud dependency.

- **Platform:** Windows 11+
- **Language:** Java 17
- **GUI Framework:** JavaFX 21.0.10
- **Version:** v0.0.3-Myna

---

## Table of Contents

1. [Features](#features)
2. [Prerequisites](#prerequisites)
3. [Building & Running](#building--running)
4. [First Run](#first-run)
5. [Data Storage](#data-storage)
6. [Screens & Functionality](#screens--functionality)
   - [Dashboard](#dashboard)
   - [Accounts](#accounts)
   - [Transactions](#transactions)
   - [Recurring](#recurring)
   - [Reports & Cash Flow Forecast](#reports--cash-flow-forecast)
   - [Financial Planning](#financial-planning)
   - [Categories](#categories)
   - [Profile](#profile)
   - [Settings](#settings)
   - [Help & Support](#help--support)
7. [CSV Import](#csv-import)
8. [Architecture Overview](#architecture-overview)
9. [Configuration Files](#configuration-files)
10. [Dependencies](#dependencies)

---

## Coding Commandments

### UI Styling
1. Never hardcode colours — define tokens, reference tokens.                                                                                                                                                                      
   All colour values belong in .root as looked-up colour variables. Every rule in every file references tokens, never hex literals.
2. No inline styles. Ever (almost).                       
   .setStyle(), .setFont(), .setTextFill(), .setBackground() in Java, and style= in FXML are banned. Style belongs in CSS. The only justified exceptions are values computed at runtime from data — and those must carry a comment explaining why.
3. When an exception exists, document it.
   If an inline style is genuinely necessary, add a comment: "Inline required: colour computed from live data at runtime." Undocumented exceptions are just debt in disguise.
4. Style classes belong to components, not screens.
   Shared styles go in components.css. If screen A uses a class defined in screen B's CSS file, that class is in the wrong place. No screen may import another screen's CSS.
5. Name things by meaning, not appearance.
   -color-error is a good token. -red is a bad token. The colour might change; the meaning won't.
6. Consolidate near-duplicates ruthlessly.
   #333, #333333, #343434 are the same colour expressed through copy-paste drift. Find them, pick one, delete the rest.
7. Always define all interactive states in CSS.
   If you style a button's normal state, you must also define :hover, :pressed, :focused, and :disabled. Forgetting any of them causes jarring visual reversion to the default theme on interaction.
8. Define a spacing scale and stick to it.
   Padding and spacing values scattered as raw numbers (8 here, 10 there, 12 somewhere else) without design intent are magic numbers. Define a consistent spacing scale and reference it uniformly.
9. Understand what you're overriding before you override it.
   Built-in JavaFX looked-up colours like -fx-base cascade silently to buttons, scrollbars, and menus. Touching them on .root changes the whole app. Be intentional — know the blast radius before setting any root-level property.
10. Specificity is a silent saboteur.
    An inline .setStyle() call always beats a CSS class rule. If you add a CSS class but forget to remove the old inline style, the inline style wins invisibly — the screen looks fine until  someone removes it later and everything breaks unexpectedly.

### Functional Coding
1. Every dialog gets its own class.
   Inline dialog code inside a parent screen is structural debt. Each dialog gets its own class, receives input via constructor/setters, and returns output via its result — never by reaching into or writing back to the parent.
2. Services must not know about the UI.
   If a service imports a javafx.* type (Stage, Node, Alert, etc.), that's a layering violation. Extract the UI interaction behind a callback or interface that the controller provides; the service defines the contract, the UI
   fulfils it.
3. Never use static mutable state to pass data between screens.
   Static fields for inter-screen data transfer is a common JavaFX antipattern. Replace with explicit constructor injection, a shared model, or a lightweight navigation service.
4. Shared logic belongs in utilities, not in whichever controller happened to have it first.
   Duplicated code that moves to a utility must land in the right layer: pure logic → XxxUtils, UI helpers → ViewUtils/UIHelper, business logic → a service class. Never park shared logic in a UI controller.
5. Minimise visibility.
   Every method and field should be as private as possible. private by default, package-private when needed within a package, public only when genuinely required by an outside caller. Overly broad access modifiers are implicit coupling — they invite misuse.
6. Standardise error handling across all screens.
   Mixing alert dialogs on some screens with inline error labels on others is inconsistency that confuses users and complicates maintenance. Pick one pattern per error category and apply it everywhere.


## Features

- **Favourite accounts** — pin any account to a Favourites section at the top of the Accounts screen for quick access
- **Multi-account tracking** — bank (savings/current), credit card, loan, and investment accounts in one place
- **Investment types** — Mutual Funds, Equity, Debt/Bonds, Fixed Deposits, Recurring Deposits, Provident Fund
- **Loan amortization** — auto-generated reducing-balance schedules with support for mid-loan rate changes
- **Recurring schedules** — monthly, quarterly, half-yearly, annual, and alternate-year with optional auto-recording
- **CSV import & reconciliation** — two-pass deduplication that merges imported rows with existing manual entries, auto-categorization via rules
- **Cash flow forecasting** — month-by-month balance projection using scheduled transactions, FD/RD maturities, and AI-analysed expense patterns; up to 36-month horizon with per-account selection and override corrections
- **Expense reporting** — category-by-month breakdown with multi-select category filter, sub-category drill-down, and CSV export
- **Retirement planning** — corpus projections, major life event tracking, expense forecasting
- **Family member tracking** — attribute transactions and income to specific members
- **Portable data** — all data lives in a user-chosen folder; move to a cloud drive or external disk at any time
- **Backup** — one-click timestamped ZIP archive of the entire data folder

---

## Prerequisites

| Requirement | Version |
|---|---|
| JDK | 17 or later |
| JavaFX SDK | 21.0.10 (provided via Maven; must be on module path at runtime) |
| Maven | 3.8+ |

---

## Building & Running

```bash
# Run directly with Maven (development)
mvn javafx:run

# Run with remote debugger attached on port 5005 (suspends on start)
mvn javafx:run -Pdebug

# Build a fat JAR
mvn clean package
# Output: target/sanchay-app.jar

# Or just repackage the shade JAR without full rebuild
mvn shade:shade
```

The fat JAR excludes JavaFX modules (they are provided by the runtime). To run the JAR outside Maven you need a JDK with JavaFX modules on the module path:

```bash
java --module-path /path/to/javafx/lib \
     --add-modules javafx.controls,javafx.fxml,javafx.graphics \
     -jar target/sanchay-app.jar
```

---

## First Run

On first launch, a setup wizard collects two things:

1. **Data folder** — where all your JSON data files will be stored (defaults to `~/sanchay-data`; can be a cloud-synced folder)
2. **Preferences** — date format (DD/MM/YYYY or YYYY-MM-DD), year format (Financial Year or Calendar Year), and base currency

The wizard writes `%APPDATA%\sanchay\app-config.json` (the "Tier 1" config) which points to your chosen data folder. Subsequent launches skip the wizard and go directly to the main window.

---

## Data Storage

Sanchay uses a two-tier configuration system.

**Tier 1 — App config** (`%APPDATA%\sanchay\app-config.json`)

```json
{
  "dataFolderPath": "C:\\Users\\You\\OneDrive\\sanchay-data",
  "appVersion": "v1.0.0",
  "appBuild": "Agami"
}
```

This file contains only the pointer to your data folder. You can change the data folder from Settings without losing any data.

**Tier 2 — Data folder** (user-chosen path)

| File | Contents |
|---|---|
| `accounts.json` | All account records |
| `transactions.json` | All transactions |
| `recurring.json` | Recurring schedules |
| `categories.json` | Category tree |
| `members.json` | Family members |
| `settings.json` | Date/currency/year format, UI state |
| `import_mappings.json` | Saved CSV column mappings per bank |
| `category_rules.json` | Auto-categorization rules |
| `type_rules.json` | Transaction-type detection rules |
| `loan_schedules.json` | Generated amortization schedules |
| `market_values.json` | Investment market value snapshots |
| `plan_params.json` | Financial planning parameters |
| `forecast_overrides.json` | Manual amount corrections and exclusions applied to the cash flow forecast |
| `forecast_account_selection.json` | Persisted account selection and "show sum" toggle for the forecast chart |
| `report_prefs.json` | Persisted hidden-category selections for Expense Report and Expense Trend tabs |

All monetary amounts are stored as **long integers in paise** (1/100th of the base currency unit) to avoid floating-point rounding errors.

---

## Screens & Functionality

### Dashboard

The home screen. Shows an at-a-glance summary of account balances and recent activity.

---

### Accounts

Lists all accounts grouped by type. Each account card shows the current balance and key metadata.

**Favourites** — a collapsible section at the top of the screen that collects accounts of any type you have starred. Click the ☆ icon inline with an account name to add it; click ★ to remove it. The Favourites section is expanded by default on first launch; all type sections (Bank, Credit Cards, etc.) start collapsed. Expand/collapse state is remembered for the rest of the session.

**Account types and subtypes:**

| Type | Subtypes / Options |
|---|---|
| Bank Account | Savings, Current |
| Credit Card | Active, Blocked, Cancelled |
| Loan | Home, Vehicle, Personal |
| Investment | Mutual Funds, Equity, Debt/Bonds, Fixed Deposit, Recurring Deposit, Provident Fund |

**Loan accounts** can store an amortization schedule (auto-generated from principal, rate, tenure, and disbursement date). Mid-loan interest rate changes are recorded as `LoanRateChange` entries and the schedule is regenerated accordingly, with prepayment applied either as Reduce Tenure or Reduce EMI.

**Investment accounts** support market value snapshots (`MarketValueEntry`) so you can track NAV or current market price over time separately from the book value.

---

### Transactions

Opened by clicking into an account. Shows all transactions for that account with filter and sort controls.

**Transaction types:**

| Type | Description |
|---|---|
| EXPENSE | Money leaving a bank or credit card |
| INCOME | Money entering a bank account |
| TRANSFER | Movement between two bank accounts |
| INVESTMENT | Funds deployed into an investment account |
| CC_PAYMENT | Bank-to-credit-card bill payment |
| REFUND | Money returned to account; offsets the original expense category |
| REDEEM | Investment redemption: principal returned with separate GAIN/LOSE transaction |
| LOAN_PAYMENT | EMI payment from bank to loan account |
| GAIN | System-generated when a REDEEM produces a profit |
| LOSE | System-generated when a REDEEM produces a loss |

**Payment modes:** UPI, Net Banking, Debit Card, Credit Card, Cash, Cheque, Auto-Debit, Internal Transfer

**Transaction sources** track how a record entered the system:

| Source | Badge | Meaning |
|---|---|---|
| MANUAL | M | Entered by the user. Right-click → Mark as Reconciled (for accounts without CSV import) |
| IMPORTED | I | Brought in by CSV import; category not yet confirmed. Right-click → Merge with existing manual transaction |
| AUTO_CATEGORIZED | ? | Imported and automatically categorized by a rule; left-click to accept, right-click to merge with an existing manual transaction |
| RECONCILED | R | Verified — either matched with an import or explicitly marked by the user |

The **"Show pending review only"** filter surfaces all MANUAL, IMPORTED, and AUTO_CATEGORIZED transactions so unreviewed entries from any source are visible in one place.

---

### Recurring

Manages scheduled transactions that repeat on a fixed frequency.

**Frequencies:** Monthly, Quarterly, Half-Yearly, Annually, Alternate Year

**Status:** Active, Paused, Completed

Each schedule specifies:
- Transaction type, amount (0 = variable/reminder only), category
- Source and destination accounts
- Due day of month (1–28)
- Start and optional end date
- Optional payment count cap (`numberOfPayments`)
- Auto-record threshold: if set, the app records the transaction automatically N days after the due date passes without manual action

**Investment schedules** (FD, RD, MF, Equity, Bonds) carry additional fields: interest rate, maturity date, expected maturity amount, scheme/script name, units/NAV info.

Recurring entries are auto-created by the system when you set up an RD, loan with EMI, or credit card account.

---

### Reports & Cash Flow Forecast

The Reports screen has three tabs, each managed by its own class (`ExpenseReportTab`, `ExpenseTrendTab`, `CashFlowForecastTab`). `ReportsScreen` is a thin coordinator that owns the `TabPane` and delegates all rendering and refresh logic to the tab classes.

---

#### Expense Report tab

Category-by-month breakdown of spending.

**Filters:**
- **Year selector** — Financial Year or Calendar Year picker, defaulting to the current FY/year. Format follows the app's Year Format setting.
- **Month selector** — optional single-month drill-down within the selected year
- **Category filter** — multi-select `MenuButton` that hides/shows categories in both the chart and the table; the menu stays open after each toggle for multiple selections. Selection is persisted to `report_prefs.json`.
- **Show sub-categories** — checkbox in the chart section header that expands each category bar to show per-sub-category breakdown in the table

**Export:** **⬇ Download CSV** saves the current view (selected year/month, active category filter, sub-category state) as a `.csv` file via a file-save dialog.

---

#### Expense Trend tab

Year-over-year comparison of net expenses (EXPENSE minus REFUND) in a tabular grid.

**Columns:** one per year — current year plus up to 4 past years (selected via the Past Years picker).

**Rows:** expense categories alphabetically, sub-categories indented below each parent, and a bold **TOTAL** row at the bottom. Zero amounts are shown as blank.

**Category filter** — same multi-select `MenuButton` as Expense Report; selection is persisted to `report_prefs.json`.

**Year format** follows the app's Year Format setting (Indian Financial Year or Calendar Year).

**Export:** **⬇ Download CSV** exports the current filtered view including the Total row.

---

#### Cash Flow Forecast tab

Projects account balances month-by-month over a user-selected time horizon.

**Horizon options:** Next 6 Months, Next 12 Months, Next 24 Months, Next 36 Months, or the current Financial Year.

**Account selection** — a **Choose Accounts** dialog (`AccountSelectionDialog`) lets you pick up to 10 accounts to display on the chart. Accounts are presented in four labelled groups (Bank, Credit Cards, Loans, Investments) with tri-state group header checkboxes. MF and Equity investment accounts appear in the Investments group as permanently disabled rows — visual cue that they are excluded from projection. A **"Show sum of all accounts"** toggle controls whether the thick gold total line appears on the chart. Selection and the show-sum toggle are persisted to `forecast_account_selection.json`.

**What the projection models:**
- Recurring scheduled transactions (income and expenses)
- FD and RD maturity events (principal + interest credited on maturity date)
- Historical expense patterns with seasonality and trend factors
- AI-generated expense forecasts per sub-category (with confidence scores)
- Discretionary spending subtracted from recurring totals to avoid double-counting

**Overrides** — double-click any forecast row in the table to correct a projected amount for a specific month. Corrections are persisted to `forecast_overrides.json` and applied on the next projection run.

**Chart features:**
- Data point tooltips show series name, month, and balance on hover
- Series legend tooltips show the full account name

Only bank and eligible investment accounts (FD, RD, Debt/Bonds) are included in the balance projection. Equity, MF, and PF accounts are excluded as their balances are market-linked.

---

### Financial Planning

Long-range retirement and wealth planning screen.

**KPI cards** at the top show:
- Current age (derived from "Self" family member's date of birth)
- Years to retirement and years in retirement
- Retirement age, projected future earnings, and forecasted corpus

**Plan Parameters** (auto-saved on field blur):

| Parameter | Default |
|---|---|
| Retirement Age | 60 |
| Life Expectancy | 80 |
| Pre-Retirement Tax % | 30% |
| Post-Retirement Tax % | 20% |
| Rate of Return — Equities | 12% |
| Rate of Return — Mutual Funds | 10% |
| Rate of Return — PF | 8.1% |
| Rate of Return — Post-Retire | 7% |
| Inflation | 6% |
| Monthly Cost of Living | ₹1,50,000 |
| Monthly SIP — MF | ₹10,000 |
| Monthly SIP — Equity | ₹5,000 |

**Major Events** — a list of significant future expenses (home purchase, wedding, education, etc.) with:
- Planned date and estimated amount
- Actual amount (auto-populated from matching transactions once incurred)
- Forecast vs. actual comparison

**Corpus and earnings breakdowns** show projected wealth split across asset classes (Bank, Equity, MF, Bonds, FD, RD, PF) and projected total earnings from all sources.

**Post-retirement corpus table** projects year-by-year balance drawdown using a three-phase spending model:

| Phase | Age range | Effective inflation |
|---|---|---|
| Active Retirement | Retirement age – 72 | Full inflation |
| Slow-go Years | 73 – 82 | Inflation − 1.5% |
| Healthcare Phase | 83+ | Full inflation |

Phase-separator rows in the table mark each transition with the effective rate. The same model drives the Required Corpus calculation.

---

### Categories

Manages the hierarchical category tree used to classify transactions. Categories are typed as EXPENSE or INCOME and support one level of sub-categories. Used by auto-categorization rules and throughout reporting.

---

### Profile

Manages family members and their income sources.

- Add family members with name, relationship, and date of birth
- The member tagged as "Self" is used for age calculations in Financial Planning
- Attach `EarningSource` records to members (salary, freelance, rental income, etc.) for income forecasting

---

### Settings

| Section | Options |
|---|---|
| Data Folder | Change path; app reloads immediately with new folder |
| Date Format | DD/MM/YYYY, YYYY-MM-DD |
| Currency | INR (currently the only option) |
| Year Format | Indian Financial Year (Apr–Mar), Calendar Year (Jan–Dec) |
| Expense Forecast Window | 3, 6, 12, 18, or 24 months of history used for forecasting |
| Backup | Creates a timestamped ZIP: `Sanchay_data_backup_yyyyMMdd_HHmmss.zip` |

---

### Help & Support

Full in-app reference rendered from `USER-GUIDE.md`. Shows the same dismissable Get Started banner as the Dashboard on first open. The user guide is rendered as styled HTML (headings, tables, lists, blockquotes, code blocks) with a working Table of Contents — clicking any TOC entry scrolls directly to that section.

---

## CSV Import

The import workflow supports pasting CSV or tab-delimited text directly from a bank statement, or loading a file via file dialog.

### Column Mapping

On first import from a new bank, you map columns to the roles Sanchay expects:

- **Date column** — transaction date
- **Amount column** — single column (positive/negative) or separate Debit/Credit columns
- **Description column** — narration or merchant name

Mappings are saved per bank name so subsequent imports are automatic.

### Import Processing Pipeline

1. **Delimiter detection** — comma vs. tab auto-detected from clipboard content
2. **Header detection** — first row identified automatically
3. **Parsing** — dates parsed using the app's configured date format; amounts normalised (strips currency symbols, handles `Dr.`/`Cr.` suffixes, comma thousands separators)
4. **Deduplication** — each row is SHA-256 hashed on `date|amount|description`; rows whose hash already exists in the database are silently skipped
5. **Two-pass reconciliation:**
   - **Pass 1:** Identify candidate matches between each CSV row and existing manual transactions (same date ±0 days, same amount, description similarity)
   - **Pass 2:** Detect contested manual transactions (matched by two or more CSV rows)
   - **Pass 3:** Commit results based on match count
6. **Match outcomes:**
   - No match → new IMPORTED or AUTO_CATEGORIZED transaction
   - One uncontested match → silently RECONCILED (CSV date/amount/hash written back; manual category preserved)
   - Contested or multiple matches → shown in the **Ambiguous Match Dialog** for manual resolution
7. **Recurring reconciliation** — CSV rows are also matched against pending recurring schedule occurrences (±2-day tolerance, description similarity ≥ 0.3)
8. **Auto-categorization** — `TypeRule` and `CategoryRule` entries are applied to unmatched imports to suggest or set category/type automatically

### Credit Card Imports

For credit card statements, CR (credit) entries are classified as:
- **CC_PAYMENT** if the description matches patterns: `PAYMENT`, `THANK YOU`, `NEFT CR`, `NACH CR`, `UPI CR`, `RTGS`, `IMPS CR`
- **REFUND** otherwise (treated as expense offset)

### Merge Strategy

When a CSV row reconciles with a manual transaction:
- **CSV wins:** date, amount, import hash
- **Manual wins:** category, sub-category, family member assignment
- **Description:** bank description appended to notes if it differs from the manual description
- **Source indicator** updated to `RECONCILED`

---

## Architecture Overview

```
MainApp (JavaFX Application)
│
├── AppConfig          ─ Two-tier config (APPDATA pointer → data folder)
├── DataStore          ─ Singleton in-memory cache of all entities
├── PersistenceService ─ GSON-based JSON read/write for all 12 data files
│
├── UI Layer (ui/)
│   ├── MainWindow     ─ Shell: sidebar nav + top bar + main panel + FAB
│   ├── UiUtils        ─ Shared dialog helpers (stylesheet, initDialog, error, etc.)
│   └── [screen packages]/
│       ├── dashboard, accounts, transactions, recurring
│       ├── reports, planning, categories, profile, settings
│       └── wizard (first-run)
│
└── Service Layer (service/)
    ├── CashFlowProjectionService  ─ Month-by-month balance forecasting
    ├── ImportService              ─ CSV parse, dedup, reconcile, auto-categorize
    ├── AmortizationService        ─ Loan schedule generation
    ├── ForecastStateService       ─ Forecast overrides + account selection persistence
    ├── PlanParamsService          ─ Financial planning parameter persistence
    ├── DescriptionNormalizer      ─ Transaction description normalisation
    ├── MoneyFormatter             ─ Amount and currency symbol formatting
    └── CurrencyConfig             ─ Currency metadata
```

### TransactionDialog Panel Architecture

`TransactionDialog` is a coordinator. The form logic for each transaction type lives in a dedicated package-private panel class in `ui/transactions/`:

| Panel class | Transaction type |
|---|---|
| `ExpensePanel` | EXPENSE |
| `IncomePanel` | INCOME |
| `TransferPanel` | TRANSFER |
| `RefundPanel` | REFUND |
| `InvestmentPanel` | INVESTMENT |
| `CCPaymentPanel` | CC_PAYMENT |
| `LoanPaymentPanel` | LOAN_PAYMENT |
| `RedeemPanel` | REDEEM |

Each panel receives a `TransactionDialog parent` reference in its constructor and accesses shared fields (`ds`, `typeCb`, `sharedDate`, `sharedAmt`, etc.) and helper methods (`requireDate()`, `parsePaise()`, `persistTransaction()`, etc.) directly — all package-private to avoid getter boilerplate. Each panel implements three responsibilities: `getNode()` (build the form section), `save()` (validate and write to DataStore), and `prefill(Transaction)` (populate fields when editing).

**Key design decisions:**

- **Singleton DataStore** — all screens share one in-memory cache; there is no direct database. All mutations go through `DataStore`, which calls `PersistenceService` to flush JSON.
- **Amounts in paise** — every monetary value is a `long` in the smallest currency unit. Formatting is only applied in the UI layer via `MoneyFormatter`.
- **Pure calculation services** — `CashFlowProjectionService` and `AmortizationService` read from DataStore but never mutate it; they return result objects.
- **Screen rebuild on navigation** — screens are not cached between navigations (except the account context passed to the FAB). This keeps memory low and avoids stale-state bugs.
- **Two-tier config** — separating the app install config from the data folder makes it straightforward to share a data folder between machines via a cloud drive, or to move the data folder without touching the application.

---

## Configuration Files

### `%APPDATA%\sanchay\app-config.json`

Written by the app on first run and when the data folder is changed in Settings. Do not edit manually unless moving to a new data folder path.

```json
{
  "dataFolderPath": "/path/to/your/sanchay-data",
  "appVersion": "v1.0.0",
  "appBuild": "Agami"
}
```

### `<data-folder>/settings.json`

Stores user preferences and UI state (date format, year format, currency, sidebar collapse states, active year). Managed entirely through the Settings screen.

---

## Dependencies

| Dependency | Group | Version | Purpose |
|---|---|---|---|
| javafx-controls | org.openjfx | 21.0.10 | UI controls |
| javafx-fxml | org.openjfx | 21.0.10 | FXML support |
| javafx-graphics | org.openjfx | 21.0.10 | Rendering |
| javafx-web | org.openjfx | 21.0.10 | WebView for in-app HTML rendering |
| gson | com.google.code.gson | 2.10.1 | JSON serialization |

**Maven plugins:**

| Plugin | Version | Purpose |
|---|---|---|
| javafx-maven-plugin | 0.0.8 | `mvn javafx:run` |
| maven-shade-plugin | 3.5.1 | Fat JAR (`sanchay-app.jar`) |

---

*Developer: Girish Tharwani*