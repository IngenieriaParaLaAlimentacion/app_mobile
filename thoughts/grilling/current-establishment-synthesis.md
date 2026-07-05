# Home "Current Establishment" — Grilling Synthesis (2026-07-04)

Outcome of the grilling session on the field-worker request: pick a "current establishment" on Home
so measurement uploads default to it instead of picking per-submission. Decisions are in
ADR-012…015; glossary in `current-establishment-glossary.md`.

## The reframe

The starting ask read like a small UI convenience ("remember my last pick"), but two forks made it a
real design decision: **(1)** does the Home selection *replace* Measurement's picker or just *seed*
it, and **(2)** does an override inside Measurement quietly change the Home default, or stay a one-off?
Both were resolved toward the least-surprising option: Home sets a **default**, Measurement keeps a
**full override**, and the two never silently rewrite each other. Everything else (persistence
mechanism, stale handling, Home's always-visible affordance) falls out of that core shape plus the
project's existing SQLDelight-cache conventions — no new architecture needed.

## Locked decisions

| ADR | Decision |
|-----|----------|
| 012 | Current establishment **pre-fills** `MeasurementState.selectedEstablishment`; Measurement's own picker stays and any change there is a **one-off override** — it never writes back to the current establishment. |
| 013 | Persisted as a new **SQLDelight single-row table** (id only, no denormalized snapshot), wired into the existing `SqlDelightDatabaseCleaner` so it's cleared on logout like every other user-scoped table. No new persistence dependency. |
| 014 | Read path **resolves against the latest approved-establishment list** every time; a stale/revoked id silently resolves to "unset" — no warning UI, no denormalized name to show. |
| 015 | Home **always** shows the current-establishment affordance (even with one approved establishment) and lets the worker switch anytime. **"Nueva medición" is never gated** on it — zero current establishment falls back to today's unchanged Measurement behavior. |

## Suggested build order (bottom-up, compile between phases)

1. **Local store (independent, ship first).** New SQLDelight table (+ `.sqm` + regenerated `.db`
   snapshot per the `sqldelight-migration-workflow` memory) → a store mirroring
   `EstablishmentLocalStore`/`DefaultEstablishmentLocalStore` (`get(): String?` / `set(id: String?)`)
   → wire its `deleteAll()` into `SqlDelightDatabaseCleaner.clearAll()`. No dependency on Home or
   Measurement UI; lowest risk.
2. **Resolution use case.** A small use case (or repository method) that takes the stored id + the
   existing `GetMyEstablishmentsUseCase` result and returns `EstablishmentWithStatus?`, applying
   ADR-014's stale-id-to-null resolution in one place so Home and Measurement share the exact same
   rule instead of re-implementing it.
3. **Home UI.** `HomeState`/`HomeAction`/`HomeViewModel` gain the resolved current establishment +
   picker open/select/dismiss actions (shape mirrors Measurement's existing
   `showEstablishmentPicker`/`EstablishmentSelected`); `HomeScreen` renders the affordance. Selecting
   here calls the store's `set()`.
4. **Measurement pre-fill.** `MeasurementViewModel.loadEstablishments()` reads the resolved current
   establishment and lets it win over `approved.singleOrNull()` when present; unchanged otherwise. No
   `MeasurementState`/`MeasurementAction`/`MeasurementScreen` shape changes needed — the override path
   already exists and, per ADR-012, must **not** call the store's `set()`.

## Residual risks to watch (not blocking, but log them)

- **Shared UI component temptation:** Home's and Measurement's establishment pickers will look and
  behave alike (ADR-015 mirrors ADR's existing picker shape) — worth checking during implementation
  whether the design system already has a reusable picker component to extend rather than duplicating
  markup across the two screens.
- **Migration ceremony** applies to the new table (ADR-013) — `.sqm` + regenerated `.db`, committed
  together, per the `sqldelight-migration-workflow` memory; don't skip the verifyMigrations guard.
- **Resolution use case placement:** keep the stale-id check (ADR-014) in one shared place. If Home
  and Measurement each re-implement "is this id still approved," the fallback-to-null rule can drift
  between them.
