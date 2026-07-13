# Survey List & Answering Fully Offline-First Implementation Plan

## Overview

Close the last online-only seam in the survey flow: the survey *list* (`GET /surveys/available`) is live-only, so the Home quick action "Encuesta" dead-ends offline even though detail, form prerequisites, and submission are already offline-capable. After this plan, a field worker who opened the app online once can — fully offline — browse the survey list, open any survey, answer it, and queue the response.

Decisions locked with the user (2026-07-13):
1. **Full warm**: shell-entry warm fetches the whole `/available` snapshot *and* prefetches `detailJson` for every survey missing it, so never-opened surveys are answerable offline.
2. **Replace-all on warm**: the warm replaces the cache (preserving `detailJson`) so server-side deactivated/removed surveys don't linger (drift fix). The list screen's own pagination keeps upsert-only semantics (it sees partial pages).
3. **Offline banner**: when the list is served from cache, show an `AlInfoCard` banner ("Sin conexión — mostrando encuestas guardadas") with a "Reintentar" affordance.

Read strategy: **network-first with cache fallback** (research Variant B, same as `SubjectQueryRepositoryImpl`), preserving the original "availability is time-sensitive" freshness property when online.

## Current State Analysis

- **The gap**: `SurveyRepositoryImpl.getAvailable` (`composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/survey/SurveyRepositoryImpl.kt:21-25`) writes every fetched summary into `CachedSurvey` but never reads it back. Offline → Ktor throws → `SurveyListViewModel.kt:66` sets `loadError` → "Reintentar" error screen (`SurveyListScreen.kt:86-99`).
- **Already offline-capable**: `getDetail` is cache-first (`SurveyRepositoryImpl.kt:27-40`); submission uses the `PendingSurveyResponse` outbox + `SyncCoordinator`; establishments are cache-first and warmed; current-establishment is a local store.
- **Cache exists, unread**: `CachedSurvey.sq` has `selectAll` (`:14-15`) and `deleteAll` (`:29-30`) that nothing on the list path uses. **No schema migration needed** — only new named queries (queries don't touch `verifyMigrations`).
- **Warm infra exists**: `AuthedShell.SyncTriggers` (`app/AuthedShell.kt:124-148`) already runs one-shot best-effort warms (`refreshMyEstablishments`, `warmActivityCache`) gated on `connectivity.isOnline()`.
- **Detail prefetch gap**: `detailJson` is only populated by `getDetail`, i.e. when a survey is *opened* online (`SurveyResponseFormViewModel.kt:79-105` fails offline for never-opened surveys).

### Key Discoveries:
- `Survey.createdAt` (`domain/model/survey/Survey.kt:15`) is set by the DTO mapper but **consumed nowhere** (list renders only `name` + `questionCount`, `SurveyListScreen.kt:115-116`). `CachedSurvey` doesn't store it, so it blocks a lossless cache→domain read. Decision: **drop the field** instead of adding a schema migration.
- `DefaultSurveyLocalStore.upsertSummaries` (`DefaultSurveyLocalStore.kt:33-52`) already shows the transaction + preserve-`detailJson` idiom `replaceAllSummaries` must follow (sqlite_3_18, no UPSERT — full-row `INSERT OR REPLACE` with read-before-write).
- Fallback pattern to copy: `SubjectQueryRepositoryImpl.searchSubjects` (`data/subject/SubjectQueryRepositoryImpl.kt:23-47`) — remote-first; on failure serve local rows as one self-contained page (`totalPages = 1`); deeper pages pass the failure through.
- Warm shape to copy: `WarmActivityCacheUseCase` (`domain/usecase/activity/WarmActivityCacheUseCase.kt`) + its `AuthedShell` hook (`AuthedShell.kt:139-142`); DI in `di/UseCasesModule.kt:77`.
- Banner shape to copy: `AlInfoCard` usage in `HomeScreen.kt:190-195` (`SyncStatusUi.OfflineClean`).
- Test conventions: `commonTest` with hand-written fakes under `fake/` + Ktor `MockEngine` (`SurveyResponseSyncEngineTest.kt:28-53`); `runTest`.

## Desired End State

- Online: list behavior unchanged (live pages, write-through cache).
- Offline with warm cache: list loads instantly from `CachedSurvey` (active surveys only, one page, banner shown), every survey opens and is answerable, responses queue in the outbox.
- Offline with cold cache (never logged in online): existing error screen unchanged.
- On shell entry while online: full snapshot warm (all pages, capped) + detail prefetch for missing `detailJson`, best-effort and silent.

Verify: airplane-mode walkthrough (see Testing Strategy) + new unit tests green.

## What We're NOT Doing

- No schema change / `.sqm` migration (`CachedSurvey` columns suffice once `createdAt` is dropped from the domain model).
- No offline pagination emulation — the cache is served as a single self-contained page (`totalPages = 1`), mirroring the subject-search fallback.
- No reactive `Flow`-based list (the app's convention is suspend + explicit refresh; not changing that here).
- No fix for the silent load-more failure (`SurveyListViewModel.kt:64-67`) beyond what falls out naturally — it's pre-existing and unrelated to offline.
- No change to the survey-response *history* caching gap (covered by `thoughts/plans/survey_response_activities_tab.md`).
- No connectivity-listener auto-refresh of an on-screen cached list; the banner's "Reintentar" covers it.

## Implementation Approach

Four phases, each independently compilable and testable. Data layer first (store read-back + replace-all), then the repository fallback, then the warm, then UI. Each phase ends with `./scripts/compile.sh` and the relevant unit tests.

---

## Phase 1: Local store read-back + replace-all

### Overview
Give `SurveyLocalStore` the three capabilities everything else builds on: read summaries back as domain models, atomically replace the snapshot preserving `detailJson`, and list ids missing details. Also drop the dead `createdAt` field that blocks lossless read-back.

### Changes Required:

#### 1. Drop unused `createdAt` from the domain summary
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/model/survey/Survey.kt`
**Changes**: remove `val createdAt: String,` from `Survey`.

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/survey/mapper/SurveyMapper.kt`
**Changes**: remove `createdAt = createdAt,` from `SurveyListItemResponse.toDomain()`. (DTO keeps the field; we just stop mapping it.) Fix any test fixture that constructs `Survey(...)` with `createdAt`.

#### 2. New query: ids missing detail
**File**: `composeApp/src/commonMain/sqldelight/ar/com/ingenieriaalimentacion/data/db/CachedSurvey.sq`
**Changes**: add a named query (no migration needed — schema untouched):

```sql
selectIdsMissingDetail:
SELECT id FROM CachedSurvey WHERE detailJson IS NULL;
```

#### 3. Row → domain mapper
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/survey/mapper/SurveyLocalMapper.kt`
**Changes**: add alongside `toDbString`/`toDomainSurveyScope`:

```kotlin
internal fun CachedSurvey.toDomainSurvey(): Survey = Survey(
    id = id,
    name = name,
    scope = scope.toDomainSurveyScope(),
    isActive = isActive == 1L,
    questionCount = questionCount.toInt(),
)
```
(`CachedSurvey` is the SQLDelight-generated row type in `ar.com.ingenieriaalimentacion.data.db`.)

#### 4. Store interface + implementation
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/survey/local/SurveyLocalStore.kt`
**Changes**: add three methods with KDoc in the file's existing style:

```kotlin
/** All cached summaries, name-ordered — the offline fallback for GET /surveys/available. */
suspend fun getSummaries(): List<Survey>

/** Replaces the entire snapshot with [surveys] (warm path). Rows absent from [surveys] are
 *  deleted (drift fix); rows present keep any existing `detailJson`, same guarantee as
 *  [upsertSummaries]. One transaction. */
suspend fun replaceAllSummaries(surveys: List<Survey>)

/** Ids of cached surveys whose questions were never fetched — the warm's detail-prefetch list. */
suspend fun idsMissingDetail(): List<String>
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/survey/local/DefaultSurveyLocalStore.kt`
**Changes**: implement, following the existing transaction idiom:

```kotlin
override suspend fun getSummaries(): List<Survey> = withContext(dispatcher) {
    queries.selectAll().executeAsList().map { it.toDomainSurvey() }
}

override suspend fun replaceAllSummaries(surveys: List<Survey>): Unit = withContext(dispatcher) {
    val nowMs = now()
    queries.transaction {
        // Snapshot existing detailJson before wiping so the replace never loses a cached detail.
        val existingDetailJson = queries.selectAll().executeAsList()
            .filter { it.detailJson != null }
            .associate { it.id to it.detailJson }
        queries.deleteAll()
        surveys.forEach { survey ->
            queries.insertOrReplace(
                id = survey.id,
                name = survey.name,
                scope = survey.scope.toDbString(),
                isActive = if (survey.isActive) 1L else 0L,
                questionCount = survey.questionCount.toLong(),
                detailJson = existingDetailJson[survey.id],
                updatedAt = nowMs,
            )
        }
    }
}

override suspend fun idsMissingDetail(): List<String> = withContext(dispatcher) {
    queries.selectIdsMissingDetail().executeAsList()
}
```

#### 5. Store unit tests
**File**: `composeApp/src/commonTest/kotlin/ar/com/ingenieriaalimentacion/data/survey/DefaultSurveyLocalStoreTest.kt` (new)
**Changes**: use the project's in-memory SQLDelight driver setup (mirror whichever existing local-store test does this; if none exists for stores, follow `SurveyResponseSyncEngineTest`'s structure with a real `CachedSurveyQueries` over an in-memory driver — check `commonTest` for an existing in-memory DB helper first and reuse it). Cover:
- `getSummaries` returns rows name-ordered, mapped correctly (scope, isActive, questionCount).
- `replaceAllSummaries` deletes rows absent from the new set.
- `replaceAllSummaries` preserves `detailJson` for surviving rows.
- `idsMissingDetail` returns only ids with `detailJson IS NULL`.

### Success Criteria:

#### Automated Verification:
- [x] Kotlin compilation passes: `./scripts/compile.sh`
- [x] Unit tests pass: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:testDebugUnitTest --no-daemon`

#### Manual Verification:
- [ ] None (pure data layer; behavior unchanged until Phase 2).

---

## Phase 2: Repository offline fallback (`fromCache` flag)

### Overview
Make `getAvailable` network-first with a cache fallback on page-1 failure, and carry a `fromCache` flag to the presentation layer.

### Changes Required:

#### 1. Domain: `fromCache` on the page model
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/repository/SurveyRepository.kt`
**Changes**:

```kotlin
data class PagedSurveys(
    val items: List<Survey>,
    val page: Int,
    val totalPages: Int,
    /** True when served from the local cache because the network failed (offline fallback). */
    val fromCache: Boolean = false,
)
```
`SurveyMapper.toDomain()` needs no change (default `false`).

#### 2. Repository fallback branch
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/survey/SurveyRepositoryImpl.kt`
**Changes**: replace `getAvailable` and update the class KDoc (it currently documents the live-only decision):

```kotlin
/**
 * [getAvailable] is network-first — availability is time-sensitive, so a live page always wins —
 * with a cache fallback: on a page-1 network failure the CachedSurvey snapshot (last warm or
 * last successful fetch) is served as a single self-contained page, active surveys only, flagged
 * [PagedSurveys.fromCache]. Deeper pages never fall back (the cache is unpaged). [getDetail] is
 * cache-first: any survey warmed or opened once online is answerable fully offline.
 */
```

```kotlin
override suspend fun getAvailable(page: Int, pageSize: Int): Result<PagedSurveys> {
    val remote = runCatching {
        val response = api.available(page, pageSize)
        local.upsertSummaries(response.items.map { it.toDomain() })
        response.toDomain()
    }
    if (remote.isSuccess || page != 1) return remote

    // Offline fallback: serve the cached snapshot as one self-contained page (totalPages = 1),
    // same shape as SubjectQueryRepositoryImpl's fallback. Active-only: the server never returns
    // inactive surveys from /available, so cached inactive rows must not resurface offline.
    val cached = local.getSummaries().filter { it.isActive }
    return if (cached.isNotEmpty()) {
        Result.success(PagedSurveys(items = cached, page = 1, totalPages = 1, fromCache = true))
    } else {
        remote
    }
}
```

#### 3. Repository unit tests
**File**: `composeApp/src/commonTest/kotlin/ar/com/ingenieriaalimentacion/data/survey/SurveyRepositoryImplTest.kt` (new)
**Changes**: `MockEngine`-backed `SurveyApi` + a `FakeSurveyLocalStore` (new, under `commonTest/.../fake/`, implementing the full `SurveyLocalStore` interface with in-memory maps). Cover:
- Online success: live page returned, `fromCache = false`, summaries upserted.
- Page-1 network failure with non-empty cache: cached items returned, `fromCache = true`, `totalPages = 1`, inactive surveys filtered out.
- Page-1 network failure with empty cache: original failure propagates.
- Page-2 network failure: failure propagates (no fallback).

### Success Criteria:

#### Automated Verification:
- [x] Kotlin compilation passes: `./scripts/compile.sh`
- [x] Unit tests pass: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:testDebugUnitTest --no-daemon`

#### Manual Verification:
- [ ] Open survey list online, kill connectivity, reopen list → previously fetched surveys appear (no error screen).

---

## Phase 3: Full warm — snapshot replace + detail prefetch

### Overview
On authed-shell entry while online, fetch the full `/available` snapshot (all pages, capped), replace the cache, then prefetch `detailJson` for every survey missing it. Best-effort and silent, mirroring the existing warms.

### Changes Required:

#### 1. Repository warm method
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/repository/SurveyRepository.kt`
**Changes**: add to the interface:

```kotlin
/** Best-effort full-snapshot warm: fetches every /available page (capped), replaces the local
 *  cache (drift fix, detailJson preserved), then prefetches details for surveys missing them.
 *  Never throws; partial progress is kept. */
suspend fun warmAvailableSurveys()
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/survey/SurveyRepositoryImpl.kt`
**Changes**: implement:

```kotlin
override suspend fun warmAvailableSurveys() {
    // 1. Full snapshot: page through /available. Any page failing aborts the replace (a partial
    //    snapshot must not delete surveys that live on unfetched pages) but keeps upsert progress
    //    already written by getAvailable's write-through.
    val all = mutableListOf<Survey>()
    var page = 1
    var totalPages: Int
    do {
        val result = runCatching { api.available(page, WARM_PAGE_SIZE) }.getOrNull() ?: return
        all += result.items.map { it.toDomain() }
        totalPages = result.totalPages
        page++
    } while (page <= totalPages && page <= MAX_WARM_PAGES)
    if (page <= totalPages) {
        // Snapshot truncated by the cap: fall back to upsert-only so uncached tail pages'
        // rows (from an earlier, larger warm) aren't deleted.
        local.upsertSummaries(all)
    } else {
        local.replaceAllSummaries(all)
    }

    // 2. Detail prefetch, one by one, best-effort: getDetail is cache-first so only missing
    //    details hit the network, and each success is cached immediately.
    local.idsMissingDetail().forEach { id -> getDetail(id) }
}

private companion object {
    const val WARM_PAGE_SIZE = 50
    const val MAX_WARM_PAGES = 5   // 250 surveys — far above any realistic tenant
}
```
Note: `getDetail`'s typed non-retryable outcomes (`Forbidden`/`NotFound`) return `Result.failure` without caching — the warm simply moves on; those surveys stay detail-less and behave exactly as today when opened.

#### 2. Warm use case + DI + shell hook
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/usecase/survey/WarmSurveyCacheUseCase.kt` (new)
**Changes**: mirror `WarmActivityCacheUseCase`:

```kotlin
/**
 * Best-effort prefetch of the full survey snapshot + question sets, so the survey list and
 * response form work fully offline later. Mirrors
 * [ar.com.ingenieriaalimentacion.domain.usecase.activity.WarmActivityCacheUseCase].
 */
class WarmSurveyCacheUseCase(private val surveys: SurveyRepository) {
    suspend operator fun invoke() = surveys.warmAvailableSurveys()
}
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/UseCasesModule.kt`
**Changes**: `factory { WarmSurveyCacheUseCase(get()) }` next to the other survey use cases (import accordingly).

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/AuthedShell.kt`
**Changes**: in `SyncTriggers`, add a parameter `warmSurveyCache: WarmSurveyCacheUseCase = koinInject()` and a one-shot effect after the activity warm (`AuthedShell.kt:139-142`):

```kotlin
LaunchedEffect(Unit) {
    // One-shot: warm the survey snapshot + details so surveys are answerable fully offline later.
    if (connectivity.isOnline()) warmSurveyCache()
}
```

#### 3. Warm unit tests
**File**: `composeApp/src/commonTest/kotlin/ar/com/ingenieriaalimentacion/data/survey/SurveyRepositoryImplTest.kt` (extend Phase 2 file)
**Changes**: cover via `MockEngine` + `FakeSurveyLocalStore`:
- Multi-page snapshot: two pages fetched, `replaceAllSummaries` called with the union.
- Page-2 fetch failure: no replace (fake records no `replaceAllSummaries` call); no throw.
- Detail prefetch: only `idsMissingDetail` ids hit `GET /surveys/{id}`; each success cached via `upsertDetail`.
- One detail 404s: warm continues to the next id, no throw.
- Cap truncation: `totalPages > MAX_WARM_PAGES` → `upsertSummaries` (not replace) with the fetched pages.

### Success Criteria:

#### Automated Verification:
- [x] Kotlin compilation passes: `./scripts/compile.sh`
- [x] Unit tests pass: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:testDebugUnitTest --no-daemon` (survey suite green; 3 pre-existing unrelated failures on `main` confirmed via `git stash`)

#### Manual Verification:
- [ ] Fresh install → sign in online → immediately enable airplane mode → survey list shows all surveys and **every** survey opens with its questions (never-opened ones included).
- [ ] Warm adds no visible jank/delay to shell entry (it's fire-and-forget in a `LaunchedEffect`).

---

## Phase 4: Offline banner on the survey list

### Overview
Surface the cache-served state: an `AlInfoCard` above the list with a "Reintentar" affordance that re-attempts a live load (and silently re-serves cache if still offline — no error flash, since the fallback returns success).

### Changes Required:

#### 1. State + ViewModel
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/survey/survey_list/SurveyListState.kt`
**Changes**: add `val fromCache: Boolean = false` to `SurveyListState`. `canLoadMore` needs no change (`totalPages = 1` already disables load-more for cached pages).

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/survey/survey_list/SurveyListViewModel.kt`
**Changes**: in `load`'s `onSuccess`, add `fromCache = paged.fromCache` to the `copy`. (`RetryClick -> load(page = 1)` already exists and now doubles as the banner's action: a live success overwrites the list and clears the flag; a still-offline retry re-serves cache and keeps it.)

#### 2. Screen banner
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/survey/survey_list/SurveyListScreen.kt`
**Changes**: in the `else ->` branch (`SurveyListScreen.kt:108`), wrap the `LazyColumn` in a `Column` and render the banner first, following `HomeScreen.kt:190-195`'s `OfflineClean` card and `HomeScreen.kt:204-215`'s trailing-button variant:

```kotlin
else -> Column(modifier = Modifier.fillMaxSize()) {
    if (state.fromCache) {
        AlInfoCard(
            title = "Sin conexión",
            description = "Mostrando encuestas guardadas",
            leadingIcon = Res.drawable.ic_mingcute_wifi_off_line, // reuse an existing offline icon if one exists; otherwise ic_mingcute_time, matching PendingOffline
            leadingIconTint = AlTheme.colors.textMuted,
            trailing = {
                AlButton(
                    onClick = { onAction(SurveyListAction.RetryClick) },
                    variant = AlButtonVariant.Outline,
                ) { Text("Reintentar") }
            },
        )
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) { /* unchanged items + isLoadingMore blocks */ }
}
```
Check `composeApp/src/commonMain/composeResources/drawable/` for an existing offline/wifi icon before reaching for the `add-mingcute-icon` skill; `ic_mingcute_time` (used by `PendingOffline`) is an acceptable existing fallback — do NOT add a new icon unless none fits. Match `AlInfoCard`'s exact parameter list from `HomeScreen.kt` at implementation time. Add appropriate spacing below the card (`AlTheme.spacing` token consistent with the screen).

#### 3. ViewModel unit test
**File**: `composeApp/src/commonTest/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/survey/survey_list/SurveyListViewModelTest.kt` (new, or extend if one exists)
**Changes**: fake `GetAvailableSurveysUseCase`'s repository dependency; cover:
- Cache-served result → `fromCache = true`, `loadError = false`, surveys populated.
- Subsequent live success (retry) → `fromCache = false`.

### Success Criteria:

#### Automated Verification:
- [x] Kotlin compilation passes: `./scripts/compile.sh`
- [x] Unit tests pass: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:testDebugUnitTest --no-daemon` (survey + this screen's tests green; 3 pre-existing unrelated failures on `main`)
- [x] Android build passes: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:assembleDebug --no-daemon`

#### Manual Verification:
- [ ] Offline list shows the banner; tapping "Reintentar" while still offline re-serves the cache without an error flash; after reconnecting, "Reintentar" swaps to the live list and the banner disappears.
- [ ] Online list shows no banner and paginates as before.
- [ ] Banner renders correctly in both themes.

---

## Testing Strategy

### Manual Testing Steps (end-to-end, on Android emulator or device):
1. Fresh sign-in online → wait a few seconds (warm) → airplane mode ON.
2. Home → "Encuesta" quick action → list loads from cache with offline banner.
3. Open a survey you have **never** opened before → questions render (detail prefetch worked).
4. Answer and submit → success toast, response queued (Home shows `PendingOffline` count).
5. Airplane mode OFF → sync drains the outbox; survey list "Reintentar" restores live list, banner gone.
6. Regression: online list pagination (>20 surveys if the environment has them), survey detail open, submit online.

## Performance Considerations

- The warm's detail prefetch is sequential, best-effort, inside a fire-and-forget `LaunchedEffect` — no UI impact. Payloads are small (question sets); at the 250-survey cap worst case it's bounded and only fetches *missing* details (subsequent warms are ~free).
- `replaceAllSummaries` is one transaction; snapshot sizes are tiny (tens of rows).

## Migration Notes

- No DB schema change; no `.sqm` migration. New `.sq` named queries only.
- `thoughts/plans/clear_local_db_on_sign_out.md` already wipes `CachedSurvey` on sign-out — no lifecycle work needed here.
- Dropping `Survey.createdAt` is source-compatible everywhere (verified unused outside the DTO mapper).

## References

- Original research: `thoughts/research/2026-07-13_16-29-36_survey-available-offline-gap.md`
- Original survey offline plan (records the live-only deviation this plan reverses): `thoughts/plans/survey_responses_offline_first.md:1149-1151`
- Fallback pattern: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/subject/SubjectQueryRepositoryImpl.kt:23-47`
- Warm pattern: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/usecase/activity/WarmActivityCacheUseCase.kt` + `app/AuthedShell.kt:124-148`
- Staleness precedent: `thoughts/grilling/adr-002-establishment-read-through-cache.md`
