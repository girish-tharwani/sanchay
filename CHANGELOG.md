## v0.0.6-Finch — 2026-04-09

### Added
- `build.sh setup-installer-checkbox` target: one-time setup to patch the WiX template with a "Launch Sanchay now" checkbox on the installer finish screen
- `resources/wix/` directory for custom WiX installer resources

### Changed
- Help screen: replaced always-visible dismissable banner with a gold "Quick Start" button that opens a modal on demand
- ImportMappingDialog: added padding inside the Detected Columns and Amount Columns info boxes

## v0.0.5-Finch — 2026-04-09

### Changed
- Removed commented-out dead code across DataStore, PersistenceService, UiUtils, TransactionsScreen, FinancialPlanningScreen

## v0.0.4-Finch — 2026-04-09

### Changed
- Removed unused dead code: methods, fields, and local variables flagged by IDE across InvestmentPanel, TransactionsScreen, ReportsScreen, RecurringScreen, ImportMappingDialog, FinancialPlanningScreen, HelpScreen, MarketValueHistoryDialog
- Updated `MarketValueHistoryDialog` to use non-deprecated `CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN`
- `HelpScreen` constructor no longer takes a `MainWindow` parameter

## v0.0.3-Finch — 2026-04-09

### Added
- Section 11.3 in USER-GUIDE.md explaining all Financial Planning calculated fields
- `/baseline-release` command for versioned release workflow

## v0.0.2-Finch — 2026-04-08
Planning screen refactor

## v0.0.1-Finch — 2026-04-08
- Start of new build 