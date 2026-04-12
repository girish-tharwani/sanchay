## v0.0.8-Jay — 2026-04-12

### Added
- Cash Flow Forecast: "Show sum of all accounts" toggle in the Choose Accounts dialog; when off, the thick gold total line is hidden and the Y-axis rescales to the visible series
- Cash Flow Forecast: MF and Equity investment accounts now appear in the Investments group of the account selector as permanently disabled rows, giving a visual cue that they are excluded from projection

## v0.0.7-Jay — 2026-04-12

### Changed
- Extracted Monthly Expense Summary tab into `MonthlyExpenseSummaryTab`; `ReportsScreen` is now a thin coordinator matching the pattern of `CashFlowForecastTab`

## v0.0.6-Jay — 2026-04-12

### Removed
- Credit Card Report tab from Reports screen

## v0.0.5-Jay — 2026-04-12

### Fixed
- Recurring schedule editor no longer auto-selects the first bank account as From Account when editing an auto-created PF schedule that has no source account set

## v0.0.4-Jay — 2026-04-11

### Added
- Cash Flow Forecast: data point tooltips on the line chart showing series name, month, and balance on hover

## v0.0.3-Jay — 2026-04-11

### Changed
- Cash Flow Forecast: removed the four stat tiles (Total Projected Income, Total Cash Outflows, Net Cash Flow, Projected Balance) and all associated calculation logic

## v0.0.2-Jay — 2026-04-11

### Fixed
- Cash Flow Forecast: FD maturity no longer zeros the entire "All FDs" account balance — only the maturing FD's principal is subtracted, leaving other active FDs intact

## v0.0.1-Jay — 2026-04-11

- Start of new build
