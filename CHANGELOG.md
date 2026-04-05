## v1.0.0-Barbet — 2026-04-05
- Release baseline

## v0.0.3-Barbet — 2026-04-05

### Fixed
- Help screen missing from packaged installer: `javafx.web` added to jlink `--add-modules` and jpackage `--java-options` in `build.sh` so the WebView runtime is included in the bundled JRE
- InvestmentPanel: Reference No field is now mandatory (validated on save) for Fixed Deposit and Bond transactions

---

## v0.0.2-Barbet — 2026-04-05

### Added
- Help & Support screen: clicking Help in the sidebar now opens a full screen instead of a dialog, with the same dismissable Get Started banner as Dashboard and a WebView rendering USER-GUIDE.md as formatted HTML (headings, tables, lists, blockquotes, code blocks, working TOC anchor links)

### Changed
- EarningsDialog: default tab for a new member's first income source is now Structured Salary instead of Simple Income

### Fixed
- WebView placed outside any ScrollPane to avoid JavaFX 21 RTTexture NPE caused by ScrollPane's internal CacheFilter compositing a WebView through its cache pipeline

---

## v0.0.1-Barbet — 2026-04-05

