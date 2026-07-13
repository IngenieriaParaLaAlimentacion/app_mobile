---
date: 2026-07-13T16:29:36-03:00
researcher: juanduzac
git_commit: d3dd503c41901545df7e829ab4500183d8ce8fdf
branch: feature/survey-response-activity-integration
repository: app_mobile
topic: "Home quick action → surveys/available fails offline: what gates survey responses from being offline-first"
tags: [research, codebase, surveys, offline-first, cached-survey, sync, survey-list, home-quick-actions]
status: complete
last_updated: 2026-07-13
last_updated_by: juanduzac
---

# Research: surveys/available offline gap in the Home quick-action flow

**Date**: 2026-07-13T16:29:36-03:00
**Researcher**: juanduzac
**Git Commit**: d3dd503c41901545df7e829ab4500183d8ce8fdf
**Branch**: feature/survey-response-activity-integration
**Repository**: app_mobile

## Research Question

The app is offline-first, but when the user taps the Home quick action for responding surveys, the flow calls `GET api/v1/surveys/available?page=1&page_size=20`, which fails offline. There is no offline-first support for that part of the flow, so it gates survey responses from being offline-first. Where exactly is the gap, what already exists, and what pattern should close it?

## Summary

The gap is **one deliberate seam**: `SurveyRepositoryImpl.getAvailable` (`composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/survey/SurveyRepositoryImpl.kt:21-25`) is a live-only call. It *writes* every fetched summary into the SQLDelight `CachedSurvey` table (write-through) but **never reads the cache back** — offline, Ktor throws, `runCatching` returns failure, and `SurveyListViewModel` shows the error + "Reintentar" screen. This was an explicit design decision recorded in both the repo KDoc (`SurveyRepositoryImpl.kt:11-15`) and the original plan (`thoughts/plans/survey_responses_offline_first.md:1149-1151`): *"availability is time-sensitive, always prefer live"*.

Everything downstream of the list is already offline-capable:
- **Survey detail (questions)** is cache-first via `CachedSurvey.detailJson` (`SurveyRepositoryImpl.kt:27-40`) — a survey opened once online is fully answerable offline.
- **Response submission** goes through a full outbox (`PendingSurveyResponse.sq` + `SurveyResponseRepositoryImpl.syncPending`), drained by the shared `SyncCoordinator`.
- **Establishment selection** in the form is cache-first and warmed on shell entry.

So the survey flow is offline-first *except for its front door*. Closing it needs: (1) an offline read fallback in `getAvailable` (the `selectAll` query already exists in `CachedSurvey.sq:14-15` — nothing reads it), and (2) a cache warm on shell entry mirroring `refreshMyEstablishments`/`warmActivityCache` in `AuthedShell`. A secondary gap: `detailJson` is only populated when a survey is *opened* online, so a warmed list alone still can't answer a never-opened survey offline — full offline-first also wants detail prefetch during the warm.

## Detailed Findings

### 1. The exact flow that fails

1. **Home quick action**: "Encuesta" tile fires `HomeAction.RespondSurveyClick` (`presentation/feature/home/HomeScreen.kt:262-267`, `HomeAction.kt:13`).
2. **Reducer**: `HomeViewModel.kt:111-112` emits `HomeDestination.NavigateToSurveyList`.
3. **Dispatch**: handled in `app/AuthedShell.kt:103` (Home is composed inline on the inner NavHost; `HomeNavigation.kt` is not the live dispatch) → `outerNav.navigateToSurveyList()` → `Route.SurveyList` (`presentation/feature/survey/survey_list/SurveyListNavigation.kt:9-10`, `presentation/navigation/Route.kt:87`).
4. **Fetch**: `SurveyListViewModel.init` loads page 1 (`SurveyListViewModel.kt:24-26`); `load()` (`:45-71`) → `GetAvailableSurveysUseCase` (`domain/usecase/survey/GetAvailableSurveysUseCase.kt:6-9`, pass-through) → `SurveyRepository.getAvailable` (`domain/repository/SurveyRepository.kt:14`) → `SurveyRepositoryImpl.getAvailable` → `SurveyApi.available` (`data/survey/SurveyApi.kt:19-26`):
   ```kotlin
   client.get("api/v1/surveys/available") {
       parameter("page", page)
       parameter("page_size", pageSize)
   }
   ```
5. **Offline behavior**: Ktor throws → `Result.failure` → `loadError = page == 1` (`SurveyListViewModel.kt:66`) → error UI "No se pudieron cargar las encuestas." + "Reintentar" (`SurveyListScreen.kt:86-99`). A failed *load-more* is silently dropped (`isLoadingMore` cleared, no error UI).

### 2. The gap itself: write-through, not read-through

