## v1.1.6 — 2026-04-03

### Changed
- Moved `HelpDialog` and `SingleInputDialog` to `ui/common/` sub-package; `MainWindow`, `SplashScreen`, and `UiUtils` remain at `ui/` root

---

## v1.1.5 — 2026-04-03

### Added
- Transactions screen: Ctrl+V pastes tabular data (from Excel or a CSV file) directly into the import flow — same mapping dialog and reconciliation logic as the Import CSV button; auto-detects tab-delimited (Excel) vs comma-delimited format
- Transactions screen: footer hint shows "Ctrl+V to paste from Excel / CSV" for Bank and Credit Card accounts

---

## v1.1.4 — 2026-04-03

### Added
- Financial Planning: Major Events section in the Expenses card — user-defined one-time or recurring financial events with forecasted cost tracked against actual spend via category matching; add/edit/delete via double-click dialog
- Financial Planning: "Forecasted Retirement Corpus" KPI tile — dynamically computed from future earnings minus expenses minus major event costs
- Dashboard: "Net Worth" summary card — corpus value computed using the same methodology as the Financial Planning screen (bank net of CC, equity/MF at 90% market value, bonds/FD/RD/PF at cost, rounded to ₹10,000 buckets)

---

## v1.1.3 — 2026-04-03

### Added
- Redeem transaction: "Reference No." dropdown shown when From Account is a Fixed Deposit account; populated from INVESTMENT transactions linked to that account; stored as `orgnlFDRef` on all three group transactions (investment-side REDEEM, bank-side REDEEM, GAIN/LOSE); restored correctly when editing an existing REDEEM

### Changed
- Financial Planning: FD Interest now excludes FDs that have already been redeemed (matched by `orgnlFDRef` on REDEEM transactions); result reduced by pre-retirement tax rate
- Financial Planning: RD Interest rewritten to use recurring INVESTMENT schedules targeting RECURRING_DEPOSIT accounts; formula is `maturityAmount − (numberOfPayments × instalment)`; result reduced by pre-retirement tax rate
- Financial Planning: Equity and MF projections now show appreciation only — total future value minus current corpus (PV) minus total planned SIP contributions

---

## v1.1.2 — 2026-04-03

### Added
- Financial Planning screen: Current Corpus Breakdown card and Current Corpus KPI tile now computed from live account data (bank net of CC outstanding, equities/MF at 90% of last market value, bonds/FD/RD/PF at invested value; all rounded down to nearest ₹10,000)
- Financial Planning screen: Future Earnings Until Retirement card and Future Earnings KPI tile now computed from live data — post-tax income, PF contributions, gratuity, PF interest (month-by-month simulation), bonds/FD/RD realized ROI, and equity/MF future value with SIP projections
- Financial Planning screen: Recalculate button now recomputes and refreshes the Future Earnings card and KPI tile in-place without rebuilding the screen

### Changed
- Financial Planning screen: Current Age in Plan Parameters now shows two decimal places (e.g. 30.58) for more precise earnings projection
- Financial Planning screen: Retirement Age input replaced with Retirement Date picker; retirement age is derived from DOB and the selected date with two decimal precision; all projection calculations use the exact date
- Financial Planning screen: "Retirement Year" KPI tile renamed to "Retirement Age" and now shows the computed decimal retirement age

---

## v1.1.1 — 2026-04-02

### Added
- Cash Flow Forecast: "Forecasted Expenses" section title above the breakdown table
- Cash Flow Forecast: manual amount corrections to forecasted expense rows (double-click Amount cell); scope dialog lets user apply the correction to this month only or all future months
- Cash Flow Forecast: Exclude / Include action buttons per forecast row; excluded rows stay visible greyed-out with strikethrough; scope dialog same as corrections
- Cash Flow Forecast: manual corrections and exclusions persisted to `forecast_overrides.json` and restored across app restarts
- Cash Flow Forecast: "Regenerate Projections" gold button clears all manual overrides and recomputes from scratch after confirmation
- Cash Flow Forecast: account grouping when more than 5 accounts are projected — default view shows Bank Accounts / Credit Cards / Investments group series; "Show Details / Show Summary" toggle restores per-account view
- Cash Flow Forecast: legend switched to FlowPane so it wraps to multiple lines when there are many series

### Changed
- Cash Flow Forecast: stat cards moved to a dedicated second row and given equal width so the filter row is no longer crowded
- Cash Flow Forecast: manual corrections and exclusions are preserved when the user changes the forecast period
- Part-Prepayment dialog: removed native OS title bar; removed the redundant "Hide Details" toggle button; button order fixed to Reduce Tenure → Reduce EMI → Cancel; "Reduce Tenure" button widened to avoid truncation

### Fixed
- Cash Flow Forecast: expense forecast significantly overestimated due to three compounding bugs: (1) recurring expenses were applied twice — once via the recurring schedule loop and again via pattern-based forecasts trained on the same data; (2) monthly average was divided by months-with-data rather than the full analysis window, inflating irregular expenses; (3) trend multiplier was unbounded, compounding overestimates on 12–24 month projections

---

## v1.0.9 — 2026-03-29

