## v0.1.0-Jay — 2026-04-12
### Added
Added the option of 36 months cash flow forecast

## v0.0.10-Jay — 2026-04-12

### Changed
- Renamed `MonthlyExpenseSummaryTab` to `ExpenseReportTab`; tab label changed from "Monthly Expense Summary" to "Expense Report"

## v0.0.9-Jay — 2026-04-12

### Added
- Monthly Expense Summary: category multi-select filter (hides/shows categories in both chart and table; menu stays open for multiple selections)
- Monthly Expense Summary: Clear button resets all filters to current FY

### Changed
- Monthly Expense Summary: default filter is now current FY instead of current month
- Monthly Expense Summary: filter bar reordered — FY first, then Month
- Monthly Expense Summary: "Show sub-categories" checkbox moved into the chart section header, aligned with the Total row
- Monthly Expense Summary: category selector uses `CustomMenuItem` to stay open after each toggle

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
