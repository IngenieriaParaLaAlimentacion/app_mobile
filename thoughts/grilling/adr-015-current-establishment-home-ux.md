# ADR-015 — Home always shows the current-establishment affordance; "Nueva medición" stays ungated

- **Status:** Accepted (grilling session 2026-07-04)
- **Context glossary:** [current-establishment-glossary.md](./current-establishment-glossary.md)

## Decision

Home renders a current-establishment affordance (e.g. a chip/row naming the establishment, tap to
reopen the picker) **regardless of how many approved establishments the worker has** — including the
single-establishment case where `MeasurementViewModel` today auto-selects with no picker at all.
Tapping it is how the worker switches at any time (e.g. moving to a second site mid-shift).

"Nueva medición" is **never blocked** on having a current establishment set. If none is set (first
launch, or resolved to unset per ADR-014), Measurement falls back to its existing behavior
unchanged: auto-select when exactly one approved establishment exists, its own picker otherwise.

## Forces

- Always showing the affordance keeps the concept visible and consistent, rather than having Home's
  UI shape change depending on establishment count — one less conditional to design/test, and it
  gives even single-site workers a visible confirmation of "which site am I on," which doubles as
  a lightweight sanity check before measuring.
- Not gating "Nueva medición" preserves today's zero-friction first-run path: a brand-new worker with
  one approved establishment can measure immediately, exactly as they can today, with no new required
  step inserted in front of it.
- Together these mean the feature is purely additive on top of existing Measurement behavior — nothing
  currently working (auto-select-when-single, manual-pick-when-multiple, submit validation) needs to
  change for the zero-current-establishment case.

## Consequences

1. Home's state (`HomeState` / `HomeViewModel`) gains a current-establishment field (resolved per
   ADR-014) and actions to open/select/dismiss a picker — same shape as Measurement's existing
   `showEstablishmentPicker` / `EstablishmentSelected` handling, reusable as a shared picker component
   if one doesn't already exist at the design-system level.
2. No new blocking/disabled state is added to the "Nueva medición" CTA.
3. Switching is always available from Home; there is no separate "lock" state — consistent with
   ADR-012's decision that only Home changes the current establishment (Measurement's override is a
   one-off, never sticky).

## Rejected alternatives

- *Auto-set silently with no picker when there's only one approved establishment* — would make Home's
  UI shape conditional on establishment count and hide the switch affordance for the majority
  (single-site) case, where showing it costs nothing and adds a confirmation signal.
- *Block "Nueva medición" until a current establishment is explicitly chosen* — adds a forced step
  that doesn't exist today and isn't needed, since Measurement already has a safe fallback.
