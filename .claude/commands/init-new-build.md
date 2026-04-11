# Init New Build

Initialize a new build series. Takes one argument: the build name (e.g. `Heron`, `Ibis`).

Usage: `/init-new-build <BuildName>`

The argument is available as `$ARGUMENTS`.

---

## Step 1 — Validate starting state and create branch

Run the following checks in order. Stop and report the specific failure if any condition is not met.

### 1a — Must be on main
```bash
git branch --show-current
```
If the result is not `main`, stop:
> Current branch is not `main`. Please switch to `main` before running this command.

### 1b — Working tree must be clean
```bash
git status --porcelain
```
If there is any output (staged, unstaged, or untracked files), stop:
> Working tree is not clean. Please commit or stash all changes before running this command.

### 1c — Must be in sync with origin/main
```bash
git fetch origin
git rev-list --count --left-right main...origin/main
```
If the output is not `0\t0`, stop:
> Local main is not in sync with origin/main. Please pull or push as needed before running this command.

### 1d — Create the new branch
All checks passed. Create and switch to the new branch:
```bash
git checkout -b dev/$ARGUMENTS
```

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
