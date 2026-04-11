## v0.0.3-Heron — 2026-04-11

### Added
- Search bar on the Recurring Transactions screen with a type filter dropdown and description text search

### Fixed
- Empty filler rows in the Recurring Transactions table no longer show stray dashes in the Next Due and Type columns

### Changed
- `/init-new-build` command now validates that the current branch is `main`, the working tree is clean, and local `main` is in sync with `origin/main` before creating the new branch with `git checkout -b dev/<BuildName>`

## v0.0.2-Heron — 2026-04-11

### Added
- `sourceInvestment` structure (`srcAccount`, `refId`) on `RecurringTransaction` and `Transaction` to link interest income schedules to their source FD or bond investment
- Source Investment and Reference No. fields in Add/Edit Recurring dialog, shown when type is Income and category contains "Interest"; reference list excludes already-redeemed FD/bond refs
- Recorded transactions for interest income schedules silently inherit `sourceInvestment` from their parent recurring schedule

### Fixed
- INCOME recurring schedules now correctly store the destination bank account in `toAccountId` (was incorrectly stored in `fromAccountId`), fixing prefill in Record dialog and enabling correct cash flow projection
- Financial planning calculator now routes interest income recurring schedules into the bond/FD interest buckets instead of post-tax income; linked schedules are excluded from the salary/income total to prevent double-counting
- App version and build fields in `app-config.json` are now re-stamped on every launch when the running version differs from what was last recorded
- `RecurringTransaction` payment limit: `paymentsMade` is now incremented before checking `isPaymentLimitReached` when auto-recording past-due occurrences

## v0.0.1-Heron — 2026-04-11

- Start of new build
