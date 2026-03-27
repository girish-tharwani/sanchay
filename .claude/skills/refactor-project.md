# Skill: Baseline Project

## Purpose
Guide Claude to learn and baseline a new project consistently according to standards.

## Rules
- Do NOT change behavior, only structure
- Do NOT rename public methods or variables (breaks contracts)
- Keep changes scoped — don't refactor beyond what's asked
- Preserve all existing comments unless they are outdated

## What to exclude
- Use .gitignore file at project root as a guide to what not to include in learning and analysis

## What to Refactor in code
- [ ] Extract long methods into smaller private methods
- [ ] Remove duplicate and dead code
- [ ] Improve variable/method naming
- [ ] Simplify conditionals (avoid deeply nested if/else)
- [ ] Ensure single responsibility per class/method

## What to Flag but not Change
- [] Chack for any logic issues or bugs in the code but don't fix it yet
- [] Check for any missing feature implementation but don't implement it yet
- [] Check for any stubbed code but don't implement it yet

## Clean up comments
- [] Clean up the comments to keep only the comments that are significant, remove trivial comments
- [] Remove all the references to previous versions, change logs, bug reports, bug numbers, enhancement numbers etc.

## Improve readme.md
- [] Remove all the references to previous versions, change logs, bug reports, bug numbers, enhancement numbers etc.
- [] Update it to align to the project functionality 

## What NOT to Do
- Do not change business logic
- Do not add new dependencies or libraries
- Do not change method signatures

## Output Format
1. Briefly explain what you changed and why
2. Show the refactored code
3. Flag the issues
4. Flag anything you were unsure about

