# ADR-003 — Subject↔measurement coupling: resolve-at-read cross-table query

- **Status:** Accepted (grilling session 2026-06-29)
- **Supersedes the "open item" in [ADR-001](./adr-001-offline-subject-measurement-chain.md) about the hold rule + 409 remap.**

## Decision

The measurement queue resolves its subject dependency by **reading `PendingSubject` at sync time**,
not by row rewrites or engine-to-engine calls. `MeasurementLocalStore.syncable()` gates and resolves
in one query:

- **Hold rule:** a measurement is returned as syncable only if its subject is ready.
- **409 remap (resolve, don't rewrite):** the subjectId sent to the backend is
  `pendingSubject.canonicalId ?: pendingSubject.localId`. The `PendingMeasurement.subjectId` column
  is never UPDATEd — no `updateSubjectId` query, no index, **no migration for the remap.**

`syncPending()` must apply the resolved id to the request body (`toRequest()` currently uses the raw
`subjectId`); the resolution feeds the POST, not just the gate.

## CORRECTNESS TRAP — it is a LEFT JOIN, not an inner join

A measurement can reference a subject that **already existed on the server** (the relevador picked it
from remote `/subjects`, so there is **no `PendingSubject` row at all**). The gating rule must be:

> Hold the measurement **only if** a `PendingSubject` row exists for `subjectId` **and** its state is
> not SYNCED/MERGED. If **no local subject row exists**, the subject is a pre-existing server subject
> → **release immediately**, send `subjectId` as-is.

An inner join would strand every measurement made against an online-selected subject in PENDING
forever. This is the single easiest way to get this ADR wrong.

## Resolution table

| PendingSubject row for subjectId | State | syncable? | id sent |
|---|---|---|---|
| none | — | yes (pre-existing server subject) | `subjectId` as-is |
| exists | PENDING / SYNCING | **held** | — |
| exists | SYNCED | yes | `localId` (= server PK) |
| exists | MERGED | yes | `canonicalId` |
| exists | FAILED | **dead-letter the measurement** ("El sujeto no se pudo sincronizar.") | — |

## Implementation refinement (planning, 2026-06-29)

Resolution lives in the **repository** (`MeasurementRepositoryImpl.syncPending`), injecting
`SubjectLocalStore` and calling its existing `selectById`-backed `find(localId)` — **not** a new
JOIN query in the measurement store. This honors the resolve-at-read principle (no row rewrite,
LEFT-JOIN semantics via `find == null → pass through`) while needing **no measurement-schema
migration** and staying unit-testable with the existing fake-store pattern. The resolved id feeds
the POST via `upload.copy(subjectId = resolved).toRequest()`. See
`thoughts/plans/measurement_offline_reads.md` Phase 3.

## Consequences

- Couples `MeasurementRepositoryImpl` to `SubjectLocalStore` (cross-store read). Accepted.
- Engine *ordering* no longer matters for correctness (subjects-first is an optimization, not a
  requirement) — a held measurement simply isn't eligible until a later pass.
- A genuine 404 from the server now only happens for a *released* measurement → 404 can stay an
  instant dead-letter (it means a real orphan/auth problem, not an ordering race).
