# ADR-009 — On id collision, the local queue row wins over the cached/remote activity item

- **Status:** Accepted (grilling session 2026-07-01)
- **Context glossary:** [activity-feed-glossary.md](./activity-feed-glossary.md) — "Dual-presence row"

## Decision (proposed)

The merged ledger read is a **union read**, directly extending the shape already accepted in
[ADR-004](./adr-004-subject-picker-union.md) (`SubjectQueryRepositoryImpl.searchSubjects`), applied
per page:

1. Query the local queue (`SubjectLocalStore.all()` / `MeasurementLocalStore.all()`) for rows whose
   `createdAt`/`updatedAt` falls in the requested date range (or, simpler: fetch the whole small
   queue and let the merge step filter/sort it — the queue is bounded by the 7-day prune, so it's
   always small).
2. Query `CachedActivityItem` (ADR-008) for the same page.
3. **Dedup by id: the local queue row wins.** If `id` appears in both, render the queue's version;
   drop the cached/remote copy for that id.

## Rationale

Local queue rows are already fully denormalized at creation time (`MeasurementUpload` carries
`subjectName`, `establishmentName`, `bmi`, `alerts` — see `MeasurementUpload.kt`) — rendering them
needs no network round trip and cannot mismatch the server's copy (same source data, same
computation, ADR-001's client-UUID-as-PK means it's *the same row*, not a separate copy that could
drift). Preferring remote would add a dependency on the cache being warm/fresh for data already
sitting in the queue, for zero benefit — there's no scenario where the server's copy of an
already-synced row usefully differs from what's in `PendingMeasurement`/`PendingSubject`.

## Consequences

- The merge step needs a `Set<id>` of queue-row ids per page to filter the cached-item query
  against, or simpler: filter cached items client-side after both fetches, since queue size is
  small (bounded by the prune window).
- This composes cleanly with ADR-006 (`isLocal` flag) and ADR-007 (MERGED exemption): a MERGED row
  always comes from the queue (never has a cache-table counterpart, per the "orphaned-history"
  glossary term), a SYNCED row prefers the queue until pruned, then falls through to the cache
  table transparently — no visible behavior change at the 7-day boundary from the user's
  perspective, which is the entire point of doing this work.
