# ADR-014 — Stale current establishment resolves to "unset" silently, checked at every read

- **Status:** Accepted (grilling session 2026-07-04)
- **Context glossary:** [current-establishment-glossary.md](./current-establishment-glossary.md)

## Decision

Every time the current establishment is read (Home render, Measurement's `loadEstablishments()`),
the stored `establishmentId` is cross-checked against the **latest approved list**. If it's missing
(access revoked, status changed away from Approved, establishment deleted) the read resolves to
`null` — silently, no error state, no "this establishment is no longer available" banner. Home just
falls back to its unset-picker state; Measurement falls back to today's existing
`approved.singleOrNull()` behavior.

## Forces

- This mirrors how `MeasurementState.selectedEstablishment == null` already behaves: `canSubmit`
  is `false` and the picker is presented — there is already a safe, understood "nothing selected"
  state to fall back to. No new UI state needs inventing.
- Surfacing a "no longer available" flag (the rejected alternative) would require a new persisted
  denormalized name (see ADR-013 — id-only storage means we don't have a name to show for a
  revoked/deleted establishment without an extra fetch), plus a new dismissable-warning UI piece,
  for a case that's rare (access revocation) and self-resolving (worker just picks again).
- Consistent with the "cache raw, filter at read" spirit of ADR-002 (`offline-reads-*`): the
  approved-list *is* the source of truth for validity, not the stored id itself.

## Consequences

1. The local store's read path (or a thin use case wrapping it) takes the current approved list as
   input and returns `EstablishmentWithStatus?`, not a raw `String?` — callers never see a dangling id.
2. No migration/versioning concern: an invalid id is simply filtered out on read, never corrected or
   deleted from the table itself (harmless to leave it — it'll keep resolving to `null` until
   overwritten by a new selection).
3. If the establishment list itself is stale/unfetched offline (rare, cache is warmed at shell entry),
   the check still runs against whatever is cached — same offline behavior the establishment picker
   already has elsewhere.

## Rejected alternatives

- *Keep showing the stale name, flagged as unavailable* — needs a denormalized name snapshot (conflicts
  with ADR-013's id-only storage) and a new UI affordance, for a rare, self-resolving edge case.
