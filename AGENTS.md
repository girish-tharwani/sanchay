u# Repository Guidelines

## Project Structure & Module Organization
Core application code lives under `src/main/java/com/sanchay`. Keep domain models in `model`, business logic and persistence in `service`, and JavaFX screens/dialogs in `ui` with screen-specific subpackages such as `ui/transactions` and `ui/reports`. Static assets and bundled docs live under `src/main/resources`, including CSS in `src/main/resources/com/sanchay/css` and the in-app help file in `src/main/resources/com/sanchay/help/USER-GUIDE.md`. Build outputs go to `target/`, Windows installers to `installer/`, and sample or manual test inputs to `test-data/`.

## Build, Test, and Development Commands
Use Maven from the repo root.

- `mvn javafx:run`: launch the desktop app in development mode.
- `mvn javafx:run -Pdebug`: start with JDWP enabled on port `5005`.
- `mvn clean package`: compile and produce `target/sanchay-app.jar`.
- `mvn shade:shade`: rebuild only the shaded JAR when classes are already compiled.
- `./build.sh package-dist`: create the Windows installer in `installer/`; this depends on local JavaFX jmods and WiX paths configured in `build.sh`.

## Coding Style & Naming Conventions
Target Java 17. Use 4-space indentation, one top-level class per file, and keep package names lowercase under `com.sanchay`. Follow existing naming patterns: nouns for models (`LoanAccount`), `*Service` for business logic, `*Screen` and `*Dialog` for JavaFX UI, and `*Panel` for transaction subforms. Do not hardcode colors or other UI styling in Java; keep reusable styling in CSS tokens and shared classes. Services must remain UI-agnostic and must not import `javafx.*`.

## Testing Guidelines
There is no committed `src/test` suite yet. Until one is added, validate changes with targeted manual runs through affected screens and workflows, using `test-data/` where helpful. When adding tests, place them in `src/test/java` mirroring production packages, and name files `*Test` so Maven can discover them cleanly later.

## Commit & Pull Request Guidelines
Recent history favors short, imperative commit subjects such as `removed unused imports` and release/version tags like `Rel Darter-v1.0.1`. Keep commits focused and descriptive; separate refactors from behavior changes. Pull requests should summarize the user-visible impact, list key files touched, mention any data/config migration concerns, and include screenshots or short recordings for UI changes.

## Configuration & Data Safety
Do not commit user data or local machine configuration. Runtime data belongs outside the repo in the user-selected data folder, while app-level config is written to `%APPDATA%\\sanchay\\app-config.json`. Treat `app-data/`, `logs/`, and `work-folder/` as local working areas unless a task explicitly requires checked-in changes there.
