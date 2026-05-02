---
name: implement-plan
description: Implement an approved technical plan phase by phase, verifying compilation after each step. Use this skill when the user wants to implement a plan from docs/plans/, pick up where they left off on a plan, continue implementing a feature plan, or says anything like "implement the plan," "start the plan," "pick up from phase 2," or "continue the bank accounts plan."
---

# Implement Plan

Implement approved technical plans from `docs/plans/`. Plans contain phases with specific changes and success criteria.

## Getting Started

When given a plan path (or when you can infer it from context):
1. Read the plan completely and check for existing checkmarks (`- [x]`)
2. Read the original ticket and all files mentioned in the plan
3. Read files fully — you need complete context to understand how pieces fit together
4. Start implementing if you understand what needs to be done

If no plan path is provided or inferable, ask for one.

## Implementation Flow

### Follow the plan's intent, not just its letter

Plans are carefully designed, but the codebase may have evolved since they were written. Your job is to:
- Implement each phase fully before moving to the next
- Verify your work compiles after each phase
- Adapt to what you actually find in the code while preserving the plan's intent

### When things don't match

If you encounter a mismatch between the plan and reality, stop and present it clearly:

```
Issue in Phase [N]:
Expected: [what the plan says]
Found: [actual situation]
Why this matters: [explanation]

How should I proceed?
```

This matters because silently deviating from the plan leads to subtle bugs and confused reviews.

## Verification

After completing each phase, run the verification script from the project root:

```bash
bash .claude/skills/implement-plan/scripts/verify.sh
```

This compiles Kotlin and regenerates protos if needed. Fix any issues before moving to the next phase.

For deeper verification context (common failures, manual testing checklist), read `references/verification.md`.

### Update progress as you go

Check off completed items in the plan file itself using Edit. This lets you and the user track exactly where things stand, and makes it easy to resume if interrupted.

## Resuming Work

If the plan has existing checkmarks:
- Trust that completed work is done
- Pick up from the first unchecked item
- Verify previous work only if something seems off during your implementation

## If You Get Stuck

When something isn't working as expected:
1. Read and understand all the relevant code — most issues come from incomplete context
2. Consider if the codebase has evolved since the plan was written
3. Present the mismatch clearly and ask for guidance

Use sub-agents sparingly — mainly for targeted investigation of unfamiliar areas of the codebase.

## Keep momentum

You're implementing a solution, not just checking boxes. Batch verification at natural stopping points (end of a phase), don't let it interrupt your flow within a phase.
