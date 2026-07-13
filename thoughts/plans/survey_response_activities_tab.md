# Survey Responses as a Third "Activities" Tab — Implementation Plan

## Overview

Fold survey-response history into `HistoryHubScreen` (the app's "activities" screen) as a
third tab, making it the entry point for viewing/editing an individual survey response — the
standalone "Mis respuestas" screen and its Home-button entry point are retired. Along the way,
close the two real offline-sync gaps found in research
(`thoughts/research/2026-07-13_14-34-55_survey-response-activities-sync.md`): survey-response
history has no persistent offline cache beyond page 1, and a dead-lettered (`FAILED`) response
*edit* is invisible to the user with no recovery path.

## Current State Analysis

- **`HistoryHubScreen`** (`presentation/feature/history/HistoryHubScreen.kt`) hosts two tabs
  (`HistorySegment.Subjects`, `.Measurements`) via `AlSegmentedControl`. Each tab is a scaffold-free
  `*ContentRoot`/`*Content` pair with its own `koinViewModel()` — no `*Destination`/`*Navigation`
  files, since neither tab navigates anywhere.
- **`AlSegmentedControl`** (`core/design_system/components/segmented/AlSegmentedControl.kt`) takes
  `segments: List<String>` and iterates with `.weight(1f)` — confirmed fully generic, zero changes
  needed for a third segment.
- Both existing tabs are backed by a **reactive floor**: `observeLocalMerged()` combines the local
  pending queue (`Flow`, via SQLDelight `Query.Listener`) with a persistent `CachedActivityItem`
  cache (`ActivityLocalStore`, keyed by `ActivityItemType { SUBJECT, MEASUREMENT }`), so the screen
  is always populated offline and updates live as sync state changes — no manual refresh anywhere.
  `SubjectHistoryRepositoryImpl`/`MeasurementHistoryRepositoryImpl` write into that same cache table
  on every successful `loadPage()` call (any page number), and fall back to it on failure.
- **Survey-response history** is currently a *separate*, structurally-parallel stack:
  - `SurveyResponseHistoryRepositoryImpl` (`data/survey/`) is **not reactive** — `getPage()` is a
    one-shot suspend call. It has **no persistent cache**: page 1 falls back to the local pending
    queue on failure; page > 1 has no fallback at all and just fails.
  - `presentation/feature/survey/survey_response_history/` is a full 6-file routed screen
    (`Action`/`Destination`/`Navigation`/`Screen`/`State`/`ViewModel`) with its own `AlScaffold` +
    `AlBackTopAppBar(title = "Mis respuestas")`, reached only via `HomeAction.MySurveysClick` →
    `Route.SurveyResponseHistory` (outer nav).
  - `SurveyResponseEditRepository.retry(editId)` exists, is wired into `SyncEngine`/DI, but **is
    never called from any use case or UI** — a `FAILED` edit is invisible and silently
    garbage-collected by the hourly `pruneTerminal(7.days)` sweep. `SurveyResponseDetailScreen`
    (currently modified in the working tree with a complete, unrelated edit-success-banner
    feature — see Key Discoveries) has no edit-sync-state field at all.
- **`LocalOuterNavController`** (`app/LocalOuterNavController.kt`) is a `staticCompositionLocalOf`
  provided once at `AppNavHost` root and already read directly from composables that live on the
  *inner* tab nav (e.g. `ProfileScreen.kt:57`) to reach the outer nav controller — confirmed
  reusable here without threading a callback through `HistoryHubScreen`.

### Key Discoveries

- `data/activity/local/ActivityLocalStore.kt` / `CachedActivityItem.sq`: the cache table is
  **generic** — `id`/`type`/`occurredAtMillis`/`payloadJson`, `type` just a string discriminator.
  Adding `ActivityItemType.SURVEY_RESPONSE` requires **zero schema migration**.
- `SurveyResponseLocalStore` (the create/pending-response queue) **already has** `observeAll(): Flow<List<SurveyResponseUpload>>` (`data/survey/local/SurveyResponseLocalStore.kt:?`) — the reactive floor's local half is a drop-in, no new plumbing needed there.
- `SyncState` (`domain/model/subject/SyncState.kt`) is already `@Serializable`, with a doc comment
  anticipating exactly this kind of reuse. `SurveyResponseSummary`
  (`domain/model/survey/SurveyResponseHistory.kt`) is **not yet** `@Serializable` — needed so it
  round-trips through `payloadJson` like `SubjectHistoryEntry`/`MeasurementHistoryEntry` do.
- `PendingSurveyResponseEdit.sq` already stores everything needed to surface per-edit sync state
  (`syncState`, `errorMessage`, `attemptCount`) — there's just no query/Flow to read "the latest
  edit for this response" and no domain method exposing it.
- `AlInlineBanner` (`core/design_system/components/banner/AlInlineBanner.kt`, also modified in the
  working tree) auto-dismisses 1s after render whenever `onDismiss` is passed — **not** suitable
  for a persistent "your edit failed, retry" notice. The existing `loadError` pattern in
  `SurveyResponseDetailScreen.kt` (plain `Text` + `AlButton`) is the right model to reuse instead.
- The uncommitted changes in `survey_response_detail/` (successMessage banner + refresh-on-nav-result
  after edit) are a complete, unrelated feature — nothing there blocks this plan.

## Desired End State

- `HistoryHubScreen` has three tabs: **Sujetos / Mediciones / Encuestas**.
- Tapping a row in "Encuestas" pushes straight to `Route.SurveyResponseDetail` on the outer nav —
  the same detail screen already reached from the old standalone list.
- Home's "Mis respuestas" button, `HomeAction.MySurveysClick`, `HomeDestination.NavigateToSurveyResponseHistory`, `Route.SurveyResponseHistory`, and the standalone screen/nav/destination files are all removed. "Responder encuesta" is unchanged.
- `SurveyResponseHistoryRepositoryImpl` reactively merges the local queue with a persistent
  `ActivityItemType.SURVEY_RESPONSE` cache, exactly mirroring Subject/Measurement — offline
  pagination beyond page 1 now degrades gracefully instead of failing.
- The survey-response detail screen shows a "Reintentar edición" affordance when the response's
  latest edit attempt is dead-lettered (`FAILED`), wired to the previously-unwired
  `SurveyResponseEditRepository.retry()`.

### Verification

- `./scripts/compile.sh` passes after every phase.
- Manual: open "Encuestas" tab online (shows synced items), airplane-mode + create a response
  (appears instantly as "Pendiente", flips to "Sincronizado" on reconnect with zero manual
  refresh), tap a row → lands on `SurveyResponseDetail`, edit a response then force a sync failure
  (e.g. airplane mode mid-edit, or 5 failed attempts) → "Reintentar edición" appears on the detail
  screen and clears the failure once retried and back online.

## What We're NOT Doing

- **Not** touching Home's own small "Actividad reciente" feed (`ActivityItemType`'s consumers in
  `HomeViewModel`/`HomeStateMapper`/`ActivityMapper`) to include survey responses. The user's ask
  is about "the activities screen" (`HistoryHubScreen`), reached via bottom-nav/"See all" — a
  distinct, larger surface from Home's inline recent-activity list. Extending the *latter* would
  require threading a third type through `ActivityItemResponse`/`ActivityMapper`/`HomeViewModel`'s
  constructor (the "5+ layers" the research/memory both flag as high-risk) for a feed that was
  deliberately scoped to exclude survey responses. If this assumption is wrong, it's a small,
  separate follow-up — not blocking this plan.
