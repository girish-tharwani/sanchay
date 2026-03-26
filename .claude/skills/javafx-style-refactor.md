---
name: javafx-css-refactor
description: Refactor and consolidate styling in Java + JavaFX applications that have accumulated inconsistent UX code over time. Use this skill whenever the user asks to clean up, standardise, consolidate, or refactor CSS or inline styling in a JavaFX project. Trigger on phrases like "fix my styling", "consolidate CSS", "remove inline styles", "style cleanup", "refactor look and feel", "UX consistency", "theming", "design system", or any mention of haphazard, scattered, or duplicated JavaFX styling code. Also trigger when the user mentions one screen class importing styles from another, hardcoded colours or fonts in Java code, or mixed approaches to styling across screens. This skill applies to .css files used by JavaFX (not web CSS — JavaFX CSS is a subset with its own property names like -fx-background-color), .java files containing inline style calls, and FXML files with inline style attributes.
---

# JavaFX CSS Refactoring Skill

This skill guides the systematic refactoring of styling in JavaFX applications where look-and-feel code has become inconsistent over time — inline styles mixed with CSS, screens pulling style classes from unrelated screens, hardcoded colours and fonts scattered through Java code, and no single source of truth for the application's visual identity.

The goal is to produce a clean, maintainable styling architecture with a central theme CSS file, consistent naming conventions, and zero inline style overrides in Java or FXML code.

