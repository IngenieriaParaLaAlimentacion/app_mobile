# ADR-013 — Current establishment is a new SQLDelight single-row table, cleared on logout

- **Status:** Accepted (grilling session 2026-07-04)
- **Context glossary:** [current-establishment-glossary.md](./current-establishment-glossary.md)

## Decision

Persist the current establishment as **just an `establishmentId`** in a new tiny SQLDelight table
(one logical row, user-scoped), following the exact shape of `CachedEstablishment` (ADR-002 in
`offline-reads-*`). It must survive app restarts and be wiped by `SqlDelightDatabaseCleaner.clearAll()`
on sign-out, same as every other user-scoped local table.

Store the **id only**, not a denormalized name/address snapshot — display fields are always resolved
by joining against the existing `EstablishmentWithStatus` cache at read time. This is required by
ADR-014 (stale-selection check needs the latest approved list anyway) and avoids a second copy of
establishment data going stale independently.

## Forces (confirmed 2026-07-04)

- The project has no existing key-value/settings primitive (no DataStore, no `multiplatform-settings`
  dependency) — every other piece of user-scoped local state (`PendingSubject`, `PendingMeasurement`,
  `CachedEstablishment`, `CachedActivityItem`) already lives in SQLDelight and is wiped centrally by
  `SqlDelightDatabaseCleaner`. Introducing a new persistence mechanism for one scalar would be an
  unjustified new dependency + a second sign-out-clearing code path to keep in sync.
- The "persist across restarts" + "cleared on logout" answers from grilling map exactly onto how
  `CachedEstablishment` already behaves — no new lifecycle rule needed, just wire the new table into
  the same cleaner transaction.
- Establishments are already cache-first and warmed at `AuthedShell` entry
  (`RefreshMyEstablishmentsUseCase`), so resolving the stored id against that cache costs nothing
  extra — the list is already in memory/local DB before Home renders.

## Consequences

1. New `.sq` file (e.g. `CurrentEstablishment.sq`) + matching `.sqm` migration + regenerated `.db`
   schema snapshot, committed together per the `sqldelight-migration-workflow` memory (verifyMigrations
   guard).
2. `SqlDelightDatabaseCleaner.clearAll()` gains one more `deleteAll()` call inside its existing
   transaction.
3. A small local store (mirrors `EstablishmentLocalStore`/`DefaultEstablishmentLocalStore`) exposes
   `get(): String?` / `set(id: String?)`, injected via `DatabaseModule`/`RepositoriesModule` the same
   way the establishment cache is.
4. Both Home and Measurement read this store independently (not via navigation arguments) — the only
   current entry point to Measurement is from Home today, but a shared local store is the correct
   CLEAN-architecture seam regardless, and costs nothing extra now.

## Rejected alternatives

- *`multiplatform-settings` (or platform expect/actual UserDefaults/SharedPreferences)* — would work,
  but adds a dependency and a second "what gets cleared on logout" mechanism to maintain alongside
  SqlDelight, for no benefit over reusing the existing table+cleaner pattern.
- *Pass the establishment as a navigation argument from Home to Measurement* — breaks down as soon as
  Measurement gains a second entry point (e.g. "add another measurement" from a subject's history),
  and doesn't let Home *display* the current establishment without its own separate read anyway.
