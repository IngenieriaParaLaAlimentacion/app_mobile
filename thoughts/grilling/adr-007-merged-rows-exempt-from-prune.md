# ADR-007 — MERGED subject rows are exempt from the 7-day prune

- **Status:** Accepted (grilling session 2026-07-01) — horizon = never (not a bounded long window)
- **Context glossary:** [activity-feed-glossary.md](./activity-feed-glossary.md) — "Orphaned-history row"

## Decision

`SyncCoordinator`'s hourly `pruneTerminal(olderThan = 7.days)` must **stop pruning `PendingSubject`
rows in the MERGED state**, regardless of age. PENDING/SYNCING never reach `pruneTerminal` (not
terminal). SYNCED and FAILED keep the existing 7-day rule (SYNCED is safely reconstructable from
`/me/activity`; FAILED has its own visible/retryable lifecycle in the Uploads screen already).

Concretely: `SyncEngine.pruneTerminal(olderThan)` (or its `PendingSubject.sq` query /
`SubjectLocalStore.pruneTerminalOlderThan`) narrows its `WHERE syncState IN (...)` to exclude
`MERGED`, OR MERGED rows get pruned on a much longer horizon (e.g. 1 year) if unbounded retention
is judged too open-ended. Needs a decision on exact horizon — "never" vs "long" — before
implementation (see Open below).

## Rationale

A MERGED row's `canonicalId` almost always belongs to a subject **this user did not create**
(someone else's pre-existing DNI). `/me/activity` only returns activity for rows *this user*
registered, so that canonical subject will **never** surface in this user's feed — there is no
remote fallback the way there is for a pruned SYNCED row. Once the local MERGED row is pruned, the
"you tried to register this person, but they already existed" event is gone forever, with no way
to reconstruct it from the new endpoint. This is strictly worse than today's behavior tolerated by
ADR-004 for the *picker* (safe there because the canonical row *is* reachable via the general
`/subjects` search — this is the ledger, which has no such general search).

## Consequences

- `PendingSubject` table gains a de facto "small, slow-growing, permanent" bucket of MERGED rows
  per user — bounded by how often a relevador re-registers an existing DNI, expected to be rare.
- Still wiped on **explicit sign-out** (`SqlDelightDatabaseCleaner.clearAll()` — unaffected, that's
  a full wipe regardless of state) — this ADR only changes the *hourly* prune, not sign-out.
- If MERGED volume ever becomes a real storage concern, revisit with a real horizon (e.g. 1 year)
  instead of "never."

## Resolved

Horizon = **never**. The row count this creates is tiny (only on a DNI collision) and the failure
mode (silently losing a "duplicate person" event a relevador might need to explain later) is worse
than a few extra kilobytes of SQLite.