- **Not** changing Subject/Measurement tab behavior, their repositories, or the shared
  `SyncCoordinator`/`SyncEngine` machinery beyond the one new engine already wired (this plan adds
  no new `SyncEngine` — `SurveyResponseEditRepository` is already registered).
- **Not** adding a "Delete" action to the survey-response tab — the old standalone screen never had
  one (only Retry), and it's not part of the reported gaps.
- **Not** restructuring `SurveyResponseHistoryRepository`'s `getPage()` into a
  `SubjectHistoryPage`-style paged wrapper with a server-provided `totalPages` — the existing
  `hasMore = items.size >= PAGE_SIZE` heuristic is a pre-existing, justified divergence (the
  `/me/activity?type=survey_response` endpoint has no total-count signal) and isn't one of the
  reported gaps.
- **Not** gating the new "Encuestas" tab by role (`canRespondToSurveys`) — `HistoryHubScreen`
  doesn't role-gate its existing two tabs, so the third follows the same default.
- **Not** further investigating the prior session's success-banner visual-parity question — that's
  a manual QA item unrelated to this plan's scope, called out again in Testing Strategy.

## Implementation Approach

Seven phases, each independently compilable, in dependency order: (1) persistent-cache parity for
survey-response history, (2) domain-layer edit-retry visibility, (3) wire edit-retry into the
detail screen, (4) extract a scaffold-free content pair for the history list (reusing phase 1's
reactive floor), (5) fold that content pair into `HistoryHubScreen` as a third tab, (6) retire the
standalone entry point now that it's redundant, (7) verification.