`data/survey/SurveyRepositoryImpl.kt:21-25`:
```kotlin
override suspend fun getAvailable(page: Int, pageSize: Int): Result<PagedSurveys> = runCatching {
    val response = api.available(page, pageSize)
    local.upsertSummaries(response.items.map { it.toDomain() })  // cache written…
    response.toDomain()                                          // …but never read back
}
```
The class KDoc (`:11-15`) documents this as intentional: availability is time-sensitive, so the list always prefers live. `CachedSurvey.sq` already has a `selectAll` query (`sqldelight/ar/com/ingenieriaalimentacion/data/db/CachedSurvey.sq:14-15`) that **nothing reads for the list screen** — the cache is only read by `getDetail` (via `findDetail`).

### 3. What already exists for surveys (built on this branch's lineage)

- **`CachedSurvey.sq`** (`CachedSurvey.sq:4-12`): `id, name, scope, isActive, questionCount, detailJson (nullable), updatedAt`. Queries: `selectAll`, `selectById`, `insertOrReplace`, `deleteAll`. Dialect note (`:20-24`): sqlite_3_18, no UPSERT — `INSERT OR REPLACE` full-row with read-before-write in a transaction.
- **`DefaultSurveyLocalStore`** (`data/survey/local/DefaultSurveyLocalStore.kt`): `upsertSummaries` (`:33-52`) re-reads existing `detailJson` inside one transaction so a list refresh never nulls a cached detail; `upsertDetail` (`:54-64`); `findDetail` (`:27-31`). Interface: `SurveyLocalStore.kt:10-21` — **has no summaries read method yet**.
- **Cache-first detail**: `SurveyRepositoryImpl.getDetail` (`:27-40`) — `local.findDetail` short-circuits; on miss `GET api/v1/surveys/{id}` (`SurveyApi.kt:28-39`, typed `Found/Forbidden/NotFound/Retryable` outcomes) then `upsertDetail`.
- **Response outbox**: `PendingSurveyResponse.sq` (localId PK doubling as wire idempotency id, denormalized display columns, `syncState PENDING|SYNCING|SYNCED|FAILED`, `attemptCount`); engine `SurveyResponseRepositoryImpl.kt:30-96` (structural port of the subject engine); edit outbox `PendingSurveyResponseEdit.sq` + `SurveyResponseEditRepositoryImpl`.
- **DI**: `di/RepositoriesModule.kt:113-120`, `di/DatabaseModule.kt:22-24` (queries), engines registered at `DatabaseModule.kt:27-34`.

### 4. The app's established offline READ patterns (what to copy)

Three variants in use:

- **Variant A — cache-first, network on cold cache** (`data/profile/ProfileRepositoryImpl.kt:34-49`, establishments): warm cache wins; a separate best-effort `refreshMyEstablishments()` prefetch keeps it fresh and silently succeeds offline. Freshness comes from the warm, not the read path.
- **Variant B — network-first with local fallback** (`data/subject/SubjectQueryRepositoryImpl.kt:23-47`, subject search): try remote; on failure serve local rows as a single self-contained page.
- **Variant C — union of queue + persistent page cache** (`data/measurement/MeasurementHistoryRepositoryImpl.kt:32-77`, mirrored by `SurveyResponseHistoryRepositoryImpl.kt:40-68`): remote page written through to `CachedActivityItem` on success; on failure serve `queue ∪ cachedPage`; local wins on id collision (ADR-009).

Given the "availability is time-sensitive" concern, **Variant B (network-first, cache fallback on failure)** preserves live freshness when online while unlocking offline — the smallest-delta fix. Variant A would also work if staleness is acceptable.

### 5. Cache warming infrastructure (already runs on shell entry)

`app/AuthedShell.kt:124-148` (`SyncTriggers`) already does one-shot best-effort warms:
```kotlin
LaunchedEffect(Unit) { if (connectivity.isOnline()) refreshMyEstablishments() }
LaunchedEffect(Unit) { if (connectivity.isOnline()) warmActivityCache() }
```
plus `LifecycleStartEffect { coordinator.requestSync() }` (`:131-134`) and a 15-minute foreground backstop (`:143-148`). A `WarmSurveysUseCase` calling `getAvailable(1, N)` would slot in here — the existing write-through at `SurveyRepositoryImpl.kt:23` already populates the cache as a side effect.

### 6. Secondary gaps that still block full offline-first

1. **Never-opened surveys have no questions offline.** `detailJson` is populated only by `getDetail` (i.e., when the user opens a survey online). A warmed *list* lets the user see and tap a survey offline, but the form's `SurveyResponseFormViewModel.load()` (`presentation/feature/survey/survey_response_form/SurveyResponseFormViewModel.kt:79-105`) would hit `getDetail` → cache miss → network → `loadError`. Full offline-first wants the warm to also prefetch details for cached surveys missing `detailJson` (payloads are small — noted at `thoughts/plans/survey_responses_offline_first.md:1578`).
2. **Offline pagination semantics.** The cache holds the union of all pages ever fetched, unordered by page. An offline fallback should serve `selectAll` as one self-contained page (`totalPages = 1`), mirroring Variant B's subject-search fallback, rather than pretending to paginate.
3. **Staleness/eligibility.** A cached survey may have been deactivated server-side (`isActive`). The submit path is already resilient — the outbox's typed outcomes dead-letter non-retryable rejections — so serving a slightly stale list offline degrades gracefully, same trade-off ADR-002 accepted for establishments.
4. **Silent load-more failures** (`SurveyListViewModel.kt:64-67`): unrelated to offline but worth noting — a failed page-2+ load shows nothing.
5. **Form prerequisites are already offline-safe**: approved establishments are cache-first (Variant A) and warmed; the current-establishment pre-selection is a local single-row store (ADR-013).

