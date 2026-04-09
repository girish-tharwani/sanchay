# Baseline Release

Prepare a versioned release from the current dev branch, merge to main, and create a release branch and tag.

The user invokes this as `/baseline-release <version>` — the version token is available as `$ARGUMENTS`.

---

## Step 1 — Validate the version argument

The version must match the pattern `vMAJOR.MINOR.PATCH-Suffix` (e.g. `v0.1.3-Finch`).

Run this check:
```bash
echo "$ARGUMENTS" | grep -qE '^v[0-9]+\.[0-9]+\.[0-9]+-[A-Za-z]+$'
```

If it does **not** match, print:

> Error: version "$ARGUMENTS" does not match the required pattern vMAJOR.MINOR.PATCH-Suffix.

Then stop — do not proceed with any further steps.

Extract the `Suffix` part (everything after the last `-`) — you will need it in subsequent steps.

---

## Step 2 — Verify the current branch

Run:
```bash
git branch --show-current
```

The current branch must be exactly `dev/<Suffix>` (case-sensitive, where `<Suffix>` is the part extracted in Step 1).

If it does **not** match, print:

> Error: current branch is "<actual branch>", expected dev/<Suffix>.

Then stop — do not proceed.

---

## Step 3 — Update version strings in project files

Update the version to `$ARGUMENTS` in the following files. Only change lines that already contain the current version number — do not touch unrelated content.

1. **`pom.xml`** — the `<version>` element directly under `<project>` (not inside `<dependencies>` or `<parent>`)
2. **`dependency-reduced-pom.xml`** — same rule as pom.xml
3. **`README.md`** — any line that contains the current version string (badge, header, etc.)

Use the Read tool to read each file first, locate the version, then use the Edit tool to make the targeted replacement.

---

## Step 4 — Add a CHANGELOG entry

Open `CHANGELOG.md`. Add a new entry at the **top** of the file (below any title line if present):

```markdown
## $ARGUMENTS — YYYY-MM-DD

baseline for release
```

Replace `YYYY-MM-DD` with today's date.

---

## Step 5 — Commit and push the dev branch

Stage all modified files and commit. The commit message format is `Rel <Suffix>-v<MAJOR.MINOR.PATCH>` — e.g. for `v0.1.3-Finch` the message is `Rel Finch-v0.1.3`.

```bash
git add -A
git commit -m "Rel <Suffix>-v<MAJOR.MINOR.PATCH>"
git push origin dev/<Suffix>
```

Confirm the push succeeded before continuing.

---

## Step 6 — Merge dev branch into main

```bash
git checkout main
git pull origin main
git merge dev/<Suffix> --no-ff -m "Rel <Suffix>-v<MAJOR.MINOR.PATCH>"
git push origin main
```

Confirm the push succeeded before continuing.

---

## Step 7 — Create the release branch

```bash
git checkout -b release/<Suffix>
git push -u origin release/<Suffix>
```

---

## Step 8 — Tag the release

Create an annotated tag named exactly `<Suffix>` (not the full version, just the suffix):
```bash
git tag -a <Suffix> -m "Rel <Suffix>-v<MAJOR.MINOR.PATCH>"
git push origin <Suffix>
```

---

## Done

Print a summary:
```
Release $ARGUMENTS complete.
  Branch merged : dev/<Suffix> → main
  Release branch: release/<Suffix>
  Tag           : <Suffix>
```