---

## Phase 1: Persistent offline cache for survey-response history

### Overview

Give `SurveyResponseHistoryRepositoryImpl` the exact same reactive-floor + cache-backed-fallback
shape as `SubjectHistoryRepositoryImpl`/`MeasurementHistoryRepositoryImpl`, reusing the existing
generic `CachedActivityItem` table.

### Changes Required

#### 1. `data/activity/local/ActivityLocalStore.kt`
Add the third enum member — no schema change, the table's `type` column is a plain string:
```kotlin
enum class ActivityItemType { SUBJECT, MEASUREMENT, SURVEY_RESPONSE }
```

#### 2. `domain/model/survey/SurveyResponseHistory.kt`
Make `SurveyResponseSummary` round-trip through `payloadJson`:
```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class SurveyResponseSummary(
    val id: String,
    val surveyName: String,
    val establishmentName: String,
    val eventAt: String,
    val syncState: SyncState,
    val isLocal: Boolean,
)
```

#### 3. `domain/repository/SurveyResponseHistoryRepository.kt`
Add the reactive floor to the interface:
```kotlin
import kotlinx.coroutines.flow.Flow

interface SurveyResponseHistoryRepository {
    fun observeLocalMerged(): Flow<List<SurveyResponseSummary>>
    suspend fun getPage(page: Int, pageSize: Int): Result<List<SurveyResponseSummary>>
    suspend fun getDetail(responseId: String): Result<SurveyResponseDetail>
}
```

#### 4. `data/survey/SurveyResponseHistoryRepositoryImpl.kt`
Add an `ActivityLocalStore` dependency, implement `observeLocalMerged()`, and rewrite `getPage()`
to write through the cache and fall back to it on failure for **every** page, not just page 1:
```kotlin
class SurveyResponseHistoryRepositoryImpl(
    private val api: SurveyResponseApi,
    private val activityApi: SurveyResponseActivityApi,
    private val local: SurveyResponseLocalStore,
    private val cache: ActivityLocalStore,
    private val surveys: SurveyRepository,
    private val json: Json,
) : SurveyResponseHistoryRepository {

    override fun observeLocalMerged(): Flow<List<SurveyResponseSummary>> =
        combine(local.observeAll(), cache.observe(ActivityItemType.SURVEY_RESPONSE)) { queued, cached ->
            merge(queued.map { it.toSummary() }, cached.map { it.toSummary() })
        }

    override suspend fun getPage(page: Int, pageSize: Int): Result<List<SurveyResponseSummary>> {
        val remote = runCatching { activityApi.list(page, pageSize) }
        return remote.fold(
            onSuccess = { response ->
                val remoteSummaries = response.items.mapNotNull { it.surveyResponse?.toSummary() }
                cache.upsertPage(ActivityItemType.SURVEY_RESPONSE, remoteSummaries.map { it.toCachedRow() })
                if (page == 1) {
                    Result.success(merge(local.all().map { it.toSummary() }, remoteSummaries))
                } else {
                    Result.success(remoteSummaries)
                }
            },
            onFailure = { error ->
                val fallback = if (page == 1) {
                    merge(local.all().map { it.toSummary() }, cachedPage(1, pageSize))
                } else {
                    cachedPage(page, pageSize)
                }
                if (fallback.isNotEmpty()) Result.success(fallback) else Result.failure(error)
            },
        )
    }

    // getDetail / fallbackToLocal / decodeDetail: unchanged

    /** Local wins on id collision (ADR-009), sorted by event time — same shape as
     *  SubjectHistoryRepositoryImpl.merge(), just keyed on eventAt instead of updatedAt since
     *  SurveyResponseSummary has no updatedAt field. */
    private fun merge(local: List<SurveyResponseSummary>, remote: List<SurveyResponseSummary>): List<SurveyResponseSummary> {
        val localIds = local.mapTo(mutableSetOf()) { it.id }
        return (local + remote.filterNot { it.id in localIds }).sortedByDescending { it.eventAtMillis() }
    }

    private suspend fun cachedPage(page: Int, pageSize: Int): List<SurveyResponseSummary> =
        cache.getPage(ActivityItemType.SURVEY_RESPONSE, limit = pageSize, offset = (page - 1) * pageSize)
            .map { it.toSummary() }

    private fun SurveyResponseSummary.toCachedRow(): CachedActivityRow =
        CachedActivityRow(id = id, occurredAtMillis = eventAtMillis(), payloadJson = json.encodeToString(this))

    private fun CachedActivityRow.toSummary(): SurveyResponseSummary = json.decodeFromString(payloadJson)

    private fun SurveyResponseSummary.eventAtMillis(): Long =
        runCatching { Instant.parse(eventAt).toEpochMilliseconds() }.getOrDefault(0L)
}
```
Note: `SurveyResponseUpload.toSummary()`/`SurveyResponseActivitySummaryResponse.toSummary()`
mappers in `data/survey/mapper/SurveyResponseHistoryMapper.kt` are unchanged — both already produce
`SurveyResponseSummary`.

