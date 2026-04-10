## v0.0.2-Toucan — 2026-04-10

### Added
- JUnit 5 `@Tag` two-axis test tagging system: `smoke`/`full` (depth) × `transactions`/`accounts`/`recurring` (feature)
- `build.sh` shortcuts: `test-smoke`, `test-full`, `test-transactions`, `test-accounts`, `test-recurring`

### Changed
- `build.sh` header comment updated with full test selection reference table and ad-hoc `-Dgroups` examples
- `build.sh` refactored to use a shared `MVN_CMD` variable, eliminating repeated Maven invocation strings

### Fixed
- `TransactionsScreenFullTest.openFab()`: dialog detection now uses `Platform.runLater` + `CountDownLatch` + `Window.getWindows()` directly on the FX thread, replacing a TestFX `interact()` polling loop that failed to detect the dialog stage

## v0.0.1-Toucan — 2026-04-10
- Start of new build 