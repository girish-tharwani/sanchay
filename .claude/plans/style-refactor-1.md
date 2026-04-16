# Style Refactor Plan — Sanchay JavaFX

> Status: DRAFT — awaiting review before any code changes begin.
> Methodology: follows `.claude/skills/javafx-style-refactor.md`

---

## Audit Summary

### Scale of the problem

| Metric | Count |
|---|---|
| `.setStyle()` calls in Java | **57** across 14 files |
| Hardcoded hex / rgba colour strings in Java | **~70+** (distinct values; many duplicated across files) |
| `setPadding(new Insets(...))` calls | **~60+** (scattered magic numbers) |
| Programmatic `setBackground()` | 1 (`SplashScreen` — justified) |
| `setEffect(DropShadow)` | 1 (`SplashScreen` — justified) |
| FXML inline `style=` | 0 (no FXML in project) |
| CSS files | **1** (`app.css`, ~1 500 lines) |
| CSS stylesheet load sites | 3 distinct entry-points (see below) |

### Top offenders by inline `.setStyle()` count

1. `TransactionsScreen.java` — **14** calls
2. `DashboardScreen.java` — **6** calls
3. `AccountsScreen.java` — **6** calls
4. `UiUtils.java` — **6** calls
5. `RecurringScreen.java` — **5** calls
6. `SplashScreen.java` — **5** calls (partially justified — standalone scene, no CSS loaded)
7. `FinancialPlanningScreen.java` — **3** calls

### CSS architecture — current state

- A single `app.css` carries **everything**: root tokens, layout, sidebar, tables, badges, buttons, forms, DatePicker, charts, planning-specific classes, import-dialog classes — no separation of concerns.
- The `.root {}` block is well-formed with a proper looked-up colour token system (`-brand-dark`, `-color-error`, `-text-hint`, etc.). This is a solid foundation.
- Despite the token system existing in CSS, **Java code frequently bypasses it** with raw hex strings instead of referencing tokens.

### Key issues found

#### Issue 1 — Inline styles that should be CSS classes

Many `.setStyle()` calls construct multi-property style strings for static, non-data-driven elements. Examples:

- `RecurringScreen` warning icon: `"-fx-font-size: 22px; -fx-text-fill: -color-error;"` — identical pattern repeated in `TransactionsScreen` and likely others.
- `TransactionsScreen.ccStat()` label: `"-fx-font-size: 10px; -fx-font-weight: 600; -fx-text-fill: -text-hint;"` — a clear `stat-label` candidate.
- `CategoriesScreen` name label: `"-fx-font-size: 13px; -fx-font-weight: bold;"` mixed with colour tokens.

#### Issue 2 — Hardcoded hex colours in Java instead of CSS tokens

Tokens exist in `.root` but Java code routinely bypasses them:

- `#27AE60` / `#C62828` / `#E74C3C` passed as raw strings to `setStyle()` and as constructor arguments — same colours are defined in CSS as `-color-income`, `-color-error`, `-color-expense`.
- `#3db89a` (`-brand-light`) hard-coded in 6+ Java files as a colour argument to `sectionLabel()`, `buildGroup()`, `Color.web()` calls.
- `#0f3d4a` (`-brand-dark`) duplicated as a Java string in `TransactionsScreen`, `CashFlowForecastTab`, and `AccountsScreen`.
- `rgba(42,138,122,0.18)` (teal-border opacity variant) appears in **both** `app.css` and 3 Java `setStyle()` calls — no token exists for this specific alpha value.
- `#856404` (amber warning text) is a one-off hardcoded hex with no CSS token or equivalent (`-color-warning` in CSS is `#B7450D`, a different hue — this discrepancy is flagged in a comment but unresolved).
- `#666` in `AccountDialog` hint label has no token mapping at all.

#### Issue 3 — Duplicated private `sectionLabel()` helper (3 copies)

The `sectionLabel(text, dotColor)` pattern — coloured Circle dot + uppercase Label — appears as three separate private methods:

- `RecurringScreen.sectionLabel()` (line 310)
- `AmbiguousMatchDialog.sectionLabel()` (line 136)
- `RecurringMatchDialog.sectionLabel()` (line 164)

And a functionally identical fourth variant:

- `FinancialPlanningScreen.startSectionCard()` (line 1056) — same dot + label header pattern but wrapped in a card.

These should be consolidated into `UiUtils`.

#### Issue 4 — `setPadding()` magic numbers everywhere

`new Insets(16)`, `new Insets(24)`, `new Insets(12, 16, 12, 16)` etc. are scattered across 60+ call sites with no consistent spacing scale. No spacing tokens exist in CSS. This is the most pervasive low-severity issue.

#### Issue 5 — Stylesheet loaded at 3 different entry-points

