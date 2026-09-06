---
name: "source-command-create-plan"
description: "Migrated source command `create_plan`"
---

# source-command-create-plan

Use this skill when the user asks to run the migrated source command `create_plan`.

## Command Template

# Implementation Plan

Use this command before implementing any feature, refactor, or migration that touches more than one file. For single-file changes, a lightweight approach (quick summary + go) is fine — reserve this full process for multi-file, multi-phase work.

Create detailed implementation plans through an interactive, iterative process. Be skeptical, thorough, and work collaboratively with the user to produce high-quality technical specifications.

## Initial Response

When this command is invoked:

1. **Check if parameters were provided**:
    - If a file path or ticket reference was provided as a parameter, skip the default message
    - Immediately read any provided files FULLY
    - Begin the research process

2. **If no parameters provided**, respond with:
```
I'll help you create a detailed implementation plan. Let me start by understanding what we're building.

Please provide:
1. The task/ticket description (or reference to a ticket file)
2. Any relevant context, constraints, or specific requirements
3. Links to related research or previous implementations

I'll analyze this information and work with you to create a comprehensive plan.

Tip: You can also invoke this command with a ticket file directly: `/create_plan thoughts/tickets/eng_1234.md`
For deeper analysis, try: `/create_plan think deeply about thoughts/tickets/eng_1234.md`
```

Then wait for the user's input.

## Process Steps

### Step 1: Context Gathering & Initial Analysis

1. **Read all mentioned files completely** (tickets, research docs, plans, JSON) using the Read tool without limit/offset. Read them in the main context before spawning any sub-tasks — sub-tasks lack conversational context, so you need to understand the material first to decompose research effectively.

2. **Spawn research sub-agents in parallel** before asking the user any questions. Use the Agent tool with appropriate `subagent_type`:
    - `codebase-locator` — find all files related to the ticket/task
    - `codebase-analyzer` — understand how the current implementation works
    - If relevant existing docs may exist, use `Explore` agent to search for them

3. **Read all files identified by research tasks** into the main context after they complete, so you have complete understanding before proceeding.

4. **Analyze and verify understanding**:
    - Cross-reference the ticket requirements with actual code
    - Identify any discrepancies or misunderstandings
    - Note assumptions that need verification
    - Determine true scope based on codebase reality

5. **Present informed understanding and focused questions**:
   ```
   Based on the ticket and my research of the codebase, I understand we need to [accurate summary].

   I've found that:
   - [Current implementation detail with file:line reference]
   - [Relevant pattern or constraint discovered]
   - [Potential complexity or edge case identified]

   Questions that my research couldn't answer:
   - [Specific technical question that requires human judgment]
   - [Business logic clarification]
   - [Design preference that affects implementation]
   ```

   Only ask questions that you genuinely cannot answer through code investigation.

### Step 2: Research & Discovery

After getting initial clarifications:

1. **If the user corrects any misunderstanding**, don't just accept it — spawn new research to verify the correct information against the actual codebase before proceeding.

2. **Spawn parallel sub-agents for comprehensive research** using the Agent tool:
    - `codebase-locator` — find specific files related to each aspect of the task
    - `codebase-analyzer` — understand implementation details of relevant components
    - `codebase-pattern-finder` — find similar features to model after
    - `Explore` — for broader searches across the codebase

   Launch multiple agents in parallel for efficiency. Each returns specific file:line references.

3. **Wait for ALL sub-agents to complete** before proceeding.

4. **Present findings and design options**:
   ```
   Based on my research, here's what I found:

   **Current State:**
   - [Key discovery about existing code]
   - [Pattern or convention to follow]

   **Design Options:**
   1. [Option A] - [pros/cons]
   2. [Option B] - [pros/cons]

   **Open Questions:**
   - [Technical uncertainty]
   - [Design decision needed]

   Which approach aligns best with your vision?
   ```

### Step 3: Plan Structure Development

Once aligned on approach:

1. **Create initial plan outline**:
   ```
   Here's my proposed plan structure:

   ## Overview
   [1-2 sentence summary]

   ## Implementation Phases:
   1. [Phase name] - [what it accomplishes]
   2. [Phase name] - [what it accomplishes]
   3. [Phase name] - [what it accomplishes]

   Does this phasing make sense? Should I adjust the order or granularity?
   ```

2. **Get feedback on structure** before writing details

### Step 4: Detailed Plan Writing

After structure approval:

1. **Write the plan** to `thoughts/plans/{descriptive_name}.md`
2. **Use this template structure**:

```markdown
# [Feature/Task Name] Implementation Plan

## Overview

[Brief description of what we're implementing and why]

## Current State Analysis

[What exists now, what's missing, key constraints discovered]

## Desired End State

[A Specification of the desired end state after this plan is complete, and how to verify it]

### Key Discoveries:
- [Important finding with file:line reference]
- [Pattern to follow]
- [Constraint to work within]

## What We're NOT Doing

[Explicitly list out-of-scope items to prevent scope creep]

## Implementation Approach

[High-level strategy and reasoning]

## Phase 1: [Descriptive Name]

### Overview
[What this phase accomplishes]

### Changes Required:

#### 1. [Component/File Group]
**File**: `path/to/file.ext`
**Changes**: [Summary of changes]

```[language]
// Specific code to add/modify
```

### Success Criteria:

#### Automated Verification:
- [ ] Kotlin compilation passes: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:compileCommonMainKotlinMetadata --no-daemon`
- [ ] Proto generation (if applicable): `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew generateProtos --no-daemon`

