# Sanchay AI Agent Guidelines

## Architecture Overview
- **Entry Point**: `com.sanchay.MainApp` — checks `%APPDATA%\sanchay\app-config.json` for data folder; launches `FirstRunWizard` if missing/invalid, else loads data and shows `MainWindow`.
- **Data Layer**: `DataStore` singleton holds all in-memory data (accounts, transactions, etc.); `PersistenceService` loads/saves JSON files atomically (write `.tmp` then rename).
- **UI Layer**: Programmatic JavaFX (no FXML); `MainWindow` manages sidebar navigation and screen switching; screens in `ui/` package (e.g., `DashboardScreen`, `AccountsScreen`).
- **Service Layer**: Business logic in `service/` (e.g., `ImportService`, `AmortizationService`); services don't reference UI types.
- **Data Model**: Amounts in **paise** (integer); dates as `LocalDate` (ISO strings in JSON); every record has UUID primary key; financial year April–March.

## Key Workflows
- **Run App**: `mvn javafx:run` (requires JavaFX 21).
- **Build/Package**: `./build.sh clean package` (Maven wrapper); packaging uses `jpackage` for Windows `.exe` (details in `build.sh` or pom.xml profiles).
- **Data Persistence**: Every mutation rewrites the full JSON file (e.g., `PersistenceService.saveTransactions()`).
- **Navigation**: Sidebar buttons switch screens in `MainWindow.navigateTo()`; `AccountsScreen` rebuilt on each visit (not cached); others cached and refreshed.
- **New Transactions**: Floating "+" button opens `TransactionDialog`; pre-populates account if on account's transaction view.

## Coding Conventions
### UI Styling (from CLAUDE.md)
- **No Hardcoded Colors**: All colors defined as `-color-*` variables in `.root`; reference tokens only (e.g., `-fx-text-fill: -text-primary;`).
- **No Inline Styles**: `.setStyle()` banned except for runtime-computed values (e.g., data-driven colors); document exceptions: `// Inline required: colour computed from live data at runtime.`
- **CSS Classes per Component**: Shared styles in `app.css`; screen-specific CSS in screen's file (e.g., `AccountsScreen.css`); no cross-imports.
- **Interactive States**: Define `:hover`, `:pressed`, `:focused`, `:disabled` for all interactive elements.
- **Spacing Scale**: Use consistent padding/margin values (e.g., 8, 12, 16, 24); avoid magic numbers.
- **Semantic Naming**: `-color-error` not `-red`; `-color-income` not `-green`.

### Functional Coding (from CLAUDE.md)
- **Dialogs as Classes**: Each dialog its own class (e.g., `TransactionDialog`); constructor/setters for input; result via `.showAndWait()` or callback.
- **Service-UI Separation**: Services take callbacks/interfaces for UI interactions; no `javafx.*` imports in services.
- **No Static Mutable State**: Pass data via constructors/injection; avoid static fields for inter-screen communication.
- **Shared Logic in Utils**: Duplicated code → `UiUtils` (UI helpers) or service classes; not in controllers.
- **Minimize Visibility**: Fields/methods `private` by default; `package-private` within package; `public` only when necessary.
- **Error Handling**: Standardize per category (e.g., alerts for validation, inline labels for form errors).

## Data Patterns
- **Transaction Types**: User-selectable: `EXPENSE`, `INCOME`, `TRANSFER`, `REFUND`, `INVESTMENT`, `CC_PAYMENT`, `REDEEM`, `LOAN_PAYMENT`; internal: `GAIN`, `LOSE` (auto-created on redeem).
- **Account Types**: `BankAccount` (Savings/Current), `CreditCardAccount`, `LoanAccount`, `InvestmentAccount` (bucket model).
- **Recurring Transactions**: Never auto-post; user confirms "Record" or "Skip"; auto-created for loans/CC/EMIs.
- **Import/CSV**: `ImportService` parses CSVs; `ImportMapping` per account; `AmbiguousMatchDialog` for conflicts.
- **Validation**: Amounts >0; dates not future (except projections); categories required for expenses/income.

## File Structure Examples
- **Models**: `Transaction.java` with nested classes (e.g., `Classification`, `Payment`); enums for `Type`, `PaymentMode`.
- **Screens**: `AccountsScreen.java` builds `VBox` with collapsible groups; uses `DataStore.getBankAccounts()`; inline style for chevron size.
- **Persistence**: `PersistenceService.java` uses Gson with `LocalDate` adapters; `loadAll()` calls per-file loaders.
- **CSS**: `.root` defines `-brand-dark: #0f3d4a;`; `.sidebar-item-active` uses `-brand-accent: #f0a500;`.

## Common Pitfalls
- **Amounts**: Always convert INR to paise (multiply by 100); display via `UiUtils.formatAmountPaise()`.
- **Dates**: Use `DataStore.getDateFormatter()` for display; store as `LocalDate`.
- **UI Refresh**: Call `mainWindow.refreshDashboard()` after mutations; screens refresh on navigation.
- **Dialog Results**: Use `Optional<ButtonType>` from `Alert.showAndWait()`; handle `OK`/`CANCEL`.
- **Threading**: UI on FX thread; long ops (e.g., import) use `Task` or `Platform.runLater()`.
