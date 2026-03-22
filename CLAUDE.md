# Sanchay — Personal Finance App (CLAUDE.md)

## What this is
A desktop personal finance management app for Windows, targeting Indian households. Single-user, self-contained — no server or cloud required. All data stored as local JSON files. Packaged as a Windows `.exe` via `jpackage`.

## Tech stack
- **Language:** Java 17, JavaFX 21 (no FXML — UI built programmatically)
- **JSON:** Gson 2.10.1
- **Build:** Maven (`mvn javafx:run` to run, `build.sh` for packaging)
- **Entry point:** `com.sanchay.MainApp`

## Package structure
```
com.sanchay/
  MainApp.java
  model/          — Account, BankAccount, CreditCardAccount, LoanAccount,
                    InvestmentAccount, Transaction, RecurringTransaction,
                    Category, FamilyMember, ImportMapping, CategoryRule
  service/        — DataStore (singleton), PersistenceService, AppConfig,
                    ImportService
  ui/
    MainWindow.java
    wizard/       — FirstRunWizard
    dashboard/    — DashboardScreen
    accounts/     — AccountsScreen, ImportMappingDialog, AmbiguousMatchDialog
    transactions/ — TransactionDialog
    recurring/    — RecurringScreen
    reports/      — ReportsScreen
    categories/   — CategoriesScreen
    profile/      — ProfileScreen, EarningsDialog
    settings/     — SettingsScreen
  resources/
    css/app.css
    version.properties
```

## Data model
All data lives in a user-chosen folder (path stored in `%APPDATA%\sanchay\app-config.json`):

| File | Contents |
|------|----------|
| `accounts.json` | All accounts (bank, CC, loan, investment) |
| `transactions.json` | All transactions |
| `recurring.json` | Recurring schedules |
| `categories.json` | Expense + income categories with sub-categories |
| `members.json` | Family members |
| `settings.json` | App preferences |
| `import_mappings.json` | Per-account CSV column mappings |

**Key data rules:**
- All amounts in **paise** (integer), displayed in INR
- Dates as ISO 8601 strings (`YYYY-MM-DD`)
- Every record has a UUID primary key
- Financial year = April–March (Indian tax year)
- **Save-on-mutation:** every change rewrites the relevant JSON file atomically (write `.tmp` then rename)

## Transaction types
`Expense`, `Income`, `Transfer`, `Refund`, `Investment`, `CC Payment`

## Account types
- **Bank:** Savings, Current
- **Credit Card:** liability account; CC Payment reduces outstanding
- **Loan:** Home, Vehicle, Personal — outstanding = opening balance minus all Transfer payments
- **Investment:** bucket model (All Mutual Funds, All Equities, All Bonds seeded by default)

## Recurring transactions
- Never auto-post — always require user confirmation (Record / Skip)
- Auto-created when saving: Loan account → EMI schedule; CC account → payment reminder; RD account → instalment transfer
- Pending instances shown on Dashboard (due ≤7 days or overdue)

## Key UI rules (binding)
- **No hover highlight** on table rows
- **Row selection:** must have sufficient contrast — no light-blue-bg + white-text
- **Dialog field labels:** dark text only (e.g. `#1A1A2E`) — never white on grey
- **Dashboard tiles:** all amounts in one consistent colour — no per-tile colour coding
- **Pending Recurring widget:** subtle background only (`#F5F5F5`); type tags fixed-width
- **Text in wizard:** never clipped or truncated — use word wrap

## Navigation
Left sidebar (fixed, non-collapsible): Dashboard → Accounts → Recurring → Reports → Categories | Profile → Settings. No standalone Transactions screen — accessed via account card → Transactions button. Floating `+` button on every screen opens the New Transaction dialog.

## Current version
`0.1.0-SNAPSHOT` (pom.xml)

## Out of scope (initial version)
Cloud sync, PDF/OFX import, push notifications, loan amortization schedule, market value tracking, budgeting, ITR summaries, mobile app, sub-categories for income, multi-user login.