- `MainWindow` loads `app.css` on the main scene — correct.
- `UiUtils.applyStylesheet()` re-loads it on every Dialog's DialogPane — acceptable JavaFX pattern.
- `FirstRunWizard` loads it directly on its own scene — acceptable (separate window).
- `CashFlowForecastTab` propagates the parent scene's stylesheets to child dialogs — correct pattern.

No cross-screen CSS file imports exist. This is clean. The only structural issue is that `app.css` is monolithic.

#### Issue 6 — `app.css` is monolithic (1 500 lines, no separation)

Planning-screen-specific classes (`fp-corpus-pill-*`, `fp-events-table`), import-dialog classes (`import-match-row`, `match-confirm-badge`), and chart-series overrides live alongside global reset rules in a single file. This makes maintenance hard — a developer cannot tell which classes are global vs. screen-specific.

#### Issue 7 — Missing or incomplete interactive states

Several inline-styled buttons (`deleteButton` in RecurringScreen/TransactionsScreen, the `aboutBtn` in HelpDialog) have their normal state set inline but no `:hover`, `:pressed`, or `:disabled` states defined. Because the inline style overrides any CSS class, these buttons cannot pick up state styles from CSS without removing the inline style first.

#### Issue 8 — `SplashScreen` — justified exceptions cluster

`SplashScreen` runs in a standalone scene before `app.css` is loaded. Its inline styles are mostly legitimate. However, the typography styles (`appName`, `subtitle`, `tagline`) could be served by loading `app.css` on the splash scene itself, reducing the exception count from 5 to ~2 (gradient background + progress bar accent which need runtime alpha values).

---

## Remediation Plan

### Phase 1 — Lay the foundation (no visible change to any screen)

**Step 1 — Split `app.css` into structured files** ✅ DONE

Reorganise the single CSS file into:

```
src/main/resources/com/sanchay/css/
├── theme.css          ← .root tokens only (colours, typography, spacing scale)
├── components.css     ← reusable classes: buttons, badges, cards, form fields, tables, sidebar
├── layout.css         ← structural: .main-panel, .filter-bar, .dialog-header-bar, .pending-item
└── screens/
    ├── planning.css   ← fp-corpus-pill-*, fp-events-table, planning-specific rules
    ├── import.css     ← import-match-row, match-confirm-badge, import-preview classes
    └── reports.css    ← cash-flow-chart overrides, chart series colours
```

Load `theme.css + components.css + layout.css` at app level in `MainWindow` and `UiUtils.applyStylesheet()`. Each screen loads its own screen CSS in addition.

**Step 2 — Add missing spacing tokens to `theme.css`**

Define a spacing scale in `.root`:

```css
-spacing-xs:  4px;
-spacing-sm:  8px;
-spacing-md: 16px;
-spacing-lg: 24px;
-spacing-xl: 32px;
```

This allows future `setPadding()` migration to reference a scale. (Migrating `setPadding()` calls is lower priority — addressed in Phase 3.)

**Step 3 — Add missing colour tokens to `theme.css`**

Several colours are used in Java but have no CSS token:

| Hex value | Where used | Proposed token |
|---|---|---|
| `rgba(42,138,122,0.18)` | 3 Java + many CSS | `-border-teal-faint` |
| `#666` | `AccountDialog` hint | map to existing `-text-hint` (`#9E9E9E`) or create `-text-dim` |
| `#856404` | amber warning text | `-color-warning-text` |
| `#C62828` / `#c0392b` | near-duplicate reds | unify to existing `-color-error` |
| `#595959` | CC stat neutral label | map to `-text-secondary` or new `-text-neutral` |
| `#1A1A2E` / `#1a1a2e` | combo cell text | map to existing `-text-label` |

---

### Phase 2 — Extract reusable components (no visible change)

**Step 4 — Consolidate `sectionLabel()` into `UiUtils`** ✅ DONE

Move the coloured-dot + section-label pattern to `UiUtils.buildSectionLabel(String text, String dotColor)`. Remove the three private copies in `RecurringScreen`, `AmbiguousMatchDialog`, `RecurringMatchDialog`.

Note: the `dotColor` argument stays as a String because it is data-driven (different callers pass different brand colours). The Circle `setFill()` call is a legitimate exception per the skill guide.

**Step 5 — Add new CSS utility classes for repeated inline patterns** ✅ DONE

Add these classes to `components.css`:

