# ADR-010 — Reuse the existing manual load-more pagination pattern

- **Status:** Accepted (grilling session 2026-07-01)

## Decision (proposed)

`SubjectUploadsViewModel`/`MeasurementUploadsViewModel` (or their post-merge successors) gain the
same manual-pagination shape already used by `SelectSubjectViewModel` for the subject picker:
state carries `page: Int` and `totalPages: Int`; a new `LoadMore` action fetches `page + 1` and
appends to the existing list. No `Paging3`/cursor library is introduced.

## Rationale

This is the one pagination pattern that already exists in the codebase
(`SubjectQueryRepositoryImpl` + `GetSubjectsUseCase` + `SelectSubjectViewModel`), it is already
proven against the exact same envelope shape (`items`/`total`/`page`/`page_size`/`total_pages`)
this new endpoint returns, and the History screen's list (a `LazyColumn`, presumably) can trigger
`LoadMore` the same way any manual-pagination list does (bottom-reached callback), no new
infrastructure needed.

## Offset-pagination drift (accepted risk)

`/me/activity` paginates by `page`/`page_size`, not a cursor. If new activity is created between
this user's page fetches (e.g. a background sync completes mid-scroll), page boundaries can shift,
causing a rare skipped or duplicated row while scrolling. Accepted as a cosmetic, rare glitch —
consistent with ADR-002's "accept the stale-write risk" pattern rather than engineering a cursor
scheme the backend doesn't support. Mitigation, if desired later: dedup already-rendered ids
client-side across pages before appending (cheap, no backend change needed) — worth doing as a
defensive one-liner even without a full cursor scheme.

## Consequences

- Reuses `GetSubjectsUseCase`'s `DEFAULT_PAGE_SIZE = 20` constant/convention for the new
  `GetMyActivityUseCase` (name TBD).
- `HistorySegment.Subjects`/`Measurements` map 1:1 to the endpoint's `type=subject`/`type=measurement`
  filter — each segment's ViewModel calls the endpoint with its own `type`, independent page state
  per segment (switching segments doesn't need to preserve/reset the other's scroll position).
