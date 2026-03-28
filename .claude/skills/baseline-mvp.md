# Skill: Baseline MVP version for project

## Purpose
Guide Claude to learn and baseline project versioning for a new MVP

## Rules
- NO CODE CHANGE AT ALL
- Ask user for MVP suffix if not provided already

## What to exclude
- Use .gitignore file at project root as a guide to what not to include in learning and analysis

## Clean up comments
- [] Clean up the comments to keep only the comments that are significant to understand the functionality in long run, remove trivial comments
- [] Remove all the references to previous versions, change logs, bug reports, bug numbers, enhancement numbers, code refactoring, UI/UX style realignment etc.

## Improve cluade.md and readme.md
- [] Remove all the references to previous versions, change logs, bug reports, bug numbers, enhancement numbers etc.
- [] Update it to align to the project functionality

## Archive existing changelog.md
- [] Read the last change log version from the changelog.md and add that version as a suffix to file name e.g.CHANGELOG_v0.2.4.md

## Update the version in POM.xml
- [] Update the version in pom.xml to 1.0.0 with the user prvides suffix appended to it

```xml
<version>1.0.0-MVP1</version>
```
## Initialize a changelog.md
- Initialize a new CHANGELOG.md

## What NOT to Do
- Do not change any code
- Do not add new dependencies or libraries

## Output Format
1. Briefly explain what you changed and why
2. Flag the issues
3. Flag anything you were unsure about

