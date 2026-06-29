# ADR-004 — Subject picker: union read, hide poison rows

- **Status:** Accepted (grilling session 2026-06-29)

## Decision

The subject picker (`SelectSubjectViewModel` → `GetSubjectsUseCase` →
`SubjectQueryRepository`) becomes a **union read**: remote `/subjects` page ∪ local `PendingSubject`
rows, deduped by id (localId = server PK, so happy-path dedup is exact).

**Only these local states are offered in the picker:** PENDING, SYNCING, SYNCED.

- **MERGED → hidden.** The remote union already carries the canonical `C` entry; hiding the local
  `S` row removes both the duplicate and the 404-poison (selecting `S` would reference a row that
  never existed server-side).
- **FAILED → hidden** from the picker. It remains visible and retryable in the subject **Uploads
  screen** (`SubjectRepository.observeUploads()`), so it is not lost — just not measurable until a
  manual retry brings it back to PENDING/SYNCED. Accepted trade-off: a relevador may briefly not
  find a subject they created; the Uploads screen is where they reconcile it.

## Offline behavior

- Offline, remote `/subjects` throws → picker shows **local rows only** (the just-created PENDING
  subject is now visible — the original bug is fixed).
- Search: server-side `search` param for remote; client-side filter for local rows. Local rows are
  always shown for an empty/short query so a fresh offline subject is reachable without typing.

## Consequence

`SubjectQueryRepository` gains a dependency on `SubjectLocalStore` (today it only wraps
`SubjectApi`). The union + dedup + state-filter lives here.
