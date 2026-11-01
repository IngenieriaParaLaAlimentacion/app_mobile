---
name: user-story-creator
description: Relentlessly clarify a feature through focused product questions, turn the shared agreement into well-scoped user stories, decide with the user how to split them and which repository owns each one, and optionally create and place the resulting GitHub issues. Use whenever the user asks to create, write, refine, split, grill, or publish user stories, acceptance criteria, product requirements, backlog issues, or cross-repository feature work—even if they only describe a feature informally. Do not use for implementation-only tasks with an already-agreed story.
---

# User Story Creator

Turn an initially fuzzy feature request into a shared, testable product agreement and then into repository-owned user stories. The interview is the work: expose consequential ambiguity before polishing prose.

Keep the agreement in the conversation. Do not create ADRs, glossaries, planning documents, or `.context` files unless the user separately asks for them.

## 1. Ground the interview in evidence

Before asking questions, use available read-only evidence to learn what is already true:

- inspect the relevant code, API contracts, schemas, tests, and repository instructions;
- inspect existing issue templates, recent user stories, labels, milestones, and project conventions in each candidate implementation repository independently;
- distinguish an implementation repository from an organization-level GitHub Project that aggregates issues from several repositories;
- identify existing capabilities, dependencies, conflicting stories, and likely non-goals.

Do not make the user answer questions the evidence can answer. Briefly state what you found and label any inference as an inference.

If no repositories or artifacts are available, begin from the user's description and say which assumptions remain unverified.

## 2. Grill toward a shared agreement

Ask focused questions in small rounds. Prefer one consequential decision at a time; group at most three tightly related questions when answering them together is genuinely easier.

When the plausible options are known:

- offer concrete alternatives instead of asking a vague open question;
- recommend one option and explain its product consequence;
- include a short example or counterexample when a term could be interpreted in multiple ways;
- challenge contradictions directly and respectfully.

Use the user's language. Default to Spanish when the request or existing backlog is Spanish.

Cover only dimensions that materially affect this feature:

1. **Actor and outcome** — who needs this, what changes for them, and why it matters.
2. **Entry points and happy path** — where the user starts, what they do, and what success looks like.
3. **Scope boundaries** — what is explicitly included and excluded.
4. **Data and contract** — source of truth, identity, ordering, filtering, pagination, and payload needs.
5. **Permissions and visibility** — who can act, who can see the result, and tenant/user isolation.
6. **Failure and recovery** — validation, empty/loading/error states, retries, idempotency, and offline behavior where relevant.
7. **Lifecycle** — creation, later viewing, editing/deletion, refresh, migration, and rollout implications.
8. **Dependencies** — prerequisite backend, mobile, web, data, infrastructure, or design work.
9. **Proof** — observable acceptance criteria and the important automated tests.

Do not mechanically ask about every dimension. Skip settled or irrelevant areas. Continue until another answer would no longer change scope, behavior, ownership, or acceptance criteria.

## 3. Keep the agreement visible

After each meaningful decision, briefly restate what changed. Maintain a compact conversational agreement containing:

- goal and actors;
- agreed behaviors;
- constraints and non-goals;
- dependencies;
- genuinely open decisions.

Do not silently resolve an open product decision. If the user's latest answer conflicts with an earlier one, surface the conflict and resolve it before drafting.

## 4. Negotiate the story split and repository ownership

Once the behavior is sufficiently agreed, present a concise synthesis and propose the smallest independently valuable split. Split by user-visible outcome or independently deliverable contract—not automatically by technical layer.

Cross-repository work often does need separate stories when different teams, deployments, or sequencing are involved. Explain the dependency direction, such as backend contract before mobile consumption.

Then explicitly ask the user:

> ¿Cómo querés dividir este acuerdo en historias de usuario y en qué repositorio debería vivir cada una?

Include your recommended split and repository mapping so the user can approve or amend something concrete. Do not draft final stories or create issues until the user answers this split-and-ownership question.

Treat a GitHub Project and a repository as separate choices:

- the repository owns the issue and should normally match the implementation;
- the organization Project is a planning view and may contain issues from multiple repositories.

Never infer that every issue belongs to the repository where a Project appears to be managed. Map each approved story to the repository whose code will implement it: for example, an API contract normally belongs to `backend`, a mobile screen to `app_mobile`, and an administration UI to `backoffice`. Resolve the full `OWNER/REPOSITORY` slug for every story before publishing.

## 5. Draft each user story

After the user approves the split, follow the target repository's established format. Inspect existing stories rather than assuming every repository uses the same template.

When no format exists, default to:

```markdown
### Épica

[Nombre]

### Descripción

**Como** [actor]
**Quiero** [capacidad]
**Para** [resultado]

### Criterios de aceptación

**CA 1: [Nombre observable]**
- [ ] [Criterio verificable]

### Puntos de historia

[Estimación acordada o "A definir"]

### Tipo de historia

⭐ Obligatoria

### Notas

[Dependencias, fuera de alcance y decisiones técnicas relevantes]
```

Write acceptance criteria that are:

- observable and independently verifiable;
- explicit about permissions, empty/error behavior, and isolation when relevant;
- free of duplicate requirements;
- precise enough to test without prescribing unnecessary implementation details.

Put essential API semantics in backend acceptance criteria when the contract itself is the deliverable. Put lower-level implementation suggestions and cross-story links in Notes.

Call out mismatches between a story and the current system. Do not mark existing behavior as unfinished or claim unsupported behavior already exists.

## 6. Publish only when authorized

Drafting stories does not by itself authorize creating GitHub issues. Create or edit issues only when the user asked to publish/create them, either initially or after reviewing the drafts.

Before publishing, repeat the approved `story → OWNER/REPOSITORY` mapping and use that exact repository in each create command. This final check prevents the current working repository or the Project's apparent home from silently becoming the owner of every issue.

Then:

1. inspect templates and comparable user stories in each target repository separately;
2. inspect that target repository's valid labels and use only labels that exist there—do not copy labels from another repository, and leave an issue unlabeled when that is the established convention;
3. inspect title conventions such as `[US]`;
4. inspect milestones, assignees, and project conventions without inventing values;
5. check for likely duplicate or overlapping issues;
6. cross-link dependencies after issue numbers exist, using fully qualified references such as `OWNER/REPOSITORY#123` whenever the stories live in different repositories.

When the user requested project placement, add every issue explicitly with `gh project item-add`; repository creation alone does not add an issue to a Project. Verify both placement and ownership: the Project item must point to the intended issue URL and its `repository` field must equal the approved `OWNER/REPOSITORY`. Also verify the issue has only labels valid for that repository. If authentication lacks `project` scope, explain the exact blocker instead of assuming an auto-add workflow will handle it.

If an issue was created in the wrong repository, prefer `gh issue transfer` within the same organization so its discussion and history survive. After transfer, verify its new number, Project status, labels, and repository field, then repair all cross-repository references with fully qualified names.

After publishing, report for each story:

- issue title and link;
- owning repository;
- labels;
- project and status, if applicable;
- dependencies or follow-up decisions.

## Quality check

Before handing off, confirm:

- the story describes a user or stakeholder outcome;
- every criterion traces to the shared agreement;
- the split avoids both a cross-team mega-story and artificial layer-by-layer fragmentation;
- repository ownership matches where the work will be implemented;
- every Project item's repository field matches the approved story mapping;
- dependencies are reciprocal and unambiguous;
- no external issue or project mutation happened without authorization;
- no context documents were created merely to conduct the interview.
