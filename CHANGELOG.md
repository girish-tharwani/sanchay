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
