# Style Refactor Plan — Sanchay JavaFX

> Produced as part of Phase 1 Audit per `.claude/skills/javafx-style-refactor.md`.
> **No code changes have been made yet.** This document is for review before any work begins.

---

## Audit Summary

### CSS Architecture (what exists today)

| File | Scope | Purpose |
|---|---|---|
| `css/theme.css` | App-wide | Colour tokens on `.root` — well structured |
| `css/components.css` | App-wide | Reusable component classes |
| `css/layout.css` | App-wide | Structural layout (sidebar, header, etc.) |
| `css/screens/reports.css` | Loaded globally by `MainWindow` + `UiUtils` | Report tab styles, chart tweaks |
| `css/screens/planning.css` | Loaded globally by `MainWindow` + `UiUtils` | Financial planning screen styles |
| `css/screens/help.css` | Loaded by `MainWindow` only | Help screen styles |

No FXML files exist — all UI is constructed in Java. That eliminates one vector but makes the Java inline-style problem the dominant issue.

### Inline Style Inventory

| Category | Count | Files Affected |
|---|---|---|
| `.setStyle()` calls | ~52 | 18 files |
| `Color.web(hex)` hardcoded calls | ~20 | 8 files |
| `dot.setFill(Color.web(hex))` | 6 | `UiUtils`, `AccountsScreen`, `HelpScreen`, `CategoriesScreen`, `ProfileScreen`, `SettingsScreen` |
| `.setPadding(new Insets(...))` | ~45 | 20+ files |

### Top Offending Files (by inline style count)

1. **`DashboardScreen.java`** — 6 `setStyle()` + 4 `setPadding()` + hardcoded hex
2. **`ExpenseTrendTab.java`** — 6 `setStyle()` (row backgrounds + typography)
3. **`ImportCompleteDialog.java`** — 4 `setStyle()` with complex multi-property inline blocks
4. **`AccountsScreen.java`** — 4 `setStyle()` + `Color.web()` + `dot.setFill()`
5. **`TransactionsScreen.java`** — 4 `setStyle()` (all data-driven colour switches)
6. **`UiUtils.java`** — 4 `setStyle()` (Text node fills, datepicker styling)
7. **`MarketValueHistoryDialog.java`** — 4 `setStyle()` (ternary colour logic in table cells)
8. **`CategoriesScreen.java`** — 4 `setStyle()` + `Color.web()` (active/inactive state logic)

---

## Problems Found

### P1 — `card-wrapper` class is undefined

`CashFlowForecastTab` applies `getStyleClass().add("card-wrapper")` to the chart container. **This class does not exist in any CSS file.** The chart card is currently unstyled. Should be either defined or replaced with the existing `.card` class.

### P2 — Card class fragmentation (4 variants + 1 undefined)

The app uses `card`, `table-card`, `card-summary`, `card-welcome`, and `card-wrapper` (undefined). Only `card`, `table-card`, `card-summary`, and `card-welcome` have CSS definitions. `FinancialPlanningScreen` even has a comment saying its stat card "matches the DashboardScreen.summaryCard pattern" — copying a pattern instead of referencing a shared class.

### P3 — Brand hex literals bypass the token system

`#3db89a` (= `-brand-light`), `#2a8a7a` (= `-brand-mid`), `#f0a500` (= `-brand-accent`), `#0f3d4a` (= `-text-primary`) appear as raw hex in 15 Java files via `Color.web()`, `buildSectionLabel()` calls, and `setStyle()`. The token system exists and is correct — it's just not being used consistently.

### P4 — `rgba(42,138,122,x)` pattern is repeated 30+ times in CSS and leaks into Java

`rgba(42,138,122,x)` is the rgba expansion of `-brand-mid` at various opacities. It appears 30+ times in `components.css`, in `layout.css`, in `reports.css`, and also in Java `setStyle()` calls in `DashboardScreen` and `ExpenseTrendTab`. The CSS files should define named tokens for the alpha variants; the Java calls should use CSS classes.

### P5 — `buildSectionLabel()` in `UiUtils` takes a hardcoded hex string

`UiUtils.buildSectionLabel(text, hexColor)` passes the dot colour as a runtime hex string. Callers pass `"#3db89a"`, `"#f0a500"` etc. — all of which map to named brand tokens. The dot fill cannot use CSS (Shape fill), but the caller should pass a constant, not a raw string.

