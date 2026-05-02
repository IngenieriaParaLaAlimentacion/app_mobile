?---
name: review-pr-feedback
description: Analyze review feedback on a PR (typically develop → main), determine which comments are valid, and produce an actionable plan with exact file:line changes. Use when the user wants to address PR review comments or asks about PR feedback.
argument-hint: "[PR number]"
context: fork
allowed-tools: Read, Grep, Glob, Bash(gh *), Agent, Write, Edit
---

# Review PR Feedback

Analyze review feedback on a PR, determine which comments are valid, and produce an actionable implementation plan with exact file:line changes.

## Pre-loaded PR Data

Fetch the PR number and all review data using the Bash tool before starting analysis. If `$ARGUMENTS` is provided, use it as the PR number. Otherwise, detect it:

```
gh pr list --base main --head develop --json number --jq '.[0].number'
```

Once you have the PR number (referred to as NUM below), run these commands in parallel using the Bash tool:

1. **PR Details**: `gh pr view NUM --json title,body,state,baseRefName,headRefName`
2. **Commits**: `gh pr view NUM --json commits --jq '.commits[] | .messageHeadline'`
3. **Issue Comments**: `gh pr view NUM --json comments --jq '.comments[] | "**@\(.author.login)**:\n\(.body)\n---"'`
4. **Inline Review Comments**: `gh api repos/OWNER/REPO/pulls/NUM/comments --jq '.[] | "\(.path):\(.line) @\(.user.login) [reply_to:\(.in_reply_to_id // "none")]\n\(.body)\n---"'` (get OWNER/REPO from `gh repo view --json nameWithOwner -q .nameWithOwner`)
5. **Review Bodies**: `gh api repos/OWNER/REPO/pulls/NUM/reviews --jq '.[] | "state:\(.state) @\(.user.login)\n\(.body)\n---"'`
6. **Changed Files**: `gh pr diff NUM --name-only`
7. **Full Diff**: `gh pr diff NUM`

---

## Instructions

If the PR number shows `NO_PR_FOUND`, list recent PRs with `gh pr list` and ask the user which one to review.

### Step 1: Parse review comments into discrete findings

Issue comments often contain **multiple findings in a single message** (structured with headers like "### Bugs", "### Improvements", numbered items, etc.).

For each comment:
1. Split it into individual findings — each numbered item or distinct issue is one finding
2. For each finding, extract:
   - **What**: The issue described
   - **Where**: File path and code snippet mentioned (if any)
   - **Severity implied by reviewer**: Bug, improvement, nitpick, etc.

### Step 2: Read the affected files

For each file referenced in any finding:
- Read the **current version** fully using the Read tool
- Understand the surrounding context beyond just quoted snippets
- Check if the issue still exists (it may have been fixed in a later commit)

### Step 3: Analyze each finding

For every discrete finding, determine:

1. **Is it valid?** — Does it identify a real issue in the current code?
2. **Already fixed?** — Cross-reference with the commit list. If a later commit addresses it, mark as "Already addressed"
3. **Why valid/invalid?** — Brief reasoning referencing the actual current code
4. **Priority**: Critical (must fix) | Important (should fix) | Nice-to-have (optional) | Invalid (disagree)

Classify by category:
- **Bug/Correctness** — Logic error, missing edge case, wrong behavior
- **Performance** — N+1 queries, missing indexes, unnecessary computation
- **Security** — Injection, auth bypass, data leak
- **Convention** — Doesn't follow project patterns (CLEAN arch, error DTOs, etc.)
- **Readability** — Naming, structure, complexity
- **Nitpick** — Style preference, minor wording

Use the checklist in `${CLAUDE_SKILL_DIR}/review-checklist.md` to catch project-specific issues.

### Step 4: Propose exact changes for valid findings

For each valid finding that isn't already addressed, specify the fix:

```markdown
### Finding {N}: {brief title}
**Source**: @{user} — "{quoted relevant excerpt}"
**Verdict**: {Valid|Partially valid|Invalid} — {category}
**Priority**: {Critical|Important|Nice-to-have}
**Why**: {1-2 sentence reasoning against current code}

**Proposed fix**:
**File**: `{path}:{line_start}-{line_end}`

Current:
```{lang}
{existing code}
```

Proposed:
```{lang}
{fixed code}
```
```

For invalid findings, still explain why you disagree — the user makes the final call.

### Step 5: Generate summary

Present a summary table:

| # | Finding | File | Verdict | Priority | Category | Status |
|---|---------|------|---------|----------|----------|--------|
| 1 | brief   | path | Valid   | Critical | Bug      | To fix |
| 2 | brief   | path | Valid   | Important| Perf     | Already fixed |
| 3 | brief   | path | Invalid | —        | Nitpick  | Disagree |

Then list the proposed changes grouped by priority (Critical first).

### Step 6: Save the plan

Write the full analysis to `docs/plans/{date}_pr-{number}-review-fixes.md` using today's date.

### Step 7: Ask for confirmation

```
I've analyzed {N} findings from {M} review comments:
- {X} valid and need fixing ({Y} critical, {Z} important)
- {A} already addressed in later commits
- {W} invalid/disagreed

The full analysis is at `docs/plans/{date}_pr-{number}-review-fixes.md`.

Would you like me to:
1. Implement all valid fixes now
2. Review the plan first and cherry-pick which to implement
3. Adjust any verdicts before proceeding
```

## Guidelines

- **Read before judging**: Always read the full file context, not just quoted snippets. A finding might seem wrong in isolation but be valid in context (or vice versa).
- **Parse carefully**: A single comment can contain 5-10 distinct findings. Don't treat one comment as one finding.
- **Check current state**: The code may have changed since the review. Always verify against the current file, not the diff.
- **Respect project conventions**: Check CLAUDE.md and existing patterns before dismissing something as a nitpick.
- **Be honest about disagreements**: If a reviewer is wrong, say so clearly with evidence. The user trusts your technical judgment.
- **Don't over-fix**: Only propose changes that address the finding. No sneaking in refactors.
- **Batch by file**: When multiple findings affect the same file, consider interactions — one fix might resolve or conflict with another.