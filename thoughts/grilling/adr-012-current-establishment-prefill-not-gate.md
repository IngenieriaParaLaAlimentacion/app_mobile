# ADR-012 — Current establishment pre-fills Measurement; it does not replace its picker

- **Status:** Accepted (grilling session 2026-07-04)
- **Context glossary:** [current-establishment-glossary.md](./current-establishment-glossary.md)

## Decision

Home lets the field worker set a **current establishment**. When they open the Measurement screen,
`selectedEstablishment` is pre-filled from it — but the screen's own establishment picker stays in
place. Picking a different establishment there is a **one-off override**: it applies to that
submission only and does not update the current establishment stored from Home.

## Forces

- The field worker's stated need is "don't make me pick the establishment on every upload," not
  "never let me pick a different one." A relevador occasionally visiting a second site for one
  measurement should not have to go back to Home and switch their default just for that.
- `MeasurementViewModel` already has a working establishment picker + auto-select-when-single logic
  (`loadEstablishments()`); reusing it as an override surface is strictly additive, not a rewrite.
- Making the override sticky (writing back to "current") would conflate two different actions
  ("just this once" vs "change my default") behind one UI gesture, which is exactly the kind of
  hidden side effect the grilling session picked apart and ruled out (see ADR-013's stale-selection
  handling, which assumes "current" only changes via Home).

## Consequences

1. `MeasurementViewModel.loadEstablishments()` gains one more precedence rule: if a current
   establishment is set *and* still present in the approved list, it wins over the existing
   `approved.singleOrNull()` auto-select. If not set (or stale — see ADR-014), today's behavior is
   unchanged.
2. `MeasurementState`/`MeasurementAction`/`MeasurementScreen` need no shape change — the existing
   `selectedEstablishment` + `EstablishmentPickerOpen/Dismiss/Selected` actions already model exactly
   "pre-filled, still changeable."
3. Home needs its own read (and write) path to the current establishment, independent of the
   Measurement screen's in-memory state — see ADR-013.

## Rejected alternatives

- *Replace Measurement's picker entirely* — would force a trip back to Home for any one-off visit to
  a different establishment; rejected as worse UX for the stated multi-site-in-a-day case.
- *Override becomes the new default* — conflates "just this once" with "change my default"; rejected
  because it would silently change Home's state from inside an unrelated screen, surprising the
  worker the next time they check Home.
