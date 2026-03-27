## v0.2.5 — 2026-03-27

### Changed
- CSS refactor: replaced ~200 inline `setStyle("…hex…")` calls across all 15 UI screens with CSS style-class references
- New CSS utility classes added to `app.css`: `badge-imported`, `badge-auto-cat`, `badge-reconciled`, `badge-manual`, `category-row`, `subcategory-row`, `menu-item-danger`, `avatar-initial`
- `UiUtils.CHART_PALETTE` extracted from `ReportsScreen.BAR_COLOURS` — single definition shared across report tabs
- `DashboardScreen` `STRIPE_*` string constants removed; inlined as CSS token references at each call site
- Removed 4 dead CSS classes (`btn-primary`, `btn-teal`, `amount-negative`, `amount-positive`, `text-caption`)
- Remaining intentional inline styles documented with `// Inline required:` comments (runtime data, Text nodes, Shape.fill, DatePicker skin)

## v0.2.4 — 2026-03-27

### Fixed
- Category rule exact-key matches now win over fuzzy co-matches — a stored rule is no longer silently suppressed when other unrelated rules share a common word (e.g. "services")
- Mid-word slashes in bank descriptions (e.g. `FAC/ILITY`) are now collapsed before tokenizing, so they still match against the correctly-spelled stored rule key

## v0.2.3 — 2026-03-26

### Added
- All dialogs now have a branded header: teal icon box + bold title on `#f8fbfc` background with bottom border, matching the design wireframe
- `UiUtils.setDialogHeader()` — shared helper used across all dialogs for consistent header styling
- Getting Started dialog (Help menu) fully redesigned: custom header, step rows with dividers, inline nav-hint chips (`[Profile]`, `[+ Add Member]`, etc.) in step descriptions, "About Sanchay…" link in footer
- Dashboard welcome banner step descriptions updated with nav-hint chips
- `UiUtils.navHint()` and `UiUtils.stepDescFlow()` helpers for building rich step descriptions
- Step number badges updated to teal gradient circle with drop shadow
- Category/sub-category search (filter-as-you-type + unambiguous inline autocomplete) added to Recurring Schedule dialog, matching Transaction dialog behaviour
- `UiUtils.wireAutoComplete()` — shared autocomplete helper used by both Transaction and Recurring dialogs

### Changed
- Transaction dialog: validation errors now keep the dialog open (event filter pattern); error styling matches recurring dialog (styled dialog, not plain Alert)
- Redeem transaction: category/sub-category list switches between Income (gain) and Expense (loss) categories live as amounts are typed; editing an existing LOSE transaction pre-selects Expense categories
- Accounts → Transactions view: Bank, Loan, and Investment accounts now show their key amount (Balance / Outstanding / Invested) right-aligned in the header bar — no extra card
- Recurring Record dialog: split into separate From Account and To Account fields; validation uses styled inline error
- Pending review filter now includes both `IMPORTED` ("i" badge) and `AUTO_CATEGORIZED` ("?" badge) transactions
- Transactions table resize policy changed to unconstrained (matches All Schedules table behaviour)
- Export CSV remembers the last selected folder within the session
- Type badge in Record Recurring dialog now renders with full badge styling (was plain text)

### Fixed
- Investment account balance correctly deducts Redeem transactions (was deducting Transfer)
- Redeem: removed principal-cannot-exceed-total validation (blocked loss-making redemptions)
- CC card amounts now display two decimal places

## v0.2.2 — 2026-03-25

### Added
- FAB (floating `+` button) now pre-populates the context account when opened from an account's transaction view
- `TransactionDialog.setContextAccount()` — handles all transaction types (Expense, Income, Transfer, Investment, CC Payment, Refund, Redeem, Loan Payment) when pre-populating from account context

### Changed
- Recurring Record dialog split "Paid From" into separate "From Account" and "To Account" fields — correctly handles Income, Transfer, and Loan Payment types
- Skip Recurring confirmation replaced with styled dialog (was plain system alert)
- Recurring Record inline validation (amount, same-account check) now uses styled error dialog
- RecurringScreen Transfer validation: added same-account check; alert titles unified to "Validation Error"
- CC card amounts display two decimal places (`%,.2f`)
- CSV export signing: EXPENSE always exported negative; all other types use from/to account direction