| Proposed class | Replaces | Used in |
|---|---|---|
| `.icon-danger` | `"-fx-font-size: 22px; -fx-text-fill: -color-error;"` | RecurringScreen, TransactionsScreen |
| `.icon-large` | `"-fx-font-size: 22px;"` or `"-fx-font-size: 24px;"` | AccountsScreen chevron, HelpDialog hero |
| `.stat-label` | `"-fx-font-size: 10px; -fx-font-weight: 600; -fx-text-fill: -text-hint;"` | TransactionsScreen.ccStat() |
| `.stat-value` | `"-fx-font-weight: bold; -fx-font-size: 13px;"` (color stays inline — data) | TransactionsScreen.ccStat() |
| `.text-hint-italic` | `"-fx-text-fill: -text-hint; -fx-font-style: italic;"` | AddEditRecurringDialog |
| `.text-link` | `"-fx-font-size: 13px; -fx-underline: true; -fx-padding: 4 0;"` | HelpDialog aboutBtn |
| `.text-warning-sm` | `"-fx-font-size: 12px;"` + warning colour | TransactionsScreen warn label |
| `.content-separator` | `"-fx-background-color: rgba(42,...); -fx-pref-width:1; ..."` | TransactionsScreen filterSep |

---

### Phase 3 — Screen-by-screen inline style removal

Work through screens in order of inline style count (highest first). For each screen:
1. Replace all `.setStyle()` calls with the new CSS classes from Phase 2.
2. Replace raw hex strings with looked-up colour token references.
3. Verify all interactive elements have `:hover`, `:pressed`, `:focused`, `:disabled` states in CSS.

**Priority order:**

| Screen | `.setStyle()` count | Effort estimate |
|---|---|---|
| 1. `TransactionsScreen` | 14 | High — several `ccStat` + delete-button + import-dialog patterns |
| 2. `DashboardScreen` | 6 | Medium — card stripe colour is data-driven (stays inline with comment) |
| 3. `AccountsScreen` | 6 | Medium — group dot colours are data-driven; name label + value label refactorable |
| 4. `UiUtils` | 6 | Medium — `navArrow()` Text node (documented exception); datepicker month header (legitimate) |
| 5. `RecurringScreen` | 5 | Low-Medium — warning icon → `.icon-danger`; delete button → `.btn-danger` |
| 6. `SplashScreen` | 5 | Special — load `app.css` on splash scene to reduce exceptions to ~2 |
| 7. `FinancialPlanningScreen` | 3 | Low — card stripe colour data-driven; dot inline |
| 8. `TransactionDialog` | 3 | Low — gain/loss colour is runtime (stays); one static label |
| 9. `CategoriesScreen` | 2 | Low |
| 10. `ReportsScreen` | 2 | Low |
| 11. `HelpDialog` | 2 | Low |
| 12. `EarningsDialog` | 1 | Trivial |
| 13. `AddEditRecurringDialog` | 1 | Trivial |
| 14. `AccountDialog` | 1 | Trivial (`#666` → token) |

**MarketValueHistoryDialog** — 2 `setStyle()` calls inside `TableColumn` cell factories with data-driven colour (gain/loss direction). These are legitimate exceptions — add comments documenting why.

---

### Phase 4 — Cleanup and validation

**Step 6 — Remove unused CSS classes**

After all screen refactoring, search for CSS classes defined in `app.css` that are no longer referenced in any Java file. Remove dead rules.

**Step 7 — Final grep verification**

Run these checks to confirm zero unresolved inline styles remain:

```bash
grep -rn "\.setStyle("  --include="*.java"   # should return only documented exceptions
grep -rn "setBackground(" --include="*.java"  # should return only SplashScreen
grep -rn "#[0-9a-fA-F]" --include="*.java"   # should return only chart palettes + data-driven values with comments
```

**Step 8 — Verify no cross-screen CSS imports**

Already clean — confirm stays clean post-split.

---

## Decisions — locked in

1. **Splitting `app.css`** — ✅ **Yes, split.** `theme.css + components.css + layout.css` loaded app-wide; screen-specific files loaded per-screen. Pure reorganisation, zero rule changes.

2. **`setPadding()` migration** — ✅ **Deferred.** Padding is structural layout, not visual appearance. Migrating ~60 `Insets` calls to CSS would require single-use classes with no design benefit. Out of scope.

3. **`SplashScreen`** — ✅ **Load `app.css` on the splash scene.** Eliminates 3 of 5 inline styles (typography). Remaining 2 (gradient `Background` + progress bar accent) are runtime-constructed — stay with comments.

4. **Chart colour palettes** — ✅ **Accept as legitimate exceptions.** JavaFX chart APIs require literal CSS colour strings; CSS looked-up colour names cannot be used as Java `String` arguments. Consolidate `CashFlowForecastTab.ACCOUNT_COLOURS` and `UiUtils.CHART_PALETTE` into a single `ChartPalette` constants section in `UiUtils`, with each constant annotated to its CSS token counterpart (e.g. `// mirrors -brand-mid in theme.css`).

---

## What will NOT change

- Visual appearance of any screen — this refactor is purely structural.
- Legitimate inline exceptions: runtime colour values (gain/loss direction, balance sign, CC outstanding), `SplashScreen` gradient, Shape `.setFill()` calls for data-driven dots, chart palette arrays.
- The colour palette and design language — tokens are being consolidated, not redesigned.
