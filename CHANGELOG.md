## v0.1.2 — 2026-03-22

### Fixed
- Transaction grid date column sorted lexicographically instead of chronologically; amount column sorted as string; both columns now use typed values (`LocalDate`, `Long`) so JavaFX sorts correctly
- CC/Loan Payment accounts missing from Expense and Loan Payment "From Account" dropdowns
- Importing a second account's statement did not reconcile against transactions originally imported from another account and then edited (e.g. bank EXPENSE converted to CC_PAYMENT); reconciliation now considers IMPORTED and AUTO_CATEGORIZED transactions as candidates
- Editing an imported REDEEM transaction (converted from bank import) now marks bank-side REDEEM and GAIN/LOSE entries as RECONCILED rather than MANUAL
- Category auto-suggestion incorrectly matched unrelated merchants sharing a city name (e.g. "NETFLIX … MUMBAI IN" suggested Travel/Vacation due to "mumbai" overlap with Mumbai hotel rules); Indian city names and other geographic noise words are now excluded from fuzzy matching

### Changed
- Transaction dialog: all account dropdowns relabelled to "From Account" / "To Account" for consistency across all transaction types
- Transfer type restricted to bank-to-bank only (investment→bank and bank→loan use their dedicated types)
- `getSignedAmountInr` refactored to share `isDebitFor` helper; `getSignedAmountPaise` added for numeric sorting

## v0.1.1 — 2026-03-22

### Added
- `GAIN` and `LOSE` transaction types for investment gain/loss on redemption (internal, not user-selectable)
- `groupTransactionId` field links the 3 transactions created by a REDEEM operation (investment-side REDEEM + bank-side REDEEM + GAIN/LOSE)
- `AUTO_CATEGORIZED` source indicator for imported transactions whose category was auto-filled by rules; shown with a `?` badge that the user can accept with a single click
- Group-aware CSV reconciliation: a single bank CSV row can now reconcile against a REDEEM group (e.g. principal + gain credited as one bank entry)
- `findGroupMatches` and `reconcileGroup` helpers in `ImportService`
- `deleteTransactionGroupInternal` and `deleteTransactionByIdInternal` helpers in `DataStore`

### Changed
- REDEEM now creates 3 linked transactions atomically instead of a single transaction; edit and delete operations are group-aware
- Delete confirmation dialog for grouped transactions warns that related principal and gain/loss entries will also be deleted
- Editing an `AUTO_CATEGORIZED` transaction downgrades its indicator to `IMPORTED` (clearing the `?` badge)
- GAIN/LOSE transactions are displayed as REDEEM in the type picker when editing

### Fixed
- REDEEM principal calculation now handles both old single-transaction format and new grouped format (backward compatible)
