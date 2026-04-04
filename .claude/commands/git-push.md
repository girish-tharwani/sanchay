# Git Push - Build Agami

Follow these steps every time changes are committed and pushed to GitHub.

## Step 1 — Determine the new version

Read the current version from `pom.xml`. It follows `MAJOR.MINOR.PATCH-SNAPSHOT`.

- Default: increment **PATCH** only (bug fixes, UI tweaks, new features — everything)
- Increment **MINOR** and reset PATCH to 0 **only** if the user explicitly requested it in this session
- Never increment **MAJOR** under any circumstances
- Always keep the existing suffix `-Agami` suffix

## Step 2 — Update pom.xml

Set the new version in `pom.xml`:
```xml
<version>1.0.1-Agami</version>
```

## Step 3 — Update CHANGELOG.md

Add a new entry at the **top** of `CHANGELOG.md` using today's date. Only include sections that apply:
```markdown
## v1.0.1 — YYYY-MM-DD

### Added
- ...

### Changed
- ...

### Fixed
- ...
```

Keep descriptions concise. No implementation details in Fixed entries.

## Step 4 — Update README.md and CLAUDE.md

Review both files and update them with new information to align with the changes made in the new version. Do **not** add version or changelog information to either file — that belongs only in `CHANGELOG.md`.

## Step 5 — Stage, commit, and push
```bash
git add -A
git commit -m "v1.0.1-MVP1"
git push
```

Use the new version string as the commit message.