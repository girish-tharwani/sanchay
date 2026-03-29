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