## Important Context 
JavaFX CSS Is Not Web CSS: JavaFX uses a CSS-like syntax but with its own property names and conventions. All JavaFX CSS properties are prefixed with `-fx-`. Understanding this distinction is critical — do not suggest web CSS properties.
Examples: All illustrations, style names and code samples in this file are for examples only. Drive the actual values as per user supplied code base.
**─────**
**EXAMPLE (illustrative only — not from the user's codebase):**

Web CSS:
```css
background-color: #336699;
font-size: 14px;
border-radius: 4px;
```

JavaFX CSS equivalent:
```css
-fx-background-color: #336699;
-fx-font-size: 14px;
-fx-background-radius: 4px;
```
**─────**

JavaFX also supports looked-up colours (similar to CSS custom properties) and derives colours using `derive()` and `ladder()` functions. These are central to building a theme system.

---

## Phase 1: Audit the Current Codebase

Before changing anything, build a complete picture of the current styling situation. This audit drives every decision that follows.

### Step 1.1 — Inventory All Styling Touchpoints

Scan the entire codebase for every place styling is applied. There are four vectors to check:

1. **CSS files** — Find all `.css` files in the project. Note which ones are loaded application-wide vs. per-screen. Check for duplicate or conflicting selectors across files.

2. **Inline styles in Java** — Search for `.setStyle(` calls across all `.java` files. These are the most common source of styling drift because developers use them for quick fixes without updating the CSS.

3. **Inline styles in FXML** — Search for `style=` attributes in `.fxml` files. These have the same problem as Java inline styles.

4. **Programmatic styling in Java** — Search for calls like `.setFont(`, `.setTextFill(`, `.setPadding(`, `.setBackground(`, and similar JavaFX Node property setters that control appearance. These are harder to spot than `.setStyle()` because they look like normal Java code.

### Step 1.2 — Catalogue Colour and Font Usage

Extract every colour value and font specification from all four vectors above. Build a table of:

- The colour value (hex, rgb, or named)
- Where it appears (file, line, context)
- What it seems to represent (primary action, error state, background, disabled text, etc.)

Do the same for fonts — family, size, and weight. You will often find the same logical colour expressed as slightly different hex values across the codebase (e.g., `#336699` in one place and `#346799` in another). Flag these near-duplicates — they are almost certainly meant to be the same colour and represent copy-paste drift.

### Step 1.3 — Map Cross-Screen Style Dependencies

Identify cases where one screen's controller or FXML references style classes that are defined in another screen's CSS file. This is a major maintenance risk because changing one screen's styles can break a completely different screen with no obvious connection.

Document every such dependency: which class, where it is defined, and where it is consumed.

### Step 1.4 — Produce the Audit Report

Summarise findings in a clear report before proposing changes. The report should include:

- Total count of inline style calls (Java + FXML)
- Total count of programmatic style-setters in Java
- Number of CSS files and their loading scope
- Colour palette as-is (with near-duplicates grouped)
- Font usage summary
- Cross-screen style dependencies
- Top offending files (ranked by number of inline overrides)

Present this to the user before moving to Phase 2. The user needs to validate the audit — they may know that certain inline styles exist for good reasons (e.g., dynamic theming, accessibility overrides, runtime-computed values).

---

## Phase 2: Design the Target Architecture

### Step 2.1 — Define the Colour Palette Using Looked-Up Colours

JavaFX supports looked-up colours, which work like design tokens. Define all colours in a root-level selector in the main theme CSS file using the `.root` selector. Every other rule in the application should reference these tokens rather than hardcoding hex values.

**─────**
**EXAMPLE (illustrative only — this is a sample palette, not a prescription):**

```css
.root {
    /* Primary palette */
    -fx-primary: #2563EB;
    -fx-primary-hover: derive(-fx-primary, -10%);
    -fx-primary-light: derive(-fx-primary, 40%);

    /* Semantic colours */
    -fx-success: #16A34A;
    -fx-warning: #D97706;
    -fx-error: #DC2626;

    /* Neutral scale */
    -fx-neutral-50: #F8FAFC;
    -fx-neutral-100: #F1F5F9;
    -fx-neutral-200: #E2E8F0;
    -fx-neutral-700: #334155;
    -fx-neutral-900: #0F172A;

    /* Semantic mappings */
    -fx-app-background: -fx-neutral-50;
    -fx-text-primary: -fx-neutral-900;
    -fx-text-secondary: -fx-neutral-700;
    -fx-border-default: -fx-neutral-200;
}
```
**─────**

The `derive()` function is powerful here — it creates lighter or darker variants of a base colour without introducing new hardcoded hex values. This means changing `-fx-primary` automatically updates every derived shade.

Name tokens semantically (what they *mean*) rather than visually (what they *look like*). `-fx-error` is better than `-fx-red` because the error colour might change to orange in a future redesign, but its meaning stays the same.

### Step 2.2 — Define Typography Tokens

Set font properties on `.root` so they cascade to all children:

**─────**
**EXAMPLE (illustrative only):**

```css
.root {
    -fx-font-family: "Segoe UI", "Helvetica Neue", sans-serif;
    -fx-font-size: 14px;
}
```
**─────**

Then create style classes for the typography scale rather than setting font sizes inline:

**─────**
**EXAMPLE (illustrative only):**

```css
.text-heading-1 { -fx-font-size: 24px; -fx-font-weight: bold; }
.text-heading-2 { -fx-font-size: 20px; -fx-font-weight: bold; }
.text-body      { -fx-font-size: 14px; }
.text-caption   { -fx-font-size: 12px; -fx-text-fill: -fx-text-secondary; }
```
**─────**

### Step 2.3 — Define Reusable Component Classes

Create style classes for common UI patterns used across multiple screens. The naming convention should make it obvious what the class does. Adopt a consistent naming scheme — BEM-like or simple descriptive prefixes both work, but pick one and stick with it.

**─────**
**EXAMPLE (illustrative only — showing the pattern, not the specific components your app needs):**

```css
/* Buttons */
.btn-primary {
    -fx-background-color: -fx-primary;
    -fx-text-fill: white;
    -fx-padding: 8 16;
    -fx-background-radius: 4;
    -fx-cursor: hand;
}
.btn-primary:hover {
    -fx-background-color: -fx-primary-hover;
}
.btn-primary:disabled {
    -fx-opacity: 0.5;
    -fx-cursor: default;
}

/* Cards / panels */
.card {
    -fx-background-color: white;
    -fx-background-radius: 8;
    -fx-border-color: -fx-border-default;
    -fx-border-radius: 8;
    -fx-padding: 16;
    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 4, 0, 0, 2);
}

/* Form fields */
.field-label {
    -fx-text-fill: -fx-text-secondary;
    -fx-font-size: 12px;
    -fx-padding: 0 0 4 0;
}
```
**─────**

### Step 2.4 — Decide the File Structure

The CSS should be organised so that there is a single source of truth for shared styling and optional per-screen files only when a screen has genuinely unique styling needs.

Recommended structure:

```
src/main/resources/css/
├── theme.css            ← colour tokens, typography tokens, .root styles
├── components.css       ← reusable component classes (buttons, cards, forms, tables)
├── layout.css           ← structural layout classes (sidebar, header, content-area)
└── screens/
    └── dashboard.css    ← styles unique to the dashboard screen (if any)
```

Most screens should need zero screen-specific CSS — the component and layout classes should cover them. A screen-specific CSS file is only justified when a screen has truly unique visual elements that are not reused anywhere else.

Load `theme.css`, `components.css`, and `layout.css` at the application level (in the `Application.start()` method or equivalent). Screen-specific CSS files are loaded only by their owning screen.

---

## Phase 3: Refactor — Screen by Screen

Work through the codebase one screen at a time. This approach is safer than a big-bang rewrite because each screen can be visually verified before moving to the next.

### Step 3.1 — For Each Screen

1. **Replace inline `.setStyle()` calls with style classes.** Find each `.setStyle()` call, determine what it is doing, and either map it to an existing component class or create a new one if needed. Then replace the inline call with `.getStyleClass().add("class-name")`.

**─────**
**EXAMPLE of a bad-to-good transformation (illustrative only):**

Before (inline style in Java — avoid this pattern):
```java
submitButton.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white; "
    + "-fx-padding: 8 16; -fx-background-radius: 4;");
```

After (using a style class — preferred pattern):
```java
submitButton.getStyleClass().add("btn-primary");
```
**─────**

2. **Replace programmatic style-setters with style classes.** Calls like `.setFont()`, `.setTextFill()`, `.setBackground()` should be replaced with CSS classes unless the value is truly dynamic (computed at runtime based on data).

**─────**
**EXAMPLE of a bad-to-good transformation (illustrative only):**

Before (programmatic styling — avoid this pattern):
```java
titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
titleLabel.setTextFill(Color.web("#0F172A"));
```

After (using a style class — preferred pattern):
```java
titleLabel.getStyleClass().add("text-heading-1");
```
**─────**

3. **Replace inline styles in FXML with style classes.**

**─────**
**EXAMPLE of a bad-to-good transformation (illustrative only):**

Before (inline FXML style — avoid this pattern):
```xml
<Button text="Save" style="-fx-background-color: #2563EB; -fx-text-fill: white;" />
```

After (using a style class — preferred pattern):
```xml
<Button text="Save" styleClass="btn-primary" />
```
**─────**

4. **Replace hardcoded colour values in CSS with looked-up colour tokens.** Any hex, rgb, or named colour in a CSS rule that matches a token in your palette should be replaced with the token reference.

5. **Remove cross-screen style imports.** If screen A's CSS file is loaded by screen B, identify which classes screen B actually uses, move those classes to `components.css`, and remove the cross-screen import.

### Step 3.2 — Acceptable Exceptions to Inline Styling

Not every inline style is wrong. Some legitimate cases remain:

- **Truly dynamic values** — If a colour or size is computed from runtime data (e.g., a progress bar colour that shifts from green to red based on a metric), that must be set in Java. Even here, prefer using `setStyle()` with a looked-up colour variable where possible, or use a PseudoClass with CSS rules for different states.
- **User-customisable values** — If the user can choose their own colours or font sizes in a preferences screen, those values must be applied at runtime.
- **One-off animation targets** — Transitional style values during animation sequences.

When an inline style is genuinely necessary, add a comment explaining why CSS cannot handle it:

**─────**
**EXAMPLE (illustrative only):**

```java
// Inline style required: colour is computed from live sensor data at runtime
sensorIndicator.setStyle("-fx-background-color: " + computedColour + ";");
```
**─────**

---

## Phase 4: Validate

### Step 4.1 — Visual Regression Check

After refactoring each screen, verify it visually. The refactored screen should look identical to the original — the goal of this phase is to consolidate *how* styles are applied, not to change *what* the application looks like. Any visual changes should be intentional and agreed with the user (e.g., unifying two slightly different blues into one consistent blue).

### Step 4.2 — Search for Residual Inline Styles

After all screens are done, run a final search across the codebase:

- `grep -rn "\.setStyle(" --include="*.java"` — should return only the justified exceptions
- `grep -rn "\.setFont(" --include="*.java"` — should return zero or near-zero results
- `grep -rn "\.setTextFill(" --include="*.java"` — should return zero or near-zero results
- `grep -rn "style=" --include="*.fxml"` — should return zero or near-zero results
- `grep -rn "setBackground(" --include="*.java"` — check each remaining call

### Step 4.3 — Check for Unused CSS Classes

Look for style classes defined in CSS that are no longer referenced anywhere in Java or FXML. These are dead code left over from the old styling and should be removed.

### Step 4.4 — Verify No Cross-Screen Dependencies Remain

Confirm that no screen loads another screen's CSS file. Every screen should only rely on the application-level CSS files plus (optionally) its own screen-specific CSS.

---

## Common Pitfalls to Watch For

These are patterns that frequently appear in legacy JavaFX codebases and should be addressed during refactoring:

1. **Style class collisions** — Two CSS files defining the same class name with different rules. When both are loaded, the last-loaded one wins, causing subtle bugs. Resolve by consolidating into a single definition in `components.css`.

2. **Specificity fights** — An inline `.setStyle()` call overrides CSS class rules because inline styles have higher specificity in JavaFX. If you replace the CSS class but miss removing the inline style, the inline style silently wins and nothing visually changes — until someone later removes the inline style and the screen breaks.

3. **Colour near-duplicates** — Hex values like `#333`, `#333333`, `#343434`, and `#303030` that are all meant to be "dark text" but have drifted apart through copy-paste. Consolidate these to a single token.

4. **Font stacking** — Different parts of the app using different font families (Arial in one screen, Segoe UI in another, Helvetica in a third) without any design intent. Unify to a single font stack in `.root`.

5. **Magic numbers** — Padding, margin, and spacing values that appear as raw numbers without any pattern (8 in one place, 10 in another, 12 in a third). Define a spacing scale and use it consistently.

6. **Using `-fx-base` and `-fx-default-button` without understanding cascade** — These are built-in JavaFX looked-up colours that affect many controls. Overriding them on `.root` changes the look of every button, scrollbar, and menu in the application. Be intentional about it.

7. **Forgetting pseudo-class states** — Replacing a button's normal style but forgetting to define `:hover`, `:pressed`, `:focused`, and `:disabled` states. The button then reverts to the default Modena theme on hover, creating a jarring flash.

---

## Naming Conventions

Adopt a consistent naming convention for style classes. Whatever convention the team chooses, apply it uniformly. Here are two common approaches — pick one:

**Option A — Descriptive with hyphens (simpler):**
`btn-primary`, `card-header`, `field-error`, `text-heading-1`

**Option B — BEM-inspired (more structured):**
`btn--primary`, `card__header`, `field--error`, `text--heading-1`

Either works. Consistency matters far more than which convention is chosen. Document the chosen convention and enforce it in code reviews.

---

## Working With the User

Throughout this process, communicate clearly about trade-offs:

- **Scope vs. speed** — Refactoring every screen at once is thorough but slow. Offer to prioritise the screens with the most technical debt first.
- **Visual changes** — If consolidating near-duplicate colours means some screens will look slightly different, flag this explicitly and get approval.
- **Testing burden** — Every refactored screen needs visual verification. If the project has automated UI tests, run them after each screen. If not, the user needs to manually check.

When presenting refactored code, always show the before and after side by side so the user can verify the intent is preserved.

---

## Quick Reference: JavaFX CSS Properties Most Commonly Abused Inline

This is a checklist of properties that are frequently set via `.setStyle()` or programmatic setters and should almost always be in CSS instead:

| Property | Inline Java equivalent | Move to CSS? |
|---|---|---|
| `-fx-background-color` | `.setStyle(...)` or `.setBackground(...)` | Yes |
| `-fx-text-fill` | `.setTextFill(...)` | Yes |
| `-fx-font-size` | `.setFont(Font.font(...))` | Yes |
| `-fx-font-family` | `.setFont(Font.font(...))` | Yes |
| `-fx-font-weight` | `.setFont(Font.font(..., FontWeight.BOLD, ...))` | Yes |
| `-fx-padding` | `.setPadding(new Insets(...))` | Yes |
| `-fx-border-color` | `.setStyle(...)` | Yes |
| `-fx-background-radius` | `.setStyle(...)` | Yes |
| `-fx-border-radius` | `.setStyle(...)` | Yes |
| `-fx-opacity` | `.setOpacity(...)` | Usually yes |
| `-fx-cursor` | `.setCursor(...)` | Yes |
| `-fx-effect` (dropshadow) | `.setEffect(new DropShadow(...))` | Yes |

The exception column: if the value is computed from runtime data, it stays in Java with a comment explaining why.

---

## Checklist: Definition of Done

A screen is considered fully refactored when:

- [ ] Zero `.setStyle()` calls remain (except documented exceptions)
- [ ] Zero `.setFont()`, `.setTextFill()`, `.setBackground()` calls remain for static styling
- [ ] Zero inline `style=` attributes remain in FXML
- [ ] All colour values reference looked-up colour tokens, not hardcoded hex
- [ ] All font specifications use typography classes, not inline values
- [ ] No CSS imports from other screens' CSS files
- [ ] All style classes use the project's naming convention
- [ ] Pseudo-class states (hover, pressed, focused, disabled) are defined for interactive elements
- [ ] Visual appearance matches the pre-refactoring state (unless a change was intentionally agreed)
- [ ] No unused style classes remain in CSS files