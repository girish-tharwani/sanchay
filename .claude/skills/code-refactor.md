---
name: javafx-refactor
description: Use this skill whenever the user wants to refactor, restructure, clean up, or improve the architecture of a Java or JavaFX application. Triggers include requests to eliminate code duplication, remove dead code, fix inconsistent patterns, extract dialog classes, separate UI from business logic, create utility classes, standardize wiring between classes, improve overall code organization, clean up comments, or improve README documentation. Also use when the user asks to audit or review a JavaFX codebase for structural issues, apply MVC/MVP patterns, or bring consistency to how screens and dialogs are organized. If the user mentions messy code, spaghetti code, tech debt, or inherited codebases in the context of Java/JavaFX, use this skill.
---

# JavaFX Application Refactoring Skill

You are helping the user refactor a Java/JavaFX application that works correctly and has consistent UI/UX styling via CSS, but has accumulated structural debt: code duplication, dead code, inconsistent class responsibilities, and non-standard wiring between components. The goal is to improve internal code quality without changing visible behavior or breaking the existing CSS styling.

## Core Principles

1. **Never break what works.** Every refactoring step must preserve existing behavior. If you aren't sure whether a change is safe, flag it and ask.
2. **Do NOT change behavior, only structure.** Do not change business logic, method signatures, or public API contracts.
3. **Do NOT rename public methods or variables.** Renaming breaks external contracts and callers you may not see.
4. **One concern at a time.** Don't try to fix duplication, dead code, and wiring in a single pass. Work in focused phases.
5. **Respect the existing style.** The app already has CSS-based styling that works. Don't touch FXML layout or CSS unless the user explicitly asks.
6. **Small, verifiable steps.** Each change should be compilable and testable on its own. Avoid large-scale rewrites that make it impossible to tell what broke if something goes wrong.
7. **Keep changes scoped.** Don't refactor beyond what's asked. Don't add new dependencies or libraries.

---

## Phase 0: Audit and Catalog

Before changing anything, build a complete picture of the codebase. This phase produces a written report — no code changes.

### What to exclude from analysis

Use the `.gitignore` file at the project root as a guide for what to exclude from learning and analysis (build output, generated files, local config, etc.).

### Step 0.1 — Map the class structure

Read every Java file. For each class, record:
- Its package and fully qualified name
- What it extends or implements (e.g., `extends Application`, `implements Initializable`, `extends Stage`, `extends Dialog`)
- Whether it is a UI class, a service/business-logic class, a model/POJO, a utility, or something else
- Its approximate line count
- A one-sentence summary of what it does

Present this as a table or structured list, grouped by package.

### Step 0.2 — Catalog dialogs and their hosting patterns

For every dialog or popup in the application, record:
- What triggers it (which screen, which user action)
- Whether it lives in its own Java class or is defined inline within a parent screen class
- If inline: how many lines of dialog code are embedded, and whether it has its own FXML or builds its scene graph in code
- Whether it returns data to the caller (and how: callback, property binding, direct field access, return value from `showAndWait()`)

Flag inconsistencies explicitly. For example: "DialogA is a separate class with its own FXML; DialogB is 80 lines of code inside MainScreenController.java; DialogC is a separate class but builds its UI in code rather than FXML. These should follow one consistent pattern."

### Step 0.3 — Identify code duplication

Look for:
- **Exact duplicates**: identical or near-identical methods appearing in multiple classes
- **Structural duplicates**: methods that do the same thing with minor variations (different field names, slightly different validation rules, etc.)
- **Boilerplate patterns**: repeated blocks for things like alert creation, table column setup, form validation, file chooser configuration, date formatting, number formatting

For each duplication cluster, note which classes contain the duplicated code and estimate how many lines are involved.

### Step 0.4 — Identify dead code

Look for:
- Methods that are never called from anywhere (search for usages across the entire codebase — be thorough, check FXML `onAction` references too)
- Imports that are unused
- Private fields that are written but never read, or declared but never used
- Entire classes that nothing references
- Commented-out blocks longer than a few lines
- `@FXML`-annotated fields or methods that don't correspond to any fx:id or event handler in the matching FXML file

Mark confidence level: "definitely dead" vs. "possibly dead — needs user confirmation" (e.g., if a method could be called via reflection or FXML that you haven't seen).

### Step 0.5 — Map cross-class wiring

For every case where one UI class directly calls a method on another UI class (controller-to-controller coupling), document:
- The calling class and method
- The called class and method
- What the call is actually trying to accomplish (refresh a table? pass data? trigger navigation?)
- Why this is problematic (tight coupling, hidden dependencies, makes classes non-reusable)

Also look for:
- Service classes that hold references to UI classes (services should not know about the UI)
- Static mutable state used to pass data between screens
- Singleton controllers or "god objects" that accumulate responsibilities

### Step 0.6 — Flag logic issues and stubs (do not fix yet)

While reading the code, note but do not act on:
- **Logic bugs**: any code that appears to produce incorrect results or handle edge cases wrong
- **Missing feature implementations**: placeholders, TODO comments, or methods with empty/stub bodies
- **Stubbed code**: methods that return hardcoded/dummy values

These are flagged for the user's awareness, not resolved during the refactoring.

### Step 0.7 — Produce the audit report

Compile all findings into a single structured report with these sections:
1. **Class inventory** (from 0.1)
2. **Dialog inconsistencies** (from 0.2)
3. **Duplication clusters** (from 0.3)
4. **Dead code candidates** (from 0.4)
5. **Wiring problems** (from 0.5)
6. **Flagged logic issues / stubs** (from 0.6)
7. **Recommended refactoring order** — prioritize by risk (low-risk first) and impact (high-impact first). Typically: dead code removal → duplication extraction → dialog extraction → wiring cleanup.

Present this report to the user and get confirmation before proceeding to any code changes.

From here on, the application code should be compilable and testable after completion of each phase.

---

## Phase 1: Dead Code Removal

This is the safest starting point because removing unused code cannot change behavior.

### How to proceed

- Start with the "definitely dead" items from the audit.
- Remove them one class at a time. After each class, confirm it still compiles.
- For "possibly dead" items, ask the user to confirm before removing.
- Remove unused imports last (they are noise but harmless).
- Do NOT remove commented-out code without asking — the user may have left it intentionally as reference.

### What to watch for

- Methods referenced in FXML files (`onAction="#handleSave"`) — make sure you've checked all FXML files, not just the one you think is associated with the class.
- Methods called via reflection (rare in typical JavaFX apps, but possible with custom frameworks).
- Event handlers registered programmatically (e.g., `button.setOnAction(this::handleClick)`).

---

## Phase 2: Extract Duplicated Code and Improve Code Quality

### 2.1 — Decide where extracted code should live

Follow these placement rules:

| What the duplicated code does | Where to put it |
|---|---|
| Pure utility logic (string formatting, date math, number parsing, file path manipulation) | A static utility class in a `util` package, e.g., `DateUtils`, `StringUtils`, `FileUtils` |
| UI helper logic (creating standard alerts, configuring table columns, building common form controls) | A `UIHelper` or `ViewUtils` class in a `ui.util` or `ui.common` package |
| Business/domain logic (validation rules, calculations, data transformations) | A service class in the `service` package, or a domain-specific helper |
| FXML-loading boilerplate | A base controller class or a `FXMLLoaderHelper` utility |

Never put shared logic in a UI controller class just because that's where one copy already lives. The whole point is to decouple.

### 2.2 — Extraction pattern

For each duplication cluster:

1. Write the new method in the appropriate utility/service class.
2. Write it to handle all the variations found across the duplicates. If duplicates differ in small ways (e.g., different field names), parameterize the method.
3. Replace each duplicate call site with a call to the new method.
4. Verify behavior is preserved — the method should do exactly what each original did.
5. If a utility class is getting too large (more than ~300 lines or more than one clear responsibility), split it.

### 2.3 — Naming conventions

Follow the existing project's naming conventions. If there are none, default to:
- Utility classes: `XxxUtils` (e.g., `AlertUtils`, `ValidationUtils`)
- Helper classes: `XxxHelper` (e.g., `TableHelper`, `FormHelper`)
- Service classes: `XxxService` (e.g., `ExportService`, `DataService`)
- Package structure: mirror what already exists — don't introduce a radically different package layout

### 2.4 — Improve code clarity (private methods only)

Within private implementation details (not public API), also address:
- **Long methods**: extract long private methods into smaller, well-named private helpers
- **Poor variable/method naming**: rename private variables and methods to better express their intent — do NOT rename public or package-visible members
- **Deep conditionals**: simplify deeply nested if/else chains using early returns, guard clauses, or extracted boolean methods
- **Single responsibility**: if a private method is doing two unrelated things, split it

---

## Phase 3: Standardize Dialog Implementation

All dialogs should follow one consistent pattern. Here is the recommended standard:

### The standard dialog pattern

Every dialog gets its own Java class file. Each dialog class:

1. **Extends `javafx.scene.control.Dialog<R>`** (where R is the return type) for data-collecting dialogs, **or extends `Stage`** for more complex standalone windows. Pick whichever pattern the codebase already uses more — consistency trumps theoretical purity.
2. **Has its own FXML file** if the rest of the application uses FXML, or builds its UI in code if the rest of the application does that. Match the existing convention.
3. **Lives in a dedicated package or sub-package** (e.g., `ui.dialog` or alongside its parent screen — match existing convention).
4. **Receives input data via constructor parameters or setter methods** — never by reaching into the parent controller's fields.
5. **Returns output data via `Dialog.setResultConverter()`** (if extending Dialog) or via a getter method called after `showAndWait()` (if extending Stage). Never by writing directly into the parent controller's fields.

### Extracting an inline dialog

When a dialog is currently defined inline within a parent controller:

1. Create a new class file for the dialog.
2. Move all the dialog's UI construction code, event handlers, and validation logic into the new class.
3. In the parent controller, replace the inline code with: instantiate the dialog, pass it any required data, call `showAndWait()`, and handle the result.
4. Make sure the dialog class has no reference back to the parent controller. Data flows in via constructor/setters, data flows out via the result.

### What to watch for

- Dialogs that modify the parent's data directly (e.g., adding an item to the parent's observable list). After extraction, the parent should handle this in response to the dialog's return value.
- Dialogs that need to refresh the parent's view. After extraction, the parent calls its own refresh method after the dialog closes — the dialog doesn't trigger it.
- CSS styling — the dialog's scene needs access to the same stylesheets. Typically, add the stylesheet in the dialog's constructor: `getDialogPane().getStylesheets().add(...)` or `scene.getStylesheets().add(...)`.

