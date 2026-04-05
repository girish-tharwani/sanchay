## v0.0.2-Barbet — 2026-04-05

### Added
- Help & Support screen: clicking Help in the sidebar now opens a full screen instead of a dialog, with the same dismissable Get Started banner as Dashboard and a WebView rendering USER-GUIDE.md as formatted HTML (headings, tables, lists, blockquotes, code blocks, working TOC anchor links)

### Changed
- EarningsDialog: default tab for a new member's first income source is now Structured Salary instead of Simple Income

### Fixed
- WebView placed outside any ScrollPane to avoid JavaFX 21 RTTexture NPE caused by ScrollPane's internal CacheFilter compositing a WebView through its cache pipeline

---

## v0.0.1-Barbet — 2026-04-05