#### Manual Verification:
- [ ] Feature works as expected when tested via UI on iOS/Android
- [ ] Performance is acceptable
- [ ] Edge case handling verified manually
- [ ] No regressions in related features

---

## Phase 2: [Descriptive Name]

[Similar structure with both automated and manual success criteria...]

---

## Testing Strategy

### Manual Testing Steps:
1. [Specific step to verify feature]
2. [Another verification step]
3. [Edge case to test manually]

## Performance Considerations

[Any performance implications or optimizations needed]

## Migration Notes

[If applicable, how to handle existing data/systems]

## References

- Original ticket: `thoughts/tickets/eng_XXXX.md`
- Related research: `thoughts/research/[relevant].md`
- Similar implementation: `[file:line]`
```

### Step 5: Sync and Review

1. **Sync the thoughts directory**:
   - This ensures the plan is properly indexed and available

2. **Present the draft plan location**:
   ```
I've created the initial implementation plan at:
`thoughts/plans/[filename].md`

Please review it and let me know:
- Are the phases properly scoped?
- Are the success criteria specific enough?
- Any technical details that need adjustment?
- Missing edge cases or considerations?
   ```

3. **Iterate based on feedback** - be ready to:
   - Add missing phases
   - Adjust technical approach
   - Clarify success criteria (both automated and manual)
   - Add/remove scope items

4. **Continue refining** until the user is satisfied

## Important Guidelines

1. **Be Skeptical**:
   - Question vague requirements
   - Identify potential issues early
   - Ask "why" and "what about"
   - Don't assume - verify with code

2. **Be Interactive**:
   - Don't write the full plan in one shot
   - Get buy-in at each major step
   - Allow course corrections
   - Work collaboratively

3. **Be Thorough**:
   - Research actual code patterns using parallel sub-agents
   - Include specific file paths and line numbers
   - Write measurable success criteria with clear automated vs manual distinction

4. **Be Practical**:
   - Focus on incremental, testable changes
   - Consider migration and rollback
   - Think about edge cases
   - Include "what we're NOT doing"

5. **Track Progress**:
   - Use TaskCreate to track planning tasks
   - Update tasks as you complete research
   - Mark planning tasks complete when done

6. **No Open Questions in Final Plan**:
   - If you encounter open questions during planning, STOP
   - Research or ask for clarification immediately
   - Do NOT write the plan with unresolved questions
   - The implementation plan must be complete and actionable
   - Every decision must be made before finalizing the plan

## Success Criteria Guidelines

**Always separate success criteria into two categories:**

1. **Automated Verification** (can be run by execution agents):
   - Commands that can be run
   - Specific files that should exist
   - Code compilation/type checking
   - Automated test suites

2. **Manual Verification** (requires human testing):
   - UI/UX functionality
   - Performance under real conditions
   - Edge cases that are hard to automate
   - User acceptance criteria

**Format example:**
```markdown
### Success Criteria:

#### Automated Verification:
- [ ] Kotlin compilation passes: `./gradlew :composeApp:compileCommonMainKotlinMetadata --no-daemon`
- [ ] Proto generation succeeds (if applicable): `./gradlew generateProtos --no-daemon`

#### Manual Verification:
- [ ] New feature appears correctly in the UI on iOS and Android
- [ ] Performance is acceptable
- [ ] Error messages are user-friendly
- [ ] No regressions in related screens
```

## Common Patterns

### For Database Changes:
- Start with schema/migration
- Add store methods
- Update business logic
- Expose via API
- Update clients

### For New Features:
- Research existing patterns first
- Start with data model
- Build backend logic
- Add API endpoints
- Implement UI last

### For Refactoring:
- Document current behavior
- Plan incremental changes
- Maintain backwards compatibility
- Include migration strategy

## Sub-agent Best Practices

When spawning research sub-agents via the Agent tool:

1. **Launch multiple agents in parallel** in a single message for efficiency
2. **Each agent should be focused** on a specific area with a clear prompt
3. **Include full path context** in prompts so agents search the right directories
4. **Request specific file:line references** in agent responses
5. **Wait for all agents to complete** before synthesizing findings
6. **Verify results** — if an agent returns unexpected results, spawn follow-ups or cross-check against the codebase yourself

## Example Interaction Flow

```
User: /implementation_plan
Assistant: I'll help you create a detailed implementation plan...

User: We need to add parent-child tracking for Codex sub-tasks. See thoughts/allison/tickets/eng_1478.md
Assistant: Let me read that ticket file completely first...

[Reads file fully]

Based on the ticket, I understand we need to track parent-child relationships for Codex sub-task events in the daemon. Before I start planning, I have some questions...

[Interactive process continues...]
```
