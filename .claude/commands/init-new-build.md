# Init New Build

Initialize a new build series. Takes one argument: the build name (e.g. `Heron`, `Ibis`).

Usage: `/init-new-build <BuildName>`

The argument is available as `$ARGUMENTS`.

---

## Step 1 — Validate the current branch

Run:
```bash
git branch --show-current
```

The branch **must** be `dev/$ARGUMENTS` (case-sensitive). If it is not, stop immediately and tell the user:

> Current branch is not `dev/$ARGUMENTS`. Please switch to the correct branch before running this command.

Do not proceed further if the branch check fails.

---

## Step 2 — Update version strings

The new version string is: `v0.0.1-$ARGUMENTS`

Update the version in each of these files by replacing whatever the current version string is:

### `pom.xml`
Find the line:
```xml
<version>...</version>
```
(the first occurrence, inside `<project>`, not inside a dependency) and change it to:
```xml
<version>v0.0.1-$ARGUMENTS</version>
```

### `dependency-reduced-pom.xml`
Same replacement — find the project-level `<version>` element and update it to `v0.0.1-$ARGUMENTS`.

### `README.md`
Find any occurrence of the current build version (e.g. a badge, header line, or version reference) and update it to `v0.0.1-$ARGUMENTS`. If there is no version string in README.md, skip this file.

---

## Step 3 — Reset CHANGELOG.md

Replace the **entire contents** of `CHANGELOG.md` with exactly the following (substituting today's date in `YYYY-MM-DD` format and the real build name):

```markdown
## v0.0.1-$ARGUMENTS — YYYY-MM-DD

- Start of new build
```

No other content. No previous entries.

---

## Step 4 — Stage, commit, and push

```bash
git add -A
git commit -m "v0.0.1-$ARGUMENTS"
```

Then check whether the remote branch exists:
```bash
git ls-remote --heads origin dev/$ARGUMENTS
```

- If the remote branch **exists**: `git push`
- If the remote branch **does not exist**: `git push -u origin dev/$ARGUMENTS`