#### 5. `di/RepositoriesModule.kt`
Wire the new `cache` dependency (the `ActivityLocalStore` singleton — same instance already shared
by Subject/Measurement):
```kotlin
single<SurveyResponseHistoryRepository> {
    SurveyResponseHistoryRepositoryImpl(
        api = get(), activityApi = get(), local = get(), cache = get(), surveys = get(),
        json = get(named("auth")),
    )
}
```

#### 6. `domain/usecase/activity/WarmActivityCacheUseCase.kt`
Extend Home's best-effort warm-up so page 1 is ready before the user opens the Encuestas tab:
```kotlin
class WarmActivityCacheUseCase(
    private val subjects: SubjectHistoryRepository,
    private val measurements: MeasurementHistoryRepository,
    private val surveyResponses: SurveyResponseHistoryRepository,
) {
    suspend operator fun invoke() {
        subjects.loadPage(page = 1, pageSize = PAGE_SIZE)
        measurements.loadPage(page = 1, pageSize = PAGE_SIZE)
        surveyResponses.getPage(page = 1, pageSize = PAGE_SIZE)
    }
    private companion object { const val PAGE_SIZE = 20 }
}
```
Update its DI registration (wherever it's constructed — grep for `WarmActivityCacheUseCase(`) to
pass `surveyResponses = get()`.

#### 7. `domain/usecase/survey/` — new use case
Add the wrapper the ViewModel (phase 4) will consume, matching `ObserveSubjectUploadsUseCase`'s shape:
```kotlin
package ar.com.ingenieriaalimentacion.domain.usecase.survey

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary
import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseHistoryRepository
import kotlinx.coroutines.flow.Flow

class ObserveSurveyResponseUploadsUseCase(private val repository: SurveyResponseHistoryRepository) {
    operator fun invoke(): Flow<List<SurveyResponseSummary>> = repository.observeLocalMerged()
}
```
Register in `di/UseCasesModule.kt`: `factory { ObserveSurveyResponseUploadsUseCase(get()) }`.

### Success Criteria

#### Automated Verification
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./scripts/compile.sh` passes

#### Manual Verification
- [ ] With network on, opening the (still-standalone, pre-phase-5) survey response history screen
      populates as before
- [ ] Airplane mode + reopen the screen still shows previously-cached items on page 1 and beyond
      (previously page > 1 returned nothing offline)

---

## Phase 2: Domain-layer visibility for a dead-lettered edit

### Overview

Expose the latest edit attempt's sync state per response, reactively, so a `FAILED` edit is no
longer invisible.

### Changes Required

#### 1. `composeApp/src/commonMain/sqldelight/.../PendingSurveyResponseEdit.sq`
Add a query for the most recent edit row for a given response (any state, not just `PENDING`):
```sql
selectLatestForResponse:
SELECT * FROM PendingSurveyResponseEdit
WHERE responseId = :responseId
ORDER BY createdAt DESC
LIMIT 1;
```

#### 2. `data/survey/local/SurveyResponseEditLocalStore.kt`
```kotlin
import kotlinx.coroutines.flow.Flow

fun observeLatestForResponse(responseId: String): Flow<SurveyResponseEditUpload?>
```

#### 3. `data/survey/local/DefaultSurveyResponseEditLocalStore.kt`
Implement using the same `asFlow()`/dispatcher/mapping conventions already used elsewhere in this
file for its other queries:
```kotlin
override fun observeLatestForResponse(responseId: String): Flow<SurveyResponseEditUpload?> =
    queries.selectLatestForResponse(responseId).asFlow().mapToOneOrNull(dispatcher).map { it?.toDomain() }
```

#### 4. `domain/model/survey/` — new small domain model
```kotlin
package ar.com.ingenieriaalimentacion.domain.model.survey

import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState

/** The most recent edit attempt for a response — lets the detail screen surface a dead-lettered
 *  (FAILED) edit instead of it silently vanishing after the 7-day prune window. */
data class SurveyResponseEditStatus(
    val editId: String,
    val syncState: SyncState,
    val errorMessage: String?,
)
```

#### 5. `domain/repository/SurveyResponseEditRepository.kt`
```kotlin
import kotlinx.coroutines.flow.Flow

fun observeEditStatus(responseId: String): Flow<SurveyResponseEditStatus?>
```

#### 6. `data/survey/SurveyResponseEditRepositoryImpl.kt`
```kotlin
override fun observeEditStatus(responseId: String): Flow<SurveyResponseEditStatus?> =
    local.observeLatestForResponse(responseId).map { edit ->
        edit?.let { SurveyResponseEditStatus(it.editId, it.syncState, it.errorMessage) }
    }
```

#### 7. `domain/usecase/survey/` — two new use cases, mirroring the existing `RetrySubjectUploadUseCase`/`RetrySurveyResponseUploadUseCase` one-liner shape
```kotlin
class ObserveSurveyResponseEditStatusUseCase(private val repository: SurveyResponseEditRepository) {
    operator fun invoke(responseId: String): Flow<SurveyResponseEditStatus?> = repository.observeEditStatus(responseId)
}

class RetrySurveyResponseEditUploadUseCase(private val repository: SurveyResponseEditRepository) {
    suspend operator fun invoke(editId: String): Result<Unit> = repository.retry(editId)
}
```
Register both in `di/UseCasesModule.kt`.

### Success Criteria

#### Automated Verification
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./scripts/compile.sh` passes (SQLDelight
      codegen for the new query included)

#### Manual Verification
- [ ] None yet — this phase has no UI surface; verified end-to-end in Phase 3

---

## Phase 3: Wire edit-retry into `SurveyResponseDetailScreen`

### Overview

Surface a "Reintentar edición" affordance when the response's latest edit is `FAILED`.

### Changes Required

#### 1. `presentation/feature/survey/survey_response_detail/SurveyResponseDetailState.kt`
```kotlin
data class SurveyResponseDetailState(
    // ...existing fields unchanged...
    val editId: String? = null,
    val editSyncState: SyncState? = null,
    val editErrorMessage: String? = null,
)
```

#### 2. `.../SurveyResponseDetailAction.kt`
```kotlin
sealed interface SurveyResponseDetailAction {
    // ...existing...
    data object RetryEditClick : SurveyResponseDetailAction
}
```

#### 3. `.../SurveyResponseDetailViewModel.kt`
Inject the two new use cases + `SyncCoordinator`, observe status reactively (independent of the
one-shot `load()`), and handle the retry action:
```kotlin
class SurveyResponseDetailViewModel(
    route: Route.SurveyResponseDetail,
    private val getSurveyResponseDetail: GetSurveyResponseDetailUseCase,
    private val getSurveyDetail: GetSurveyDetailUseCase,
    private val getMyProfile: GetMyProfileUseCase,
    private val getMyEstablishments: GetMyEstablishmentsUseCase,
    observeEditStatus: ObserveSurveyResponseEditStatusUseCase,
    private val retryEditUpload: RetrySurveyResponseEditUploadUseCase,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    // ...existing _state/state...

    init {
        load()
        viewModelScope.launch {
            observeEditStatus(route.responseId).collect { status ->
                _state.update {
                    it.copy(
                        editId = status?.editId,
                        editSyncState = status?.syncState,
                        editErrorMessage = status?.errorMessage,
                    )
                }
            }
        }
    }

    fun onAction(action: SurveyResponseDetailAction) {
        when (action) {
            // ...existing branches...
            SurveyResponseDetailAction.RetryEditClick -> {
                val editId = _state.value.editId ?: return
                viewModelScope.launch { retryEditUpload(editId); syncCoordinator.requestSync() }
            }
        }
    }

    // ...rest unchanged...
}
```

#### 4. `.../SurveyResponseDetailScreen.kt`
Render the retry affordance only for `SyncState.Failed`, using the same plain-`Text` + `AlButton`
shape already used by this file's `loadError` branch (NOT `AlInlineBanner`, which auto-dismisses):
```kotlin
if (state.editSyncState == SyncState.Failed) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = state.editErrorMessage ?: "No se pudo guardar tu última edición.",
            style = AlTheme.typography.body,
            color = AlTheme.colors.danger,
        )
        AlButton(onClick = { onAction(SurveyResponseDetailAction.RetryEditClick) }) {
            Text("Reintentar edición")
        }
    }
}
```
Place this block right after the `successMessage` banner slot (top of the scrollable content), so
it's immediately visible alongside the existing banner without competing for the same spot.

### Success Criteria

#### Automated Verification
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./scripts/compile.sh` passes

#### Manual Verification
- [ ] Edit a response, force 5 consecutive sync failures (e.g. toggle airplane mode so each retry
      attempt fails until `MAX_SYNC_ATTEMPTS` dead-letters it) → "Reintentar edición" appears with
      the edit's `errorMessage`
- [ ] Tap "Reintentar edición" while online → the row disappears once the edit syncs (reactive,
      no manual reload)

---

## Phase 4: Extract a scaffold-free content pair for the survey-response list

### Overview

Turn the standalone routed screen into an embeddable `Content`/`ContentRoot` pair matching the
Subject/Measurement tabs' shape, switched onto the phase-1 reactive floor. This also drops the
now-redundant `loadError`/`destination` concepts in favor of the same "reactive floor never fails
silently" pattern Subject/Measurement already use — a single-value nav signal replaces the
sealed `Destination`, since this tab has exactly one link target (survey response detail) and
doesn't warrant its own `*Destination`/`*Navigation` file pair.

### Changes Required

#### 1. `presentation/feature/survey/survey_response_history/SurveyResponseHistoryState.kt` — rewrite
```kotlin
data class SurveyResponseHistoryState(
    val navigateToResponseId: String? = null,
    val items: List<SurveyResponseSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
) {
    val canLoadMore: Boolean get() = hasMore && !isLoading && !isLoadingMore
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}
```

#### 2. `.../SurveyResponseHistoryAction.kt` — drop `BackClick`
```kotlin
sealed interface SurveyResponseHistoryAction {
    data class ResponseClick(val responseId: String) : SurveyResponseHistoryAction
    data class RetryUploadClick(val responseId: String) : SurveyResponseHistoryAction
    data object LoadMore : SurveyResponseHistoryAction
    data object RetryLoad : SurveyResponseHistoryAction
}
```

#### 3. `.../SurveyResponseHistoryViewModel.kt` — rewrite onto the reactive floor
```kotlin
class SurveyResponseHistoryViewModel(
    private val observeUploads: ObserveSurveyResponseUploadsUseCase,
    private val getHistoryPage: GetSurveyResponseHistoryPageUseCase,
    private val retrySurveyResponseUpload: RetrySurveyResponseUploadUseCase,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(SurveyResponseHistoryState())
    val state = _state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), _state.value)

    init {
        viewModelScope.launch {
            observeUploads().collect { items -> _state.update { it.copy(items = items, isLoading = false) } }
        }
        viewModelScope.launch { refreshPage1() }
        syncCoordinator.requestSync()
    }

    fun onAction(action: SurveyResponseHistoryAction) {
        when (action) {
            is SurveyResponseHistoryAction.ResponseClick ->
                _state.update { it.copy(navigateToResponseId = action.responseId) }
            is SurveyResponseHistoryAction.RetryUploadClick -> viewModelScope.launch {
                retrySurveyResponseUpload(action.responseId)
                syncCoordinator.requestSync()
            }
            SurveyResponseHistoryAction.LoadMore -> loadMore()
            SurveyResponseHistoryAction.RetryLoad -> viewModelScope.launch { refreshPage1() }
        }
    }

    fun clearNavigation() {
        _state.update { it.copy(navigateToResponseId = null) }
    }

    private suspend fun refreshPage1() {
        getHistoryPage(page = 1, pageSize = PAGE_SIZE).onSuccess { items ->
            _state.update { it.copy(page = 1, hasMore = items.size >= PAGE_SIZE) }
        }
        // Failure is silent: reactive observeUploads() keeps the screen populated from the local
        // floor, same contract as Subject/MeasurementUploadsViewModel.
    }

    private fun loadMore() {
        val current = _state.value
        if (!current.canLoadMore) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            getHistoryPage(page = current.page + 1, pageSize = PAGE_SIZE).fold(
                onSuccess = { items ->
                    _state.update { s ->
                        val seen = s.items.mapTo(mutableSetOf()) { it.id }
                        s.copy(
                            items = s.items + items.filterNot { it.id in seen },
                            page = current.page + 1,
                            hasMore = items.size >= PAGE_SIZE,
                            isLoadingMore = false,
                        )
                    }
                },
                onFailure = { _state.update { it.copy(isLoadingMore = false) } },
            )
        }
    }

    private companion object { const val PAGE_SIZE = 20 }
}
```

#### 4. New file `.../SurveyResponseHistoryContent.kt`
Lift the body of the old `SurveyResponseHistoryScreen` composable (infinite-scroll `LazyColumn`,
loading/empty states, `SurveyResponseHistoryRow` with its `SyncState → (label, AlPillTone)` mapping
and inline "Reintentar" button) verbatim, minus `AlScaffold`/`AlBackTopAppBar`/`BackClick` — same
relationship `MeasurementUploadsContent.kt` has to `MeasurementUploadsScreen`'s old body. Use the
same `isEmpty`/`EmptyState` pattern `SubjectUploadsContent` uses (check that file for the exact
`EmptyState` composable to reuse/mirror) instead of the old plain `Text` empty message, for visual
consistency with the other two tabs.

#### 5. New file `.../SurveyResponseHistoryContentRoot.kt`
```kotlin
@Composable
fun SurveyResponseHistoryContentRoot(
    modifier: Modifier = Modifier,
    viewModel: SurveyResponseHistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val outerNav = LocalOuterNavController.current

    LaunchedEffect(state.navigateToResponseId) {
        state.navigateToResponseId?.let { id ->
            outerNav.navigate(Route.SurveyResponseDetail(id))
            viewModel.clearNavigation()
        }
    }

    SurveyResponseHistoryContent(state = state, onAction = viewModel::onAction, modifier = modifier)
}
```

#### 6. Delete
- `.../SurveyResponseHistoryScreen.kt` (superseded by `Content.kt` + `ContentRoot.kt`)
- `.../SurveyResponseHistoryNavigation.kt`
- `.../SurveyResponseHistoryDestination.kt`

### Success Criteria

#### Automated Verification
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./scripts/compile.sh` passes (note: this
      phase temporarily leaves nothing calling the new `ContentRoot` — Phase 5 wires it in; if that
      causes an "unused" lint/compile concern, do Phases 4 and 5 as one commit)