### P6 — `screens/help.css` is not included in `UiUtils.applyStylesheet()`

`MainWindow` loads all 6 stylesheets. `UiUtils.applyStylesheet()` (used by all dialogs) loads only 5 — it omits `screens/help.css`. This means any dialog that opens while on the Help screen and uses `.help-guide-card` or other help-specific classes will be unstyled. Low impact today but a latent bug.

### P7 — State-conditional styling via `setStyle()` instead of PseudoClass

`AccountsScreen` does:
```java
name.setStyle(active ? "-fx-text-fill: -brand-dark;" : "-fx-text-fill: -text-hint;");
starLbl.setStyle(acc.isFavourite() ? "-fx-text-fill: -brand-accent; ..." : "-fx-text-fill: -text-hint; ...");
```
`CategoriesScreen` does the same for active/inactive categories. These are boolean states that belong in a PseudoClass + CSS rule, not in conditional `setStyle()` calls that run on every rebuild.

### P8 — Typography applied inline instead of via CSS classes

Multiple files apply font size, weight, and colour via `setStyle()` with no corresponding CSS class:
- `EarningsDialog`: `"-fx-font-weight: bold; -fx-font-size: 13px;"`
- `HelpDialog`: `"-fx-font-size: 13px; -fx-underline: true; -fx-padding: 4 0;"` (link-style button)
- `DashboardScreen`: `"-fx-font-size: 20px;"` (welcome icon)
- `AddEditRecurringDialog`: `"-fx-text-fill: -text-hint; -fx-font-style: italic;"`
- `AccountsScreen`: `"-fx-padding: 5px 10px;"` on reorder button

### P9 — `ExpenseTrendTab` row backgrounds are entirely inline

The category row background (`rgba(42,138,122,0.07)`), sub-category border, and total row background (`rgba(42,138,122,0.13)`) are all `setStyle()` calls. These are structural row patterns that appear every time the table is rebuilt — they should be CSS classes applied to the region elements.

### P10 — `ImportCompleteDialog` builds UI entirely inline

The import result card uses 4 complex `setStyle()` blocks with linear-gradient, hardcoded hex colours (`#f0fdf4`, `#16a34a`, `#bbf7d0`, `#f8fbfc`, `#7aa4b0`), font sizes, and borders. Some are data-driven (success vs. neutral state) but the two states could be two CSS classes instead.

### P11 — `PostRetirementProjectionPanel` uses hardcoded `#f0f8f6`

The outer-phase row background is `"-fx-background-color: #f0f8f6;"` — this is exactly the `-surface-teal-faint` token already defined in `theme.css`.

### P12 — `screens/reports.css` and `screens/planning.css` loaded globally despite being "screen" files

Both are in a `screens/` folder implying they are screen-scoped, but they are loaded globally. This naming is misleading. Their content is legitimately shared (reports CSS is used by dialogs opened from the forecast tab; planning CSS is used by planning sub-panels). They should either be renamed or moved to `components.css`.

---

## Legitimate Inline Style Exceptions (do not touch)

The following are intentional and documented — they must be left as-is:

| Location | Reason |
|---|---|
| `CashFlowForecastTab` — chart series `setStyle()` | Chart series node stroke/colour is runtime-computed per account |
| `DashboardScreen.summaryCard()` — stripe `setStyle()` | Stripe colour is data-driven per card type |
| `FinancialPlanningScreen.summaryCard()` — same pattern | Same reason |
| `PlanningSectionCard` — `dot.setStyle()` | Dot colour is data-driven |
| `TransactionsScreen` — `val.setStyle()` for positive/negative | Colour switches on runtime data |
| `RedeemPanel` — `gainLossLbl.setStyle()` | Gain/loss colour is computed at runtime |
| `SplashScreen` — `Color.web()` for animation circles | Programmatic animation graphics — no CSS equivalent |
| `UiUtils.navArrow()` — `Text.setStyle()` | `Text` nodes don't support `-fx-fill` via style classes |
| `UiUtils.stepDescFlow()` — `Text.setStyle()` | Same `Text` node limitation |
| `AccountDialog` — `hint.setStyle("-fx-font-size: 11px;")` | Has explanatory comment; 11px is below any defined utility size |
| `ProfileScreen.memberColor()` — avatar colour array | Programmatically generated avatar colours; data-driven |

