# Git Push — Versioning and Changelog Rules

Follow these rules every time changes are committed and pushed to GitHub.

---

## 1. Version bump (pom.xml)

Every push must include a version increment in `pom.xml`. The version follows the pattern `MAJOR.MINOR.PATCH-SNAPSHOT`.

### Which segment to increment

| Situation | Action | Example |
|-----------|--------|---------|
| Bug fixes, small UI tweaks, minor additions to existing features | Increment **PATCH** only | `0.1.0` → `0.1.1` |
| New features, new transaction types, new screens, significant additions | Increment **PATCH** only (until told otherwise) | `0.1.2` → `0.1.3` |
| User explicitly asks to bump the minor version | Increment **MINOR**, reset PATCH to 0 | `0.1.5` → `0.2.0` |

> **Rule:** Never increment MINOR (to v0.2.x or beyond) unless the user explicitly requests it. When in doubt, bump PATCH.

The version string in `pom.xml` always keeps the `-SNAPSHOT` suffix:
```xml
<version>0.1.1-SNAPSHOT</version>
```

---
## 2. Update README.md and CLAUDE.md if needed
Verify if README.md and CLAUDE.md needs to be updated to keep them aligned with the changes done since last code push and update them. Do not include versioning information in them as they go in separate CHANGELOG.md


---

## 3. Update CHANGELOG.md

Maintain a `CHANGELOG.md` in the project root. Each push adds a new entry at the **top** of the file, under a heading with the new version number and today's date.

### Format

```markdown
## v0.1.1 — 2026-03-22

### Added
- Brief description of new functionality

### Changed
- Brief description of changes to existing behaviour

### Fixed
- Brief description of bug fixes (keep these concise — no implementation details)
```

Only include sections that are relevant (omit `### Fixed` if there are no fixes, etc.).

---

## 4. Order of operations

1. Determine the correct new version (PATCH bump unless told otherwise)
2. Update `<version>` in `pom.xml`
3. Update `CHANGELOG.md` — add new entry at the top
4. Stage both files along with all other changed files
5. Commit with the version as the commit message (e.g. `v0.1.1-SNAPSHOT`)
6. Push to GitHub

---

## 5. What NOT to do

- Do not skip the version bump — every push gets a new version
- Do not increment MINOR without explicit user instruction
- Do not increment MAJOR under any circumstances (reserved for user decision)
- Do not remove the `-SNAPSHOT` suffix
