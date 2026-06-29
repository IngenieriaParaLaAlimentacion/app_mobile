# Offline-First Reads — Grilling Synthesis (2026-06-29)

Outcome of the grilling session on the two live-HTTP reads the measurement screen depends on.
Decisions are in ADR-001…004; glossary in `offline-reads-glossary.md`.

## The reframe that drove everything

The starting framing — *"both reads need an offline strategy"* — was half right. The **establishment**
read is a genuine read-cache problem. The **subject** read is **not**: it's a dependent-write /
sync-ordering problem wearing a read's clothes. Making the picker read local rows is necessary but
~20% of the work; the other 80% is the measurement→subject FK dependency at sync time.

The backend's **client-supplied UUID = server PK** design collapsed the scary part (no id-learning,
happy-path needs no remap). What remained was ordering + the 409/DNI-merge edge.

## Locked decisions

| ADR | Decision |
|-----|----------|
| 001 | Measurement references the subject's `localId`; sync is ordered (subject lands first). Only the 409/DNI-merge needs reconciliation. |
| 002 | Establishments → SQLDelight read-through reference cache. Cache raw + filter at read. Accept stale-write risk (safe dead-letter). Key by user, clear on logout. |
| 003 | Subject↔measurement coupling = **resolve-at-read cross-table query** in measurement `syncable()`. Dissolves the hold rule + 409 remap into one query, no row rewrite. **LEFT JOIN, not inner** (pre-existing server subjects have no local row → release immediately). |
| 004 | Subject picker = union(remote ∪ local), dedup by id. Offer PENDING/SYNCING/SYNCED only; hide MERGED + FAILED (FAILED still lives in the Uploads screen). |

## Suggested build order (bottom-up, compile between phases)

1. **Establishment cache (independent, ship first).** New SQLDelight table (+ `.sqm` + `.db`
   snapshot per the migration guard) → `EstablishmentLocalStore` → make `getMyEstablishments()`
   cache-first → warm at login. No dependency on the subject work. Lowest risk, immediate value.
2. **Subject picker union read.** `SubjectQueryRepository` gains `SubjectLocalStore`; union + dedup +
   state filter (hide MERGED/FAILED). Fixes "offline-created subject is invisible." No schema change.
3. **Measurement resolve-at-read gating.** Change `MeasurementLocalStore.syncable()` to the LEFT-JOIN
   gate against `PendingSubject` and feed the resolved `canonicalId ?: localId` into `toRequest()`.
   This is the keystone — it's what makes the full offline chain actually sync without dead-lettering.

## Residual risks to watch (not blocking, but log them)

- **Engine ordering** is now an optimization, not a correctness requirement (ADR-003) — but verify the
  measurement 404 path is only reachable for *released* rows before relying on instant dead-letter.
- **FAILED subject vanishing from the picker** (ADR-004) — confirm the Uploads screen is discoverable
  enough that a relevador can find/retry it.
- **Cross-table coupling** (ADR-003) couples two stores; keep the join in the measurement local store,
  not leaking subject types into the measurement domain model.
- **Migration ceremony** applies to the new establishment table (ADR-002) — `.sqm` + regenerated
  `.db`, committed together, per the sqldelight-migration-workflow memory.