#### Manual Verification
- [ ] Deferred to Phase 5 (no reachable UI until the tab is wired in)

---

## Phase 5: Fold in as the third `HistoryHubScreen` tab

### Changes Required

#### 1. `presentation/feature/history/HistorySegment.kt`
```kotlin
enum class HistorySegment(val label: String) {
    Subjects("Sujetos"),
    Measurements("Mediciones"),
    SurveyResponses("Encuestas"),
}
```

#### 2. `presentation/feature/history/HistoryHubScreen.kt`
```kotlin
when (segment) {
    HistorySegment.Subjects -> SubjectUploadsContentRoot(Modifier.fillMaxSize())
    HistorySegment.Measurements -> MeasurementUploadsContentRoot(Modifier.fillMaxSize())
    HistorySegment.SurveyResponses -> SurveyResponseHistoryContentRoot(Modifier.fillMaxSize())
}
```
(plus the new import)

### Success Criteria

#### Automated Verification
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./scripts/compile.sh` passes

#### Manual Verification
- [ ] "Historial" screen shows three segments; switching to "Encuestas" loads the list
- [ ] Tapping a row navigates to `SurveyResponseDetail` and back-navigation returns to the tab

---

## Phase 6: Retire the standalone "Mis respuestas" entry point

### Overview

Now that the tab is the entry point, remove the old one everywhere it's wired, and delete the
routed screen it used.

### Changes Required

#### 1. `presentation/feature/home/HomeAction.kt` — remove `MySurveysClick`
#### 2. `presentation/feature/home/HomeDestination.kt` — remove `NavigateToSurveyResponseHistory`
#### 3. `presentation/feature/home/HomeViewModel.kt` — remove the `MySurveysClick ->` branch in `onAction`
#### 4. `presentation/feature/home/HomeScreen.kt` — remove the "Mis respuestas" `AlButton` from
   `SurveyActions`; keep "Responder encuesta" (simplify the wrapping `Column` if it no longer needs
   to stack two buttons)
#### 5. `presentation/feature/home/HomeNavigation.kt` — this file is already dead code (not
   live-dispatched, per its own header comment) but kept in sync with `HomeDestination`; remove its
   reference to `NavigateToSurveyResponseHistory` too so it still compiles
#### 6. `app/AuthedShell.kt` — remove the
   `HomeDestination.NavigateToSurveyResponseHistory -> outerNav.navigateToSurveyResponseHistory()`
   mapping
#### 7. `presentation/navigation/Route.kt` — remove `data object SurveyResponseHistory : Route()`
#### 8. `app/AppNavHost.kt` — remove `surveyResponseHistoryScreen(navController)` and its import

Before deleting, grep the whole `composeApp/src/commonMain` tree for `Route.SurveyResponseHistory`,
`SurveyResponseHistoryScreenRoot`, and `navigateToSurveyResponseHistory` to make sure every
reference above is caught (the `SurveyResponseHistoryNavigation.kt`/`Destination.kt` files
themselves were already deleted in Phase 4).

### Success Criteria

#### Automated Verification
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./scripts/compile.sh` passes
- [x] `grep -rn "SurveyResponseHistory" composeApp/src/commonMain/kotlin | grep -v "presentation/feature/survey/survey_response_history/"` returns only the repository/use-case/DI references
      that are still legitimately in use (i.e. nothing left pointing at the deleted route/screen)

