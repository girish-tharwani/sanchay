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
