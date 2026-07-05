# Glossary — Home "Current Establishment" (Measurement Default)

Working vocabulary for the grilling session on letting a field worker pick a "current
establishment" from Home so it defaults every Measurement upload instead of picking per-submission.

## Core terms

- **Current establishment** — a single `establishmentId` persisted locally (user-scoped), set from
  Home, that pre-fills `MeasurementState.selectedEstablishment` on the Measurement screen. Not a
  server concept — purely a local UX default.
- **Approved establishment** — existing term (`EstablishmentStatus.Approved`, from
  `EstablishmentWithStatus`). Only approved establishments are selectable as the current one; this
  mirrors the existing filter in `MeasurementViewModel.loadEstablishments()`.
- **Pre-fill (not gate)** — the current establishment sets the *initial* value of the Measurement
  screen's own establishment field. It does not remove or disable that field; the worker can still
  change it for a single submission (see **one-off override**).
- **One-off override** — changing the establishment inside the Measurement screen for a single
  upload. It does **not** write back to the current establishment — Home and future measurements
  are unaffected. The Measurement screen's own picker becomes a pure per-submission override, no
  longer a "make a choice from scratch every time" control.
- **Stale current establishment** — a persisted `establishmentId` that is no longer present in the
  latest *approved* list (access revoked, status changed away from Approved, or establishment
  removed). Resolved to "unset" the next time it's checked — never shown as a dangling reference.
- **Source establishment list** — the existing `GetMyEstablishmentsUseCase` → `EstablishmentWithStatus`
  read-through cache (`CachedEstablishment`, ADR-002 in `offline-reads-*`), already warmed at
  `AuthedShell` entry (`RefreshMyEstablishmentsUseCase`). The current-establishment feature adds no
  new establishment data source — it only adds *which one is remembered as default*.

## Non-scope (explicitly ruled out this session)

- **Subject creation** — subjects are not establishment-scoped (`NewSubject` / `PendingSubject` carry
  no `establishmentId`); this feature only touches the Measurement flow.
- **Server-side default** — the current establishment is not synced or POSTed anywhere; it is a
  local convenience value only, same trust tier as UI state, not sync-queue state.
