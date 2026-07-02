# Server-Backed Activity Feed — Grilling Synthesis (2026-07-01)

Outcome of the grilling session on integrating `GET /api/v1/users/me/activity` into Home and
`HistoryHubScreen`. Decisions are in ADR-005…011 (continuing the numbering from the prior offline-
reads session, ADR-001…004); glossary in `activity-feed-glossary.md`. **All ADRs below are now
Accepted.**

## The reframe that drove everything

The starting framing — *"the activity feed needs an offline-first cache strategy"* — undersold the
real trigger: **the local queue already has an offline-first cache strategy, and it's lossy by
design.** `SyncCoordinator.pruneTerminal(olderThan = 7.days)` deletes terminal rows after a week.
The new endpoint doesn't add an offline capability so much as it **plugs the hole the 7-day prune
already dug.**

A second reframe, corrected mid-session: Home and History share the same underlying merged source
after all. The initial instinct — "Home is pure sync-status, doesn't need this" — didn't survive
contact with what `HomeStateMapper` actually does (it already shows the last 4 items regardless of
sync state, not just in-flight ones). Once that was caught, reusing the History merge plumbing for
Home became close to free.

A third reframe, also corrected mid-session: "cache what we fetch, forever" sounds like the
offline-first-correct answer but isn't — the server already durably archives everything; the local
cache only needs to serve "what did I do recently" offline, which a **bounded rolling window**
does just as well as an unbounded one, without the unpredictable multi-year storage growth.

## Locked decisions

| ADR | Decision |
|-----|----------|
| 005 | Home **also merges** — reads the same local `queue ∪ CachedActivityItem` source as History (read-only, no independent network call), `take(4)`. Kept fresh via History's own refresh-on-open plus a new small prefetch at `AuthedShell` entry. |
| 006 | Retry/Delete render only for local-sourced rows (`isLocal` flag); remote-only ledger rows are read-only. |
| 007 | MERGED subject rows are exempted from the 7-day prune, **permanently** (no bounded horizon) — no remote fallback exists for them in `/me/activity`. |
| 008 | New `CachedActivityItem` SQLDelight table, **capped to the most recent ~200 items** (not unbounded) — JSON payload column, evict-beyond-cap after each page upsert. Different shape than ADR-002's bounded-reference-data cache, but still bounded, just on a rolling-recency basis instead of a small-fixed-set basis. |
| 009 | On id collision between the local queue and the cache/remote item, **the local queue row wins** — already denormalized, no network dependency, provably the same row (ADR-001). |
| 010 | Reuse the existing manual load-more pattern (`SelectSubjectViewModel`'s shape) for pagination — no new library. Offset-pagination drift (no cursor in the API) accepted as a rare cosmetic risk. |
| 011 | Parse the discriminated union as one flat DTO with nullable `subject`/`measurement` fields — no `JsonContentPolymorphicSerializer` needed. |

## Suggested build order (bottom-up, compile between phases)

1. **DTOs + API client (independent, ship first).** `data/activity/dto/ActivityResponse.kt`
   (ADR-011), `ActivityApi.getMyActivity(page, pageSize, type)`. Pure network plumbing, unit-testable
   against a `MockEngine` fixture built from the endpoint's documented example payload.
2. **`CachedActivityItem` table (ADR-008).** New `.sq` file, `.sqm` migration, regenerated `.db`
   snapshot (`verifyMigrations` guard). Local store with `upsert`/`selectPage`/`evictBeyondCap`/
   `deleteAll`. **Immediately wire `deleteAll()` into `SqlDelightDatabaseCleaner.clearAll()`** —
   easy to forget, this is the "new table forgot the wipe" trap. Ships and compiles standalone.
3. **Prune exemption (ADR-007).** One-line change to the `pruneTerminal` query's `WHERE` clause to
   exclude MERGED. Independent, low-risk, ships alone.
4. **Repository merge + pagination (ADR-006, 009, 010).** New `ActivityRepository` that unions
   local queue + `CachedActivityItem` + live network per page, dedups by id (local wins), tags
   `isLocal`, feeds a `GetMyActivityUseCase`. This is the keystone phase — ADR-004's union-read
   shape reapplied at ledger scale, plus the eviction-after-upsert step from ADR-008.
5. **`HistoryHubScreen` + segment ViewModels wiring.** Swap `ObserveSubjectUploadsUseCase`/
   `ObserveMeasurementsUseCase` for the new paginated use case. Add `LoadMore` action, gate
   Retry/Delete on `isLocal`.
6. **Home wiring (ADR-005).** `HomeViewModel` reads the same merged local source, `take(4)`, no
   network trigger of its own. Add the small activity-page-1 prefetch to `AuthedShell`'s
   `SyncTriggers`, alongside the existing establishment-cache warm-up.

## Residual risks to watch

- **"New table forgot the sign-out wipe"** — concrete, easy-to-miss step; called out explicitly in
  phase 2 above.
- **Eviction ordering interacting with "local wins" (ADR-009)** — a queue row and a cached row can
  represent the same id; make sure `evictBeyondCap` counts by cached rows only (it naturally does,
  since the queue isn't in this table), so evicting cache rows never removes something still
  actionable in the queue.
- **`isLocal` flag leaking into domain models that don't need it** — keep it presentation-layer
  (UI-mapper) if possible, matching the "don't leak subject types into the measurement domain
  model" caution from ADR-003.
- **Uploads screens vs. Ledger screens naming** — `SubjectUploadsViewModel`/
  `MeasurementUploadsViewModel` are named around "uploads" (this-device queue management); once
  they also render read-only remote history, the name may no longer fit. Worth a naming pass during
  phase 5, not before — premature renaming before the shape is locked adds churn for nothing.
- **~200-item cache cap is a starting guess**, not measured against real usage volume — cheap to
  tune later since it's a single constant, not a schema change.