---

## Phase 4: Fix Cross-Class Wiring

### 4.1 — Eliminate controller-to-controller direct calls

When ControllerA calls ControllerB.someMethod() directly:

1. **Determine what the call is trying to accomplish.** Common cases:
    - **Data passing**: ControllerA is sending data to ControllerB (e.g., passing a selected item to a detail screen). → Use a navigation/mediator service, or pass data via the constructor/factory method of ControllerB.
    - **Triggering a refresh**: ControllerA changed something and wants ControllerB to update. → Use a lightweight event/notification mechanism (JavaFX properties, an event bus, or a shared observable model).
    - **Accessing shared state**: Both controllers need the same data. → Extract shared state into a model or service class that both controllers reference.

2. **Pick the simplest mechanism that fits.** Don't introduce a heavy event bus framework if a simple callback or property binding will do. Rank of preference:
    - **Constructor injection / method parameter** — simplest, most explicit
    - **Shared model/service injected into both controllers** — good for data both screens read and write
    - **JavaFX property binding / listeners** — good for reactive updates
    - **A simple callback interface** — good for "notify me when X happens"
    - **Event bus (e.g., a simple publish/subscribe class)** — only if many-to-many communication is genuinely needed

3. **Never introduce a dependency injection framework** (like Spring or Guice) just for this purpose unless the user explicitly asks for it. A small app doesn't need the ceremony.

### 4.2 — Ensure services don't reference UI classes

If any service class imports or references a controller, Stage, Scene, Node, or any `javafx.*` type:

1. Identify what UI interaction the service is performing (showing an alert? updating a progress bar? writing to a text area?).
2. Extract that interaction behind a callback or interface that the UI layer provides to the service.
3. The service defines what it needs (e.g., `Consumer<String> statusUpdater`); the controller provides it when creating the service.

### 4.3 — Remove static mutable state for data passing

If classes use `static` fields to pass data between screens (a common antipattern in JavaFX apps):

