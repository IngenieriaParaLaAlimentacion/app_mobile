# ADR-006 — Retry/Delete stay scoped to local-sourced rows only

- **Status:** Accepted (grilling session 2026-07-01)
- **Context glossary:** [activity-feed-glossary.md](./activity-feed-glossary.md)

## Decision (proposed)

Every item in the merged `HistoryHubScreen` ledger carries an `isLocal: Boolean` (or equivalently,
a `source: Local | Remote` tag) alongside its display fields. `Retry`/`Delete` affordances render
**only** for `isLocal == true` items — i.e. rows that still have a `PendingSubject`/
`PendingMeasurement` row backing them (PENDING, SYNCING, FAILED; see ADR-007 for SYNCED/MERGED
dedup). A row sourced purely from `/me/activity` (no local backing row — either never existed on
this device, or aged out past the 7-day prune) is rendered read-only: no trailing action, same as
a plain list row.

## Rationale

`SubjectUploadsAction.Retry`/`Delete` and `MeasurementUploadsAction.Retry`/`Delete` operate on
`localId` against `SubjectRepository`/`MeasurementRepository` — both ultimately mutate a
SQLDelight row (`bumpRetry`/`resetForRetry`/`deleteById`). There is no server-side equivalent
operation exposed by `/me/activity` (it's read-only), and retrying/deleting a row that was already
accepted by the server makes no product sense — the action correctly has nothing to do.

## Consequences

- `SubjectUploadUi`/`MeasurementUploadUi` (or their post-merge successors) need the extra
  `isLocal` flag threaded through from the merge step, not just from the local mapper.
- The Screen composables (`SubjectUploadsContent`/`MeasurementUploadsContent`) gate the
  trailing icon button (trash / retry) on this flag instead of assuming every row is local.
- No new use case needed — this is a pure UI-affordance gate on data already known at merge time.
