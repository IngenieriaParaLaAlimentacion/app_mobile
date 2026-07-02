# ADR-005 — Home's recent-activity list also merges (revised)

- **Status:** Accepted, revised (grilling session 2026-07-01) — supersedes the original
  "Home stays local-only" proposal below the fold.
- **Context glossary:** [activity-feed-glossary.md](./activity-feed-glossary.md)

## Decision

Home's top-4 recent list becomes a **read-only local Flow** over the same merged source History
uses (`queue ∪ CachedActivityItem`, see ADR-008/009), sorted by `occurredAt`/`createdAt` descending,
`take(4)`. Home does **not** trigger its own network fetch — it only reads whatever's already
cached. Freshness comes from two existing/adjacent triggers, not a Home-specific one:

1. Whenever `HistoryHubScreen` is opened, its own page-1 refresh (ADR-008) updates the shared cache.
2. A new small prefetch of activity page 1 at `AuthedShell` entry (mirroring the existing
   "warm establishment cache once if online" `LaunchedEffect` in `AuthedShell`'s `SyncTriggers`),
   so Home has reasonably fresh data even for a user who logs in and never opens History that
   session.

No pagination/`LoadMore` is added to Home — it's a fixed `take(4)`, same shape as today.

## Why the original "stays local-only" proposal was wrong

`HomeStateMapper.deriveState` does **not** only show in-flight/error rows — it takes *all*
subjects/measurements (any `SyncState`) and shows the last 4 by `createdAt`. So Home already
behaves as a short history glance, not purely a sync-status gauge. Under the original proposal,
Home would show an empty or misleadingly sparse list right after a fresh install, right after the
7-day prune clears out recently-synced rows, or for activity that happened on another device —
exactly the gaps the new endpoint exists to close. Since the merge plumbing is being built for
History regardless, reusing it for Home costs one small prefetch call, not a parallel
implementation.

## Consequences

- `HomeViewModel` gains a dependency on the same repository/local-store read used by History's
  merge (ADR-009's "local wins" dedup applies here too, for the same reasons).
- `AuthedShell`'s `SyncTriggers` gains one more warm-cache `LaunchedEffect`, alongside the existing
  establishment one — same pattern, same lifecycle (best-effort, online-only, fire-and-forget).
- Home's badges (Pendiente/Sincronizando/Error) still only apply to `isLocal` rows (ADR-006) —
  a remote-sourced row in Home's top-4 renders with no actionable badge, just its content (name,
  measurement, whatever `RecentActivityUi` already shows for a SYNCED row today).
- No `LoadMore`/pagination surface added to Home — deliberately kept as simple as today.

---

## Original proposal (superseded, kept for record)

Home would stay untouched, deriving purely from the local queue, on the theory that Home's job is
strictly "what's syncing right now" and server data adds nothing. Rejected per the reasoning above
— that framing didn't match what `HomeStateMapper` actually does today.