---

## Remediation Plan

### Step 1 — Fix the `card-wrapper` bug (P1)

**File:** `css/components.css`

Define `.card-wrapper` as an alias or variant of `.card` (likely same as `.card` but without internal padding since the chart fills the container). This is a one-line bug fix that should be done first because it is currently causing invisible missing styling.

---

### Step 2 — Consolidate the `rgba(42,138,122,x)` alpha tokens in `theme.css` (P4)

**File:** `css/theme.css`

Add named looked-up colour tokens for the brand-mid alpha variants that are used throughout the CSS files:
```
-brand-mid-08:  rgba(42,138,122,0.08)   — hover tint
-brand-mid-10:  rgba(42,138,122,0.10)   — ...
-brand-mid-12:  rgba(42,138,122,0.12)   — subtle dividers
-brand-mid-15:  rgba(42,138,122,0.15)   — border muted
-brand-mid-18:  rgba(42,138,122,0.18)   — card border, table divider
-brand-mid-22:  rgba(42,138,122,0.22)   — field border
-brand-mid-25:  rgba(42,138,122,0.25)   — stronger border
-brand-mid-30:  rgba(42,138,122,0.30)   — selected/focus border
```
Then do a find-replace across all CSS files to use the tokens. This eliminates the magic-number rgba pattern in CSS and makes the Java `setStyle()` uses easier to replace with CSS classes.

---

### Step 3 — Add CSS classes for the `ExpenseTrendTab` row patterns (P9)

**File:** `css/screens/reports.css`

Add three classes:
- `.trend-row-category` — teal-faint background for category header rows
- `.trend-row-subcategory` — teal-faint border bottom for sub-category rows
- `.trend-row-total` — slightly stronger teal background for the total row

Then replace the 6 `setStyle()` calls in `ExpenseTrendTab` with `getStyleClass().add(...)`.

---

### Step 4 — Add CSS classes for state-driven label styling; replace with PseudoClass (P7, P8)

**Files:** `css/components.css`, `AccountsScreen.java`, `CategoriesScreen.java`

Add:
- `.account-name-inactive` — `text-hint` colour + no weight change
- `.account-star-active` / `.account-star-inactive` — brand-accent vs text-hint
- `.category-name-inactive` — text-hint + italic

Replace the conditional `setStyle()` calls with `PseudoClass` toggling or direct style class switching (`getStyleClass().add/remove`).

---

### Step 5 — Add CSS classes for recurring inline typography patterns (P8)

**File:** `css/components.css`

Add:
- `.text-link-button` — for the "About Sanchay" link-style button in `HelpDialog`
- `.text-result-value` — for the bold + 13px inline calc label in `EarningsDialog`
- `.inv-type-hint` — for the italic hint label in `AddEditRecurringDialog`
- `.btn-compact` — for the reorder button's `"-fx-padding: 5px 10px;"` override in `AccountsScreen`

Replace the `setStyle()` calls with the new classes.

---

### Step 6 — Fix the `PostRetirementProjectionPanel` token bypass (P11)

**File:** `PostRetirementProjectionPanel.java`

Replace:
```java
setStyle(outerPhase ? "-fx-background-color: #f0f8f6;" : "");
```
with:
```java
setStyle(outerPhase ? "-fx-background-color: -surface-teal-faint;" : "");
```
This is a one-line change. The inline style stays (data-driven toggle) but uses the token instead of a hardcoded hex.

---

### Step 7 — Replace hardcoded `Color.web(hex)` calls with named constants (P3, P5)

**Files:** `UiUtils.java`, `AccountsScreen.java`, `CategoriesScreen.java`, `ProfileScreen.java`, `SettingsScreen.java`, `HelpScreen.java`, `RecurringScreen.java`

All calls to `Color.web("#3db89a")` are for `-brand-light`. All calls to `Color.web("#f0a500")` are for `-brand-accent`. Define constants in `UiUtils`:
```java
public static final String HEX_BRAND_LIGHT  = "#3db89a";
public static final String HEX_BRAND_MID    = "#2a8a7a";
public static final String HEX_BRAND_ACCENT = "#f0a500";
```
Replace all raw hex strings with these constants. This does not eliminate the `Color.web()` pattern (which is necessary for `Shape.fill`) but consolidates the literal values so future palette changes are a single edit.