## Code References

- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/survey/SurveyRepositoryImpl.kt:21-25` — **the gap**: live-only `getAvailable`, write-through cache never read
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/survey/SurveyRepositoryImpl.kt:27-40` — cache-first `getDetail` (already offline-capable)
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/survey/SurveyApi.kt:19-26` — `GET api/v1/surveys/available` with `page`/`page_size`
- `composeApp/src/commonMain/sqldelight/ar/com/ingenieriaalimentacion/data/db/CachedSurvey.sq:14-15` — unused `selectAll` query
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/survey/local/DefaultSurveyLocalStore.kt:33-52` — `upsertSummaries` preserving `detailJson`
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/survey/survey_list/SurveyListViewModel.kt:45-71` — load/error handling; `:66` sets `loadError` on page-1 failure
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/survey/survey_list/SurveyListScreen.kt:86-99` — offline error UI ("Reintentar")
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/home/HomeScreen.kt:262-267` — "Encuesta" quick action
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/AuthedShell.kt:103` — quick-action navigation dispatch; `:124-148` — warm/refresh triggers to extend
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/profile/ProfileRepositoryImpl.kt:34-49` — Variant A cache-first pattern (establishments)
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/subject/SubjectQueryRepositoryImpl.kt:23-47` — Variant B network-first-with-fallback pattern
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/usecase/activity/WarmActivityCacheUseCase.kt:12-23` — existing warm use case shape
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/subject/sync/SyncCoordinator.kt:20-53` — single-flight sync coordinator (4 engines, `DatabaseModule.kt:27-34`)
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/survey/survey_response_form/SurveyResponseFormViewModel.kt:79-105` — form load; would fail offline for never-opened surveys

## Architecture Insights

- The app's offline-first backbone is: SQLDelight caches/outboxes as source of truth, `SyncCoordinator` (single-flight mutex, connectivity + foreground + 15-min triggers), and best-effort warms in `AuthedShell.SyncTriggers`. Read freshness is achieved by *warming*, not by making read paths network-first.
- The surveys list is the only reference-data read in the app that is live-only by design. The decision predates the "offline-first everywhere" expectation; the KDoc rationale ("availability is time-sensitive") is now outweighed by the field-worker use case, and the submit path's typed dead-lettering already absorbs the staleness risk.
- SQLDelight dialect is sqlite_3_18 (no UPSERT); schema changes require a numbered `.sqm` migration (`verifyMigrations = true`, `composeApp/build.gradle.kts:12-24`). **Closing this gap needs no schema change** — table and queries already exist; only a `SurveyLocalStore.getSummaries()` method, a fallback branch in `getAvailable`, and a warm hook are missing.

## Historical Context (from thoughts/)

- `thoughts/plans/survey_responses_offline_first.md` — the original plan; defines `CachedSurvey` as a read-through cache but records the deviation at `:1149-1151`: `getAvailable` shipped as "cache written but not read-through — availability is time-sensitive, always prefer live". This research documents why that decision now gates the flow.
- `thoughts/research/2026-07-13_14-34-55_survey-response-activities-sync.md` — earlier today's research; found the survey-response *history* caching gap and unwired edit retry (different gap: history, not availability).
- `thoughts/plans/survey_response_activities_tab.md` — Phase 1 closes the history-cache gap via `ActivityItemType.SURVEY_RESPONSE`.
- `thoughts/grilling/adr-002-establishment-read-through-cache.md` — the precedent for accepting slight staleness on reference data in exchange for offline reads.
- `thoughts/grilling/adr-009-local-wins-dedup.md`, `adr-010-reuse-manual-load-more.md` — merge/pagination conventions an offline list fallback should respect.
- `thoughts/plans/clear_local_db_on_sign_out.md` (in progress, uncommitted) — sign-out already plans `cachedSurveyQueries.deleteAll()`, so the cache lifecycle is covered.

## Related Research

- `thoughts/research/2026-07-13_14-34-55_survey-response-activities-sync.md` — survey response uploads vs. activities feed sync parity

## Open Questions

1. Should the offline list show *all* cached surveys or filter `isActive = 1`? (Server presumably filters `/available`; the cache may retain rows the server no longer returns until the next warm's upsert — note `upsertSummaries` upserts but never deletes absent rows. A `replaceAllSummaries` on warm, preserving detailJson, would fix drift.)
2. Should the warm prefetch `detailJson` for every cached survey (full offline answering) or only summaries (list browsable, form still needs one online open)? Payload size argues for full prefetch.
3. Should the offline fallback be surfaced in UI (e.g. an "offline — mostrando encuestas guardadas" banner) consistent with how Home surfaces `PendingOffline`?
