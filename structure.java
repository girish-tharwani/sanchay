Use .claude/skills/code-refactor.md
Problem Statement:
As I examined the code for this project, I noticed that it has some haphazard functional logic code. It uses a good class structure with ui classes, service classes etc but there appears to be
- some amount of code duplication
- some dead code
- non-standard wiring patterns across classes e.g. a function written in UI class being called from another UI classes, instead of that function being written in a separate utility class.
- There are some dialog boxes that open from main screens. In some places, these dialog boxes are implemented in separate java classes but in other cases, they are withing the java class for parent screen.
- some clases have grown too big and need to be refactored
Task:
Acting as an expert Java designer and developer, evaluate how bad the code situation is and create a remediation plan with a planned and structured approach as per .claude/skills/code-refactor.md
Do not make any code changes. First just produce a high level numbered step-by-step plan and store it at ./.claude/plans/code-refactor.md for review.