The `buildSectionLabel(text, hexColor)` API is already in `UiUtils` and the hex is passed as a parameter. Update the callers to pass the constants instead of literal strings.

---

### Step 8 — Add CSS classes for `ImportCompleteDialog` states (P10)

**File:** `css/components.css`, `ImportCompleteDialog.java`

The dialog has two visual states per stat box: success (green palette) and neutral (teal-muted). Define:
- `.import-stat-box` — base stat box (white background, border, radius, padding)
- `.import-stat-box-success` — modifier: green background + border
- `.import-stat-box-neutral` — modifier: muted teal background + border
- `.import-stat-count` — the large count number (18px bold)
- `.import-stat-label` — small uppercase label (10px bold)

The icon header gradient is a branding element that could also be a CSS class (`.import-success-header`). Replace the `setStyle()` calls with class assignments.

---

### Step 9 — Fix `screens/help.css` omission from `UiUtils.applyStylesheet()` (P6)

**File:** `UiUtils.java`

Add `"/com/sanchay/css/screens/help.css"` to the `STYLESHEETS` array so dialogs opened in the help screen context receive the same stylesheet set as the main window.

---

### Step 10 — Rename `screens/reports.css` → `css/reports.css` and `screens/planning.css` → `css/planning.css` (P12)

**Files:** `css/screens/reports.css`, `css/screens/planning.css`, `MainWindow.java`, `UiUtils.java`

Both files are globally loaded, making the `screens/` subdirectory misleading. Move them up one level to reflect that they are app-wide component stylesheets, not scoped to individual screens. Update the three load paths (`MainWindow`, `UiUtils`).

This is purely structural cleanup and carries zero visual risk.

---

### Step 11 — Unify card classes: define `.card-wrapper` as a no-padding card variant (P2)

**File:** `css/components.css`, then verify consumers

Audit all four card class usages:
- `.card` — general purpose card with 16px padding
- `.table-card` — card that contains a full-bleed table (no internal padding)
- `.card-summary` — compact stat card used in dashboard and planning header
- `.card-welcome` — dashboard welcome banner card
- `.card-wrapper` — currently undefined (fix in Step 1); chart container card with no padding

After Step 1 defines `.card-wrapper`, document the intended use of each variant in a CSS comment block so future developers don't add yet another variant.

---

## Execution Order and Risk Assessment

| Step | Risk | Effort | Visual Impact |
|---|---|---|---|
| 1 — Fix `card-wrapper` bug | None | Tiny | Adds missing chart card styling |
| 2 — Alpha tokens in `theme.css` | Low | Small | None — purely internal token names |
| 3 — `ExpenseTrendTab` row classes | Low | Small | None if classes are defined correctly |
| 4 — PseudoClass state labels | Medium | Medium | None if CSS classes match existing inline styles exactly |
| 5 — Recurring typography classes | Low | Small | None |
| 6 — Token in `PostRetirementProjectionPanel` | None | Tiny | None |
| 7 — `Color.web()` constants | None | Small | None — same hex, just via constant |
| 8 — `ImportCompleteDialog` states | Medium | Medium | None if states replicate current inline values |
| 9 — Help CSS in `applyStylesheet()` | None | Tiny | Fixes latent missing styling in dialogs |
| 10 — Rename screen CSS files | None | Tiny | None |
| 11 — Card class documentation | None | Tiny | None |

Steps 1, 2, 6, 7, 9, 10 are low-risk mechanical changes.
Steps 3, 5 require care to match existing visual output exactly.
Steps 4, 8 require thorough visual testing of the affected screens.

---

## Out of Scope

- `setPadding(new Insets(...))` calls — these are layout positioning, not visual styling. Migrating them to CSS padding is possible but offers little UX value and high merge risk. Leave as-is.
- `SplashScreen` programmatic circle animation — entirely runtime graphics, no CSS equivalent.
- Chart series stroke/fill in `CashFlowForecastTab` — runtime computed, documented exception.
- `DashboardScreen`/`FinancialPlanningScreen` stripe colour `setStyle()` — data-driven, documented exception.
