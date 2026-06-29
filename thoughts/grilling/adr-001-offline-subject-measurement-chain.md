# ADR-001 — Offline subject→measurement chain references localId; sync is ordered

- **Status:** Accepted (grilling session 2026-06-29)
- **Context glossary:** [offline-reads-glossary.md](./offline-reads-glossary.md)

## Decision

A measurement created offline against an offline-created subject references that subject's
**`localId`**. No server round-trip is needed to "learn" an id, because the backend treats the
client-supplied UUID as the row primary key (idempotent replay → 200).

`PendingMeasurement.subjectId = the subject's localId`. In the happy path this value *becomes* the
server PK when the subject is POSTed, so the measurement's FK is already correct — **no remapping**.

## Forces (from backend contract, confirmed 2026-06-29)

- Both `SubjectRequest` and `MeasurementRequest` accept an optional client `id: UUID` → PK;
  replay is idempotent (200, not duplicate).
- `measurement.subject_id` is a **non-nullable FK** to `subjects.id`, and `MeasurementService`
  calls `_get_subject_or_raise()` → a missing subject is **404**, not deferred. There is no
  server-side pending-FK reconciliation.
- Therefore **sync ordering is mandatory: a subject must land server-side before its measurement.**
- DNI is a unique natural key → a colliding subject POST returns **409 + canonical_subject**.

## Consequences / rules this forces

1. **Ordered sync:** subjects sync before measurements that depend on them. (See ADR open item:
   the hold rule — a measurement must not *attempt* while its subject is still PENDING/SYNCING/FAILED,
   or it burns its retry budget on guaranteed 404s.)
2. **409-merge remap (the only reconciliation):** when subject `S` merges to canonical `C`
   (`SyncState.Merged`, `canonicalId = C`), every still-PENDING `PendingMeasurement` with
   `subjectId = S` must be rewritten to `C` **before** it is POSTed, else it 404s.
3. **Dedup is trivial in the happy path:** local SYNCED row and remote row share the same id
   (localId = PK), so a union picker dedups by id. **Exception:** MERGED rows (local id `S` ≠
   remote id `C`) do *not* dedup — see ADR open items.

## Rejected alternatives

- *Block measuring unsynced subjects* — would kill the full-offline-chain goal; unnecessary now
  that the client UUID removes the id-learning problem.
- *Server-side deferred FK* — does not exist in the backend; not an option.
