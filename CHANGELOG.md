## v0.0.5-Kiwi — 2026-04-12

### Added
- Expense Trend tab in Reports: tabular view of net expenses (EXPENSE minus REFUND) by category and sub-category across current + up to 4 past years; category filter with multi-select; Total row; CSV export
- Category filter on Expense Trend tab — selection persisted to `report_prefs.json` across restarts
- Category filter on Expense Report tab — selection persisted to `report_prefs.json` across restarts
- `ReportPrefsService` — loads/saves hidden category sets for both report tabs

### Changed
- All three Reports tabs now share consistent styling: teal page background, 24px padding, `text-section-title` headings, default ComboBox appearance
- `menu-button-as-combo` CSS class added to components.css for MenuButtons that must match ComboBox styling
- Clear button removed from Expense Report tab (category selections are now persisted instead of reset)

## v0.0.4-Kiwi — 2026-04-12

### Changed
- Accounts screen group collapse state is no longer persisted — on every app start only Favourites is expanded, all other groups are collapsed

### Fixed
- Removed stale `activeFinancialYear` field from settings.json (all consuming code was already commented out)

## v0.0.3-Kiwi — 2026-04-12

### Added
- Dashboard alert card for uncategorized EXPENSE / INCOME / REFUND transactions — shows count and a "Review & Fix →" link that opens a bulk-categorize dialog; card auto-hides when count reaches zero
- Uncategorized review dialog: table of uncategorized transactions with inline category and sub-category selectors, interim save support, and auto-close when all rows are categorized

### Fixed
- Category Transactions dialog: table now expands to fill available height (removed wrapping ScrollPane that was preventing VBox grow)
- Category Transactions dialog: ACCOUNT column now shows the correct account for REFUND transactions (falls back to `toAccountId` when `fromAccountId` is null)

## v0.0.2-Kiwi — 2026-04-12

### Changed
- Dashboard summary cards (top row) now each occupy exactly 25% of available width
- Dashboard credit card / loan cards now each occupy exactly 50% of available width
- Financial Planning KPI strip cards now each occupy exactly 33% of available width

## v0.0.1-Kiwi — 2026-04-12

- Start of new build