#### Manual Verification
- [ ] Home no longer shows a "Mis respuestas" button; "Responder encuesta" still works
- [ ] No dead navigation target / crash when tapping through Home → History → Encuestas → detail → back

---

## Phase 7: Verification

### Testing Strategy

#### Manual Testing Steps
1. Fresh app open, online: History → Encuestas shows existing responses with correct sync pills.
2. Airplane mode → Responder encuesta → submit: response appears in Encuestas instantly as
   "Pendiente"; re-enable network → flips to "Sincronizado" without leaving the screen.
3. Airplane mode, scroll Encuestas past page 1 (requires >20 previously-synced responses, or lower
   `PAGE_SIZE` temporarily to test) → previously-cached rows still render instead of the list going
   empty/erroring.
4. Tap a response row → lands on `SurveyResponseDetail`; back button returns to the Encuestas tab.
5. Edit a response, force repeated sync failure until dead-lettered → "Reintentar edición" appears
   on the detail screen with a Spanish error message; tap it online → clears once synced.
6. Confirm in the running app whether the create-flow success banner
   (`SurveyResponseFormScreen.kt:157`) visually matches other upload screens' banners — this was
   flagged as unconfirmed by static analysis in the research doc and is unrelated to this plan's
   changes, but worth a quick look while testing flow #2 above.

### Performance Considerations

None beyond what Subject/Measurement already do — same cache table, same 200-row eviction cap
(`ActivityLocalStore.DEFAULT_CAP`), same single-flight `SyncCoordinator`.

### Migration Notes

No data migration: `CachedActivityItem` is schema-unchanged (new `type` value only), and existing
`PendingSurveyResponse`/`PendingSurveyResponseEdit` rows are read as-is.

## References

- Research: `thoughts/research/2026-07-13_14-34-55_survey-response-activities-sync.md`
- Prior plan (survey responses greenfield): `thoughts/plans/survey_responses_offline_first.md`
- Prior plan (History hub origin): `thoughts/plans/activity_feed_history.md`
- ADR-009 (local-wins dedup): `thoughts/grilling/activity-feed-synthesis.md`
- Memory: `activities-screen-third-tab`, `survey-responses-design-decisions`
