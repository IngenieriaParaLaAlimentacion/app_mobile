# ADR-008 — New `CachedActivityItem` SQLDelight table, capped to a rolling window

- **Status:** Accepted, revised (grilling session 2026-07-01) — capped, not unbounded (see below)
- **Context glossary:** [activity-feed-glossary.md](./activity-feed-glossary.md) — "Unbounded append-mostly ledger"

## Decision

Add a new SQLDelight table, `CachedActivityItem.sq`, that persists activity items fetched from
`/me/activity`, keyed by the item's own id (subject id or measurement id) — but **capped to the
most recent ~200 items**, not kept forever. After each page upsert, evict rows beyond the cap
(ordered by `occurredAtMillis DESC`). Page 1 always refreshes on open; pages beyond the cap are
simply not persisted — served live from network when online, unavailable offline (an accepted
cliff, same spirit as the queue's existing 7-day prune).

Proposed schema (minimal columns + a JSON payload blob, following the existing
`PendingMeasurement.alertsJson` precedent rather than a wide flat table that must track every
backend field 1:1):

```sql
CREATE TABLE CachedActivityItem (
    id TEXT NOT NULL PRIMARY KEY,       -- subject id or measurement id (server PK = client UUID)
    type TEXT NOT NULL,                  -- "SUBJECT" | "MEASUREMENT"
    occurredAtMillis INTEGER NOT NULL,   -- parsed from occurred_at, for ORDER BY / paging
    payloadJson TEXT NOT NULL            -- the rest of the item, serialized (subject or measurement variant)
);
CREATE INDEX idx_activity_occurred ON CachedActivityItem(occurredAtMillis DESC);

selectPage:
SELECT * FROM CachedActivityItem ORDER BY occurredAtMillis DESC LIMIT :limit OFFSET :offset;

upsert:
INSERT OR REPLACE INTO CachedActivityItem VALUES (?, ?, ?, ?);

-- run after each upsert batch: keep only the top CACHE_CAP rows
evictBeyondCap:
DELETE FROM CachedActivityItem
WHERE id NOT IN (SELECT id FROM CachedActivityItem ORDER BY occurredAtMillis DESC LIMIT :cap);

deleteAll:
DELETE FROM CachedActivityItem;
```

`type` also drives the existing segmented tabs (Sujetos/Mediciones) — `HistorySegment.Subjects`
queries `WHERE type = 'SUBJECT'`, matching the endpoint's own `type` filter param 1:1.

## Why capped, not unbounded (revised from the original proposal)

The original proposal cached every item ever fetched, permanently, reasoning that establishments'
"cache raw and overwrite" pattern (ADR-002) didn't fit an unbounded, growing ledger — true, but the
fix isn't "cache everything forever" either. Two reasons a rolling cap is the better shape:

1. **We already paginate against a durable server.** The server is the permanent archive; the
   local cache only needs to cover the realistic offline use case — "what did I do recently" — not
   serve as a second permanent copy of years of history. Unbounded growth duplicates data for a
   benefit (infinite offline scrollback) nobody asked for.
2. **It keeps the local DB's retention story consistent.** The queue already caps itself at 7 days
   (`SyncCoordinator.pruneTerminal`). A "keep everything forever" activity cache would be the only
   table in the app without a bound, which is inconsistent and makes storage growth unpredictable
   over a multi-year install.

~200 items (roughly 1–3 weeks of typical field-worker volume at 20/page) is a starting number, not
a hard requirement — easy to tune once real usage volume is known.

## Fetch/refresh strategy

- **Page 1** is fetched from network whenever `HistoryHubScreen` opens *and* the device is online
  (mirrors `SubjectUploadsViewModel`'s `syncCoordinator.requestSync()`-on-init pattern), plus a
  small prefetch at `AuthedShell` entry for Home's benefit (ADR-005) — upserted into the cache,
  then `evictBeyondCap` runs.
- **Deeper pages** are fetched on `LoadMore` (ADR-010) and upserted the same way, also subject to
  the cap — so only the *most recently viewed* window survives eviction, not necessarily the most
  recently *created* window, if a user scrolls deep into old history. Acceptable: that's a rare
  path, and it only affects offline availability of that deep-scrolled page, not correctness.
- **Offline**, any page request is served straight from `CachedActivityItem`.

## Consequences

- `SqlDelightDatabaseCleaner.clearAll()` **must** add
  `database.cachedActivityItemQueries.deleteAll()` to its transaction (new table = new line, easy
  to forget — flagging per the sqldelight-migration-workflow memory). Full wipe on explicit
  sign-out only, same lifecycle as `PendingSubject`/`PendingMeasurement`/`CachedEstablishment`.
- New table requires the `.sqm` migration file + regenerated `.db` snapshot ceremony
  (`verifyMigrations` guard).
- `payloadJson` needs the flat DTO from ADR-011 — this ADR only fixes *storage* shape.

## Rejected alternatives

- **Unbounded, permanent cache** (original proposal) — rejected per the reasoning above.
- **Wide flat table** (one column per backend field, nullable per variant) — rejected: couples the
  schema to every field the backend happens to expose today, forcing a migration for every new
  display field. The JSON-blob approach only needs a migration when *storage/query* shape changes.