1. Replace with explicit data passing — either via constructor parameters to the next controller, or via a lightweight navigation context/service.
2. Make the previously-static fields instance fields.
3. Verify no other code path relied on the static state.

---

## Phase 5: Final Cleanup and Consistency Pass

After all structural changes:

1. **Standardize access modifiers.** Methods that are only used within their own class should be `private`. Methods used by other classes in the same package should be package-private. Only make things `public` if they genuinely need to be.
2. **Standardize method ordering within classes.** A reasonable convention: constructors and `initialize()` first, then public methods, then private methods, then inner classes. Match whatever the codebase already leans toward.
3. **Ensure consistent error handling.** If some screens show errors via alerts and others via inline labels, note the inconsistency and ask the user which they prefer. Then standardize.
4. **Clean up comments.** Keep only comments that are significant — those explaining non-obvious decisions, gotchas, or intent. Remove trivial comments that restate what the code already says clearly (e.g., `// increment counter` above `count++`). Remove all references to previous versions, bug numbers, changelog entries, and enhancement numbers from comments.
5. **Improve README.md.** Remove all references to previous versions, changelogs, bug reports, and enhancement numbers. Update it to accurately reflect the current project functionality, architecture, and how to build/run the application.
6. **Verify CSS still applies correctly.** Load every screen and dialog, confirm they look the same as before the refactoring.
7. **Update any comments or Javadoc** that reference old class names, removed methods, or changed wiring.

## Phase 6: Update CLAUDE.md and README.MD with new relevant information
1. As this will require review of whole code structure and potentially some structural changes in the code, update CLAUDE.MD and README.md with pertinent information.
---

## How to Apply This Skill

When the user provides their codebase (or a portion of it), follow this workflow:

1. **Read all the code first.** Don't start suggesting changes until you've read every file the user has provided. The audit phase is essential — you can't safely refactor code you haven't fully mapped.

2. **Present the audit report and get buy-in.** The user should agree with your findings and your proposed order of operations before you change anything.

3. **Work in phases.** Complete one phase fully before starting the next. Within each phase, work one file or one duplication cluster at a time.

4. **Show your work.** For each change, explain what you're doing and why. Show the before and after. If a change is complex, walk through the reasoning.

5. **Ask before removing anything ambiguous.** If you're not 100% sure something is dead code or if you're not sure whether a particular wiring pattern was intentional, ask.

6. **Preserve the user's conventions.** If they use camelCase for packages (unusual but it happens), continue using camelCase for packages. If they put FXML files alongside controllers rather than in a resources directory, continue doing that. Don't impose conventions that conflict with what's already there.

7. **Don't gold-plate.** The goal is to clean up the mess, not to redesign the architecture. If the app has a reasonable basic structure (UI classes, service classes, model classes), work within that structure. Don't suggest switching to MVVM, introducing reactive streams, or adding dependency injection unless the user asks for it.

---

## Common Pitfalls to Avoid

- **Don't rename FXML fx:id values or onAction handlers** without also updating the corresponding FXML files. These are string-based references and won't produce compile errors if mismatched — they'll produce runtime errors.
- **Don't move controller classes** without updating the `fx:controller` attribute in the FXML file.
- **Don't change the return type of `Dialog.showAndWait()`** without updating every caller.
- **Don't extract a method into a utility class if it accesses instance state** (like `@FXML`-injected fields). Extract the logic, but keep the field access in the controller — pass the values as parameters.
- **Don't introduce new dependencies** (Maven/Gradle) without asking. A refactoring skill should work with what's already there.
- **Don't touch the module-info.java** exports/opens unless absolutely necessary (e.g., a new package genuinely needs to be opened to javafx.fxml). If you do need to, explain why.

---

## Output Expectations

When producing refactored code:

- Provide complete file contents, not just snippets. The user should be able to drop in the file and have it work.
- If a refactoring step affects multiple files, provide all affected files together.
- Include a brief changelog at the top of each response summarizing what changed and why.
- If you create a new utility or helper class, provide the complete class with appropriate package declaration and imports.
- Preserve existing license headers, copyright notices, and file-level comments.
- Flag any logic issues, bugs, or stubs encountered during the refactor — do not fix them silently.
