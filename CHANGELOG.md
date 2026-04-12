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
