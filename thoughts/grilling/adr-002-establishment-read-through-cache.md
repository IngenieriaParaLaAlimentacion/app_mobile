# ADR-002 — Establishments use a SQLDelight read-through reference cache

- **Status:** Accepted (storage); open sub-items below
- **Context glossary:** [offline-reads-glossary.md](./offline-reads-glossary.md)

## Decision

`getMyEstablishments()` becomes cache-first against a new **SQLDelight table** (consistent with
`PendingSubject`/`PendingMeasurement`, gives a reactive Flow). Read serves the cached copy
immediately; when online, a background fetch refreshes and overwrites. Warm the cache at login /
first successful online entry so it exists before the user goes offline.

Storage chosen: **SQLDelight table** (over a Settings JSON blob) — accepts the `verifyMigrations`
ceremony (new `.sqm` + regenerated `.db` snapshot) per the migration workflow memory.

## Rationale

Establishments are **reference data**: slow-changing, server-owned, not user-generated, no
conflict model. "Remember the last good copy" is the whole requirement — no sync/merge engine.

## Resolved sub-items

- **Q3a — cache raw, filter at read (Accepted).** Store all statuses; filter to `Approved` at read
  time, so a background refresh returning "0 approved" is distinguishable from "cache cold" and the
  `singleOrNull()` auto-select can't silently misfire.
- **Q2 — stale-write risk: accept it (Accepted).** Offline auto-select can submit a measurement
  against an establishment whose approval was revoked while offline. No pre-validation is added: the
  measurement engine already degrades safely — `Forbidden → markFailed("No tenés acceso a ese
  establecimiento")`, `NotFound → markFailed(...)`. Data is never corrupted; the row dead-letters
  with a correct Spanish reason. Establishment access is stable enough that engineering around this
  isn't worth the complexity.
- **Q4 — scope & eviction (Accepted, refined at planning).** Single-user-at-a-time table **cleared
  on logout** (on the `SessionStatus → NotAuthenticated` transition in `AppViewModel`). This achieves
  the no-cross-account-leak guarantee without per-user keying. No TTL.

## Implementation refinement (planning, 2026-06-29)

The "return cached immediately, refresh in background" shape is realized as **cache-first read +
separate prefetch** given the one-shot (non-Flow) `getMyEstablishments()` and `MeasurementViewModel`:
- `getMyEstablishments()` = cache-first (return cache when non-empty, else network + populate;
  network failure with a warm cache falls back to cache).
- A `refreshMyEstablishments()` prefetch runs at **authed-shell entry when online**
  (`AuthedShell` `SyncTriggers`) and overwrites the cache — this is the background refresh, and it
  avoids a mid-session auto-select flip. True Flow-based SWR is deferred. See
  `thoughts/plans/measurement_offline_reads.md` Phase 1.