### Added
- Loan amortization schedule: "Setup Payments" gold button generates a new recurring payment schedule pre-populated with EMI amount, due day, start date, and remaining payment count from the amortization schedule
- Loan amortization schedule: if a payment schedule already exists, the button is disabled and an "Edit" link is shown instead — preventing duplicate schedules for the same loan
- Loan rate / EMI change: when an EMI change is saved, the app offers to update the linked recurring payment schedule amount automatically
- Loan prepayment (Reduce EMI mode): after the amortization schedule is recalculated, the app offers to update the linked recurring payment schedule to the new EMI amount

---

## v1.0.8 — 2026-03-29

### Added
- Reports: Cash Flow Forecast tab shows a multi-series line chart projecting account balances month by month over Next 6 / 12 / 24 Months, This Financial Year, or This Calendar Year; includes all active bank accounts, credit cards, and FD / RD / Bond investment accounts
- Cash Flow Forecast: stat cards show projected end balance, total projected income, total projected expenses, and net cash flow for the selected period
- Cash Flow Forecast: FD and RD maturity events are applied at the correct month; the maturity amount is credited to the source bank account and the investment account closes to zero
- Cash Flow Forecast: warning bar highlights active loans that have no recurring EMI payment set up and are therefore excluded from the projection

### Fixed
- Reports screen no longer crashes on navigation (StackOverflowError caused by re-entrant refresh loop in period picker)
- Profile → Earnings: validation error now uses the same styled dialog as New Transaction; dialog stays open after a validation error so the user can correct the fields

---

## v1.0.7 — 2026-03-29

### Added
- Loan payments: part-prepayment is automatically detected when the principal paid exceeds the scheduled amount; user is prompted to choose between "Reduce Tenure" (same EMI, fewer months) or "Reduce EMI" (same tenure, lower EMI); the amortization schedule is recalculated and saved immediately
- Prepayment mode preference can be saved per loan so the user is not prompted again
- Profile → Earnings: each earning member now supports multiple income sources; tabs are user-named and can be added (Simple or Structured Salary) or removed independently
- Profile → Earnings: Estimated Tax Rate field added to the Simple earnings tab; net amount shown live
- Profile → Earnings: Structured Salary inputs are now entered as annual figures; calculations divide by 12 internally
- Profile → Earnings: EPS field now shows "—" until Basic+DA data is entered; no longer defaults to ₹1,250
- Profile: removing an earning member now cascade-deletes all linked income and PF recurring schedules

---

## v1.0.6 — 2026-03-29

### Added
- Accounts screen: chevron next to the "Accounts" title collapses or expands all account groups at once; clicking anywhere on the title row (chevron or label) toggles it

### Fixed
- Loan account details page no longer shows a redundant "Joint Holder" row; co-applicant is shown via the existing "Co-applicant" field

---

## v1.0.5 — 2026-03-29

### Added
- Market value tracking for Equity and Mutual Fund accounts: record a market value snapshot at any past or present date, view full history in a table, and see the latest market value alongside invested amount on both the account card and account details page
- Account card for Equity/MF accounts now shows "Invested / Market Value" in the same side-by-side format as credit card Outstanding / Available

---

## v1.0.4 — 2026-03-29

### Fixed
- New Transaction dialog: Type dropdown now restricts to context-appropriate values when opened from within an account page (CC → Expense/Refund/CC Payment; Loan → Loan Payment; Investment → Investment/Redeem; Bank → all types)
- New Transaction dialog: switching away from Investment type no longer collapses the dialog height
- New Recurring Schedule dialog: Category dropdown now correctly loads Income categories when Transaction Type is Income
- New Recurring Schedule dialog: account field label now changes to "To Account" when Transaction Type is Income

---

## v1.0.3 — 2026-03-29

### Added
- Investment transaction dialog: account-specific fields (Scheme/NAV, FD details, RD reference) now appear in a side panel that expands the dialog width when a To Account is selected; fields use a stacked label-above-input layout
- Payout Date day spinner is now editable (user can type a number directly)

### Fixed
- Investment side panel was blank when switching away from Investment type and back; the panel now repopulates correctly on every return
- Payout Date month ComboBox was too narrow to show the month name; Day spinner was too wide — widths corrected
- Payout Date month ComboBox and Day spinner heights now match

---

## v1.0.2 — 2026-03-28

### Added
- Transaction table columns are now context-aware by account type: Loan accounts show Principal and Interest; Investment (Equity/MF) shows Scheme/Script and Units/NAV; Investment (Bonds/FD) shows Maturity Date and Maturity Amount; Investment (RD) shows Maturity Date from the parent recurring schedule; Bank and Credit Card retain Category and Sub-category

### Changed
- Loan outstanding calculation now uses only the principal portion of each EMI (from stored split or amortization schedule fallback) instead of the full EMI amount
- Monthly Expenses on Dashboard now deducts Refunds; Monthly Income now includes Gains and deducts Losses
- Removed Category and Sub-category columns from the Recurring Schedules table

### Fixed
- To/From Account column now correctly resolves the counterpart account for REDEEM transactions
- Principal and Interest columns in Loan transaction table now fall back to the amortization schedule when the principal split is not stored on the transaction

## v1.0.1 — 2026-03-28

### Changed
- Fixed Deposit transaction fields (FD Ref, Interest Rate, Maturity Date, Maturity Amount) are now stored as dedicated typed fields on the Transaction model instead of being packed into the `notes` string

## v1.0.0-MVP1 — 2026-03-28

MVP1 v1.0.0 baseline
