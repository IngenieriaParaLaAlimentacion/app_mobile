# Glossary — Server-Backed Activity Feed

Working vocabulary for the grilling session on integrating `GET /api/v1/users/me/activity` into
Home's recent-activity list and `HistoryHubScreen`. Extends the identity/sync-state terms already
defined in [offline-reads-glossary.md](./offline-reads-glossary.md) (localId, canonicalId, SyncState).

## The two existing surfaces (as they are today — 100% local)

- **Queue view** — what Home (top 4) and `HistoryHubScreen`'s Sujetos/Mediciones tabs show today:
  a live `Flow` over `PendingSubject`/`PendingMeasurement`, unfiltered, `ORDER BY createdAt DESC`,
  capped to rows still in the DB. Carries **actionable** sync-state (Pendiente/Sincronizando/Error)
  and Retry/Delete affordances. Entirely this-device, this-session.
- **7-day prune** — `SyncCoordinator`'s hourly `pruneTerminal(olderThan = 7.days)` deletes local
  rows in a terminal state (SYNCED/MERGED/FAILED) once they're older than 7 days. This means the
  queue view **already loses history past 7 days** — the strongest argument for the new endpoint.
- **Ledger view** (proposed name for the new thing) — the merged, paginated, date-descending view
  the new endpoint enables: this user's full activity, across devices/sessions/reinstalls, sourced
  from server truth once synced, falling back to/union'd with whatever's still in the local queue.

## New endpoint terms

- **Activity item** — one element of `GET /me/activity`'s `items[]`; a discriminated union on
  `type` (`"subject" | "measurement"`), each variant carrying only its own nested object
  (`subject` xor `measurement` — never both, never neither).
- **occurred_at** — the sort key. Subject items: creation timestamp. Measurement items:
  `measured_at`. Note this is **not** the same field name as local `createdAt`/`measuredAt` — it's
  a server-computed projection, already resolved to "the one timestamp that matters for this row."
- **Responsible** (measurement item field) — always *this* authenticated user for `/me/activity`
  (it's *their own* activity), so it's redundant for rendering but present in the payload.

## Merge-semantics terms (the crux of this session)

- **Local-only row** — PENDING/SYNCING/FAILED: never reached the server, so it exists *only* in the
  queue and *never* in `/me/activity`. Must always come from local.
- **Dual-presence row** — SYNCED (or a measurement referencing a MERGED subject): exists in *both*
  the local queue (until pruned) and the server feed, under the *same* id (client-UUID = server PK,
  per [ADR-001](./adr-001-offline-subject-measurement-chain.md)). Needs dedup, not just union.
- **Orphaned-history row** (new failure mode, not covered by ADR-004) — a local **MERGED** subject
  row. Its canonical id `C` usually belongs to a subject *this user did not create* (someone else's
  DNI collision), so `C` will **never appear** in *this user's* `/me/activity` — there is no remote
  fallback. Unlike ADR-004's picker (where hiding MERGED is safe because the canonical row *is*
  reachable in the general `/subjects` union), hiding it here would make the "Ya existía" event
  vanish once the 7-day prune deletes the local row. Needs an explicit call: exempt MERGED from
  pruning, or accept the loss.
- **Reconciliation** (as used by the user's brief) — being able to see, in the ledger, an action
  taken on this device that later synced/merged elsewhere, without depending on the local queue
  still holding that row.

## Cache-shape terms

- **Bounded reference cache** — [ADR-002](./adr-002-establishment-read-through-cache.md)'s pattern:
  small, slow-changing, safe to refetch-and-overwrite wholesale. Fits establishments. Likely **does
  not** fit the activity feed (see below).
- **Unbounded append-mostly ledger** — the activity feed's actual shape: grows every day, every
  page beyond the first is effectively immutable once fetched (a past subject-registration or
  measurement event doesn't change). Different cache shape than ADR-002: persist fetched pages,
  refresh only the *newest* page to catch new arrivals, never wholesale-overwrite — **but still
  capped** (see below), not kept forever.
- **Rolling window cache** (the actual accepted shape, ADR-008) — persist only the most recently
  fetched/viewed ~200 items; evict the oldest beyond that after each page upsert. The server is the
  permanent archive; the local cache only needs to cover realistic offline use ("what did I do
  recently"), not serve as a second unbounded copy of the user's whole history.
- **Offset-pagination drift** — classic issue with `page`/`page_size` (not cursor-based): if new
  activity lands between page fetches, item boundaries can shift, causing skips or dup renders
  while scrolling. The backend contract offers no cursor — this is either accepted as a rare
  cosmetic glitch or guarded client-side with an in-memory seen-id set.
