## v1.0.0-Lark — 2026-04-14

baseline for release

## v0.0.4-Lark — 2026-04-14

### Fixed
- Record Recurring dialog no longer auto-selects the first bank account as From Account when the schedule has no from account set (e.g. PF and ESPP investment schedules)

## v0.0.3-Lark — 2026-04-14

### Added
- Structured Salary income sources now support an optional Share Purchase Plan (ESPP): checkbox below Gratuity enables a monthly contribution amount field and an equity account selector; the contribution is deducted post-tax from the bank deposit amount and creates a recurring INVESTMENT schedule (from account blank, auto-record after 1 day) linked to the chosen equity account
- PF recurring schedule is now also created with auto-record after 1 day

## v0.0.2-Lark — 2026-04-14

### Fixed
- Record Recurring dialog now uses correct account filters per transaction type: EXPENSE shows bank + credit card accounts in From; CC_PAYMENT, TRANSFER, LOAN_PAYMENT, INVESTMENT show bank accounts only in From; CC_PAYMENT, INVESTMENT, and LOAN_PAYMENT now show a type-specific To Account dropdown (credit cards, investment accounts, and loan accounts respectively)

### Changed
- Financial Planning screen header now shows a "Check Help to understand how these amounts are calculated." hint line beneath the last-updated date
- USER-GUIDE Post-Retirement Projection section updated to reflect three-phase spending model, Minimum/Actual projection toggle, table columns, and two-cell depletion highlighting

## v0.0.1-Lark — 2026-04-13

- Start of new build
