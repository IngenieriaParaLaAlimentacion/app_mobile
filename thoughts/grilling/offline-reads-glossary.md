# Glossary — Offline-First Reads (Measurement Screen)

Working vocabulary for the grilling session on the two live-HTTP reads the measurement screen
depends on. Terms are deliberately precise so we can argue about the right thing.

## Identity terms (the crux)

- **localId** — client-minted v4 UUID. Primary key of `PendingSubject` / `PendingMeasurement`,
  *and* the idempotency `id` POSTed to the backend. Exists the instant a row is created offline.
- **canonicalId** — the *server's* id for a subject. On `PendingSubject` it is `null` until sync,
  then set to the server's own id (201/200) **or** to a pre-existing server id (409 merge).
- **serverSubjectId** — synonym for a non-null `canonicalId`. The value
  `PendingMeasurement.subjectId` is documented to hold ("server UUID, from SubjectSummary").
- **id reconciliation** — the act of replacing a measurement's reference to an *unsynced* subject
  with that subject's `canonicalId` once it becomes known. The hard part of the chain.

## Data-classification terms

- **Reference data** — slow-changing, server-owned, not user-generated, no conflict model.
  Establishments are reference data scoped to the user.
- **User-generated data** — created on-device, must round-trip to the server, has a sync/merge
  model. Subjects (and measurements) are user-generated.

## Read-strategy terms

- **Read-through cache** — read serves cache immediately; a background fetch refreshes and
  overwrites when online. Proposed for establishments.
- **Union read** — picker result = remote page ∪ local rows, deduped by identity. The candidate
  shape for the subject picker.
- **Warm cache** — cache populated *before* first offline use (e.g. prefetched at login).
- **Cache key / scope** — what the cache is partitioned by (here: the authenticated user).

## Sync-state terms (existing, from `SyncState`)

- **PENDING** — queued, not yet accepted by server. **SYNCING** — POST in flight.
- **SYNCED** — server accepted (201/200). **MERGED** — server said 409; `canonicalId` is an
  existing server subject. **FAILED** — dead-lettered after `MAX_SYNC_ATTEMPTS` (5) or hard error.

## The two reads under grilling

| Read | Path today | Store it reads | Offline result |
|------|------------|----------------|----------------|
| Subject picker | `SelectSubjectVM → GetSubjectsUseCase → SubjectQueryRepositoryImpl → SubjectApi.list()` | remote `/subjects` only | throws → `loadError`, empty list; offline-created subject invisible (it's in `PendingSubject`) |
| Establishment picker | `MeasurementVM.loadEstablishments → GetMyEstablishmentsUseCase → ProfileRepositoryImpl → ProfileApi.getMyEstablishments()` | remote `/me/establishments` only | fails → `profileError`, `selectedEstablishment=null`, submit blocked |
