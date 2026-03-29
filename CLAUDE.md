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
User-selectable: `Expense`, `Income`, `Transfer`, `Refund`, `Investment`, `CC Payment`, `Redeem`, `Loan Payment`
Internal (code-generated, not shown in type picker): `GAIN`, `LOSE` — created as part of a REDEEM group to represent investment gain/loss posted to the bank account.

## Account types
- **Bank:** Savings, Current
- **Credit Card:** liability account; CC Payment reduces outstanding
- **Loan:** Home, Vehicle, Personal — outstanding = opening balance minus all Transfer payments
- **Investment:** bucket model (All Mutual Funds, All Equities, All Bonds seeded by default)

## Recurring transactions
- Never auto-post — always require user confirmation (Record / Skip)
- Auto-created when saving: Loan account → EMI schedule; CC account → payment reminder; RD account → instalment transfer
- Pending instances shown on Dashboard (due ≤7 days or overdue)

## Navigation
Left sidebar (fixed, non-collapsible): Dashboard → Accounts → Recurring → Reports → Categories | Profile → Settings. No standalone Transactions screen — accessed via account card → Transactions button. Floating `+` button on every screen opens the New Transaction dialog.

## Current version
`1.0.3-MVP1` (pom.xml)

## Out of scope (initial version)
Cloud sync, PDF/OFX import, push notifications, market value tracking, budgeting, ITR summaries, mobile app, multi-user login.
