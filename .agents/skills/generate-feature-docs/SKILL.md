---
name: generate-feature-docs
description: Generate feature specification and API integration documents from a codebase research file. Use when the user asks to generate specs, create feature docs, or produce frontend/API documentation from a research output.
---

# Generate Feature Docs

Takes a research document (from `/research_codebase` or similar) and produces two standardized documents:

1. **`{feature}_specs.md`** — Functional specification (non-technical, for product/design/frontend engineers)
2. **`{feature}_api.md`** — API integration guide (technical, for frontend engineers integrating with the backend)

## When to Use

- User says "generate docs from this research"
- User says "create specs for {feature}"
- User says "generate feature docs"
- User provides a research file and asks for spec + API outputs

## Input

The user provides either:
- A path to a research `.md` file (e.g. `docs/research/2026-04-06_profile-screen-backend-integrations.md`)
- Or the research content directly

## Process

### Step 1: Read the templates

Read both templates before generating:
- `docs/templates/frontend_spec.md`
- `docs/templates/feature_api.md`

### Step 2: Read the research

Read the full research file provided by the user.

### Step 3: Determine the feature name

Derive a short snake_case feature name from the research topic (e.g. `profile`, `home`, `checkout`). Ask the user if ambiguous.

### Step 4: Generate both documents in parallel

#### For `{feature}_specs.md`:

Follow the `frontend_spec.md` template **strictly**. Every section in the template must appear in the output (delete optional sections only if truly not applicable).

Key rules:
- Use **"Pages"** not "Screens" — the spec is platform-agnostic (mobile + web)
- **Copy and labels**: Include exact wording for every CTA, button, label, title, placeholder. Mark dynamic text and explain what drives it.
- **User actions**: Use platform-neutral verbs ("Interact with", "Select", not "Tap")
- **Empty states**: Include message text, CTA text, and CTA action
- **Error states**: Include recovery mechanism
- **Analytics**: Exact event names with property name, type, and value description — these are strict contracts
- **Navigation flow**: Include page map diagram and destinations table with params
- **State management**: How pages share data, local state with scope, optimistic updates with rollback
- Do NOT include implementation details, class names, or code references

#### For `{feature}_api.md`:

Follow the `feature_api.md` template **strictly**.

Key rules:
- **Architecture overview**: ASCII diagram showing all backend calls
- **Each endpoint**: Type, method, function/table name, trigger, auth requirement, full request params table, full response with field-by-field table
- **Pagination**: Strategy, page size, how to get next page, how to detect end
- **Frontend behavior**: Optimistic updates, rollback, debouncing where applicable
- **Hardcoded values**: List any data that doesn't come from the backend
- **Local-only features**: List client-side-only functionality
- **Summary table**: All integrations at a glance
- Use Supabase JS client syntax for request examples (not mobile/Kotlin code)

### Step 5: Write the files

Write both files to `docs/specs/`:
- `docs/specs/{feature}_specs.md`
- `docs/specs/{feature}_api.md`

## Output Location

```
docs/specs/
├── {feature}_specs.md
└── {feature}_api.md
```
