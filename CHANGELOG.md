## v0.0.10-Myna — 2026-04-17

### Changed
- `AccountsScreen` and `TransactionsScreen` no longer hold a `MainWindow` reference; shell callbacks are passed via the new `NavigationContext` value object (step 4.2 of refactor plan)

## v0.0.9-Myna — 2026-04-17

### Changed
- Categories screen: expand/collapse chevron changed from button to label (no square border), using `▸`/`▾` glyphs matching other screens
- Categories screen: category and sub-category name labels now use `-brand-dark` (green) to match chevron colour used elsewhere

## v0.0.8-Myna — 2026-04-17

### Changed
- Settings › Backup Now: file chooser now reopens in the last-used backup folder (persisted in `settings.json`)

## v0.0.7-Myna — 2026-04-17

### Fixed
- `AccountDialog`, `RecordMarketValueDialog`, `MajorEventDialog`, `AddEditRecurringDialog`, `LoanPaymentPanel`: `Alert` instances now call `UiUtils.applyStylesheet()` so design tokens and component styles resolve correctly in alert dialogs

## v0.0.6-Myna — 2026-04-17

### Added
- `ui/common/AccountCombos`: utility for consistent account combo display (name cell factory + button cell); applied across all account ComboBoxes in transaction panels, recurring dialogs, and earnings forms
- `ui/common/CategoryComboWiring`: utility encapsulating the category → sub-category cascade listener and "└ name" sub-category cell styling; replaces inline duplication in `TransactionDialog` and `AddEditRecurringDialog`
- `ui/common/TransactionTableBuilder`: utility producing standard transaction table columns (DATE, DESCRIPTION, TYPE, ACCOUNT, SUB-CATEGORY, AMOUNT); used in `CategoryTransactionsDialog`

### Changed
- `EarningsDialog`: raw `GridPane` + `ColumnConstraints` construction in `buildSourceTab` and `showAddSourceDialog` replaced with `UiUtils.buildFormGrid()`
- `CategoryTransactionsDialog`: column setup replaced with `TransactionTableBuilder.buildStandardColumns()`; date column now respects user's date format preference instead of hardcoded "dd MMM yyyy"
- `TransactionDialog`: removed private `styleAccountCombo()` and `wireCatSubCat()` methods; delegates to `AccountCombos` and `CategoryComboWiring` utilities

## v0.0.5-Myna — 2026-04-16

### Changed
- `AccountsScreen`: star label and account name label state colours moved from `setStyle()` to CSS classes (`.account-star-active/inactive`, `.account-name-active/inactive`)
- `CategoriesScreen`: category and sub-category name label active/inactive states moved from `setStyle()` to CSS classes (`.category-name-active/inactive`, `.subcategory-name-active/inactive`)
- `ImportCompleteDialog`: all four `setStyle()` blocks replaced with CSS classes — gradient header icon, per-line check badge (success/neutral states), and count number

### Added
- `components.css`: state classes for account card labels, category name labels, and import dialog elements (`.import-success-header`, `.import-check-badge-*`, `.import-stat-count-*`)

## v0.0.4-Myna — 2026-04-16

### Fixed
- `TransactionDialog`: scroll pane content area now shows white background, matching all other dialogs

### Changed
- `ExpenseTrendTab`: category, sub-category, and total row styles moved from inline `setStyle()` to CSS classes (`.trend-row-category`, `.trend-row-subcategory`, `.trend-row-total`, etc.)
- `HelpDialog`, `EarningsDialog`, `AddEditRecurringDialog`, `AccountsScreen`: recurring typography patterns moved from `setStyle()` to CSS utility classes (`.text-link-button`, `.text-result-value`, `.inv-type-hint`, `.btn-compact`)

### Added
- `theme.css`: `-brand-mid-13` alpha token
- `reports.css`: six `.trend-row-*` classes for Expense Trend table row styling
- `components.css`: four typography utility classes — `.text-link-button`, `.text-result-value`, `.inv-type-hint`, `.btn-compact`

## v0.0.3-Myna — 2026-04-16

### Added
- `theme.css`: 12 named alpha tokens (`-brand-mid-06` … `-brand-mid-35`) replacing all `rgba(42,138,122,x)` magic numbers across the CSS codebase
- `components.css`: `.card-wrapper` class (no-padding card variant) — fixes missing chart card styling in Cash Flow Forecast
- `UiUtils`: `HEX_BRAND_LIGHT`, `HEX_BRAND_MID`, `HEX_BRAND_ACCENT` string constants — brand hex literals are now centralised in one place
- `UiUtils.applyStylesheet()` now loads `screens/help.css` (was previously omitted)

### Changed
- All `rgba(42,138,122,x)` occurrences in `components.css`, `layout.css`, `help.css`, `reports.css`, and `planning.css` replaced with the new named tokens
- `PostRetirementProjectionPanel`: phase row background changed from raw `#f0f8f6` to `-surface-teal-faint` token
- All callers of `Color.web("#3db89a")` and `Color.web("#f0a500")` updated to use `UiUtils.HEX_BRAND_LIGHT` / `HEX_BRAND_ACCENT`
- `screens/reports.css` renamed to `reports.css`; `screens/planning.css` renamed to `planning.css` — both are globally loaded, so the `screens/` directory was misleading

## v0.0.2-Myna — 2026-04-16

### Added
- Cash Flow Forecast tab: "Excluding Market Investments" subtitle below the section title
- Recurring schedule dialog: Payment Mode field with smart defaulting

### Changed
- Payment Mode options: removed NEFT and IMPS; default changed from UPI to Net Banking
- Expense transactions: Payment Mode auto-defaults to Credit Card when a credit card account is selected, Net Banking otherwise
- Recorded recurring transactions now inherit the payment mode stored on the schedule

## v0.0.1-Myna — 2026-04-16

- Start of new build