### Fixed
- Investment account balance now correctly deducts Redeem transactions (was incorrectly deducting Transfer)
- Redeem dialog: removed principal-cannot-exceed-total validation (blocks legitimate loss-making redemptions)

## v0.2.1 — 2026-03-23

### Changed
- Transaction type badges now use distinct CSS classes and a refreshed color palette with border outlines; compact `badge-sm` variant used in table cells
- Delete transaction and import-complete confirmations replaced with styled dialogs (previously plain system alerts)
- Categories add/rename dialogs replaced with styled single-field input dialog (previously `TextInputDialog`)
- Recurring Record/Skip icon buttons unified in style across Dashboard and Recurring screens (muted fill, border outline)
- All primary action buttons (Browse, Backup Now, Get Started, Download CSV, Dismiss banner) migrated to `btn-gold` style class
- Row accent strip (imported/reconciled) background insets fixed — bottom gap removed
- Recurring and Accounts table columns switched to `prefWidth` / unconstrained resize policy for better horizontal layout

## v0.2.0 — 2026-03-23

### Changed
- Full UI redesign: teal (#0f3d4a / #2a8a7a) and gold (#f0a500) design system applied across all screens
- All dialogs now receive the app stylesheet (were previously unstyled system dialogs)
- Tables: white rows with teal row dividers, uppercase teal column headers, CENTER alignment for headers and data rows; Description columns left-aligned, Amount columns right-aligned
- Account cards: account name now in brand teal; icon buttons updated to match wireframe (ℹ / ≡)
- Sidebar, Dashboard, Accounts, Recurring, Reports, Categories, Profile, Settings screens restyled
- FirstRunWizard and SplashScreen updated to new colour palette
- Import CSV dialog: detected columns shown as pill chips; amount columns in a styled sub-section
- AmbiguousMatchDialog and RecurringMatchDialog: full structural redesign with section labels and card-style match rows

## v0.1.10 — 2026-03-23

### Fixed
- Recurring record dialog now displays transaction type in title case (e.g. "Expense", "CC Payment") instead of raw enum name
- Transactions screen filter bar (date pickers, search field) restyled as flat controls with no box border, matching the rest of the app
- Reports screen month, FY, and card dropdowns restyled to match flat filter-bar style
- TabPane on Reports screen restyled with flat underline indicator — transparent background, brand-blue underline on active tab
- AmbiguousMatchDialog: title shortened, header changed to "Manual selection required", candidate row background extended to cover radio button for correct left-edge alignment
- RecurringMatchDialog: header shortened to "Select the matching schedule", candidate row background extended to cover radio button for correct left-edge alignment

## v0.1.9 — 2026-03-22

### Fixed
- Date picker calendar popup now has consistent styling (dark-blue header, visible navigation arrows) across all screens — Transaction dialog, Accounts screen, Recurring screen, main window date picker, and profile date of birth picker

## v0.1.8 — 2026-03-22

### Fixed
- "Auto-record after" checkbox label in the recurring schedule dialog is now visible (was white/invisible)
- Date picker calendar popup month/year header now shows correctly styled text (was white/invisible due to CSS not reaching the popup scene)

## v0.1.7 — 2026-03-22

### Fixed
- Recurring schedule matching now also matches compound description words (e.g. "Homeloan" vs "Home Loan") via substring token overlap, reducing false negatives in import reconciliation
- `Loan Payment` type badge in Recurring screen now renders with correct coloured styling (was plain text)
- Date picker calendar popup month/year header now correctly shows dark blue background with white text (CSS variables are now literal hex values to ensure they resolve in the popup's scene)

## v0.1.6 — 2026-03-22

### Added
- During CSV import, imported transactions with no manual match are now also matched against pending recurring schedule occurrences — same account, direction, ±2-day date window, ±5% amount tolerance, and description token-overlap similarity
- New `RecurringMatchDialog` lets the user confirm or reject each recurring match, or add the transaction as new instead
- Import summary now shows a separate count for transactions recorded against a recurring schedule

## v0.1.5 — 2026-03-22

### Added
- "Show pending review only" checkbox in the transaction grid toolbar — filters the table to auto-categorized (`?`) rows only

### Changed
- Transaction grid filter toolbar restyled to match the rest of the app: filter labels use `form-label` class, row wrapped in a card, search field grows to fill available width

## v0.1.4 — 2026-03-22

### Fixed
- Accepting an auto-categorized transaction via the `?` badge now correctly sets the source indicator to `RECONCILED` (was incorrectly set to `MANUAL`), preventing re-import on future statement imports
- ACH/NACH SIP transactions for a recurring fund now match existing type rules regardless of the date embedded in the description (e.g. `KOTAKMF05032026` now matches `KOTAKMF05012026`)

### Changed
- Transaction grid "2nd Account" column renamed to "To / From Account"

## v0.1.3 — 2026-03-22

### Fixed
- Left-border accent on imported/reconciled rows was not rendering; switched from `-fx-border-color` (painted over by Modena theme) to `PseudoClass` + CSS background-insets layering
- Amber border on imported/auto-categorized rows did not clear after manual edit; editing an IMPORTED transaction now downgrades its source indicator to MANUAL

## v0.1.2 — 2026-03-22

### Fixed
- Transaction grid date column sorted lexicographically instead of chronologically; amount column sorted as string; both columns now use typed values (`LocalDate`, `Long`) so JavaFX sorts correctly
- CC/Loan Payment accounts missing from Expense and Loan Payment "From Account" dropdowns
- Importing a second account's statement did not reconcile against transactions originally imported from another account and then edited (e.g. bank EXPENSE converted to CC_PAYMENT); reconciliation now considers IMPORTED and AUTO_CATEGORIZED transactions as candidates
- Editing an imported REDEEM transaction (converted from bank import) now marks bank-side REDEEM and GAIN/LOSE entries as RECONCILED rather than MANUAL
- Category auto-suggestion incorrectly matched unrelated merchants sharing a city name (e.g. "NETFLIX … MUMBAI IN" suggested Travel/Vacation due to "mumbai" overlap with Mumbai hotel rules); Indian city names and other geographic noise words are now excluded from fuzzy matching

### Changed
- Transaction dialog: all account dropdowns relabelled to "From Account" / "To Account" for consistency across all transaction types
- Transfer type restricted to bank-to-bank only (investment→bank and bank→loan use their dedicated types)
- `getSignedAmountInr` refactored to share `isDebitFor` helper; `getSignedAmountPaise` added for numeric sorting

## v0.1.1 — 2026-03-22

### Added
- `GAIN` and `LOSE` transaction types for investment gain/loss on redemption (internal, not user-selectable)
- `groupTransactionId` field links the 3 transactions created by a REDEEM operation (investment-side REDEEM + bank-side REDEEM + GAIN/LOSE)
- `AUTO_CATEGORIZED` source indicator for imported transactions whose category was auto-filled by rules; shown with a `?` badge that the user can accept with a single click
- Group-aware CSV reconciliation: a single bank CSV row can now reconcile against a REDEEM group (e.g. principal + gain credited as one bank entry)
- `findGroupMatches` and `reconcileGroup` helpers in `ImportService`
- `deleteTransactionGroupInternal` and `deleteTransactionByIdInternal` helpers in `DataStore`

### Changed
- REDEEM now creates 3 linked transactions atomically instead of a single transaction; edit and delete operations are group-aware
- Delete confirmation dialog for grouped transactions warns that related principal and gain/loss entries will also be deleted
- Editing an `AUTO_CATEGORIZED` transaction downgrades its indicator to `IMPORTED` (clearing the `?` badge)
- GAIN/LOSE transactions are displayed as REDEEM in the type picker when editing

### Fixed
- REDEEM principal calculation now handles both old single-transaction format and new grouped format (backward compatible)
