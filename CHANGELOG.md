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
