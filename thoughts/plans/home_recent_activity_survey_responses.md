# Survey Responses in Home's "Actividad reciente" Feed — Implementation Plan

## Overview

Home's inline "Actividad reciente" list (`HomeState.recent`) currently only shows subjects and
measurements — a survey response upload never appears there, even though it's fully reactive and
offline-cached everywhere else in the app. Extend the existing subjects+measurements derivation
pipeline to include survey responses as a third source, reusing machinery that already exists from
`thoughts/plans/survey_response_activities_tab.md` (the `HistoryHubScreen` "Encuestas" tab plan).

## Current State Analysis

- `HomeViewModel` (`presentation/feature/home/HomeViewModel.kt:56-76`) drives `HomeState.recent` from
  a pure `combine(observeSubjectUploads(), observeMeasurements(), connectivity.isOnline) { ... }` →
  `deriveState(...)` (`HomeStateMapper.kt:25`). There is no live network call in this path — Home only
  ever *observes* local Flows.
- `HomeStateMapper.deriveState()` merges `subjects`/`measurements` into `RecentActivityUi` rows,
  sorts by `createdAt` descending, and caps at `RECENT_LIMIT = 4` (`HomeStateMapper.kt:54-56`).
- `RecentActivityUi.Kind` (`HomeState.kt:50`) is a closed `enum class Kind { Subject, Measurement }`
  — no third case exists yet.
- `HomeScreen.kt:406-409` pattern-matches `Kind` to pick a leading icon per row.

### Key Discoveries

- **The reactive floor for survey responses already exists**, built in
  `thoughts/plans/survey_response_activities_tab.md` (already implemented on this branch):
  - `ObserveSurveyResponseUploadsUseCase` (`domain/usecase/survey/ObserveSurveyResponseUploadsUseCase.kt`)
    returns `Flow<List<SurveyResponseSummary>>` — the exact same shape as
    `ObserveSubjectUploadsUseCase`/`ObserveMeasurementsUseCase`.
  - `ActivityItemType.SURVEY_RESPONSE` is already a member of the shared `CachedActivityItem` cache
    enum, and `WarmActivityCacheUseCase` (invoked once per foreground session from
    `AuthedShell.kt:139-142`) already warms survey-response page 1 into that cache alongside
    subjects/measurements.
  - This means **no DTO/mapper/cache work is needed** — the "5+ layers, hardcoded to two types"
    concern from prior research (`ActivityItemResponse`/`ActivityMapper`/`ActivityApi`) refers to the
    *live-fetch* `/me/activity` pipeline used by the History tabs' pagination, which Home never
    touches directly. Home's `recent` list is purely a local-Flow derivation, so this change is
    confined to the presentation layer: `HomeStateMapper`, `HomeState`, `HomeViewModel`, `HomeScreen`.
- `SurveyResponseSummary` (`domain/model/survey/SurveyResponseHistory.kt`) has `id`, `surveyName`,
  `establishmentName`, `eventAt` (ISO string, no separate `createdAt`/`updatedAt` millis field),
  `syncState`, `isLocal` — no subject name, since a survey response isn't tied to a subject.
- `kotlinx.coroutines.flow.combine` has a typed 4-flow overload
  (`combine(flow1, flow2, flow3, flow4) { a, b, c, d -> ... }`), so `HomeViewModel` doesn't need to
  switch to the array-based `combine(vararg)` form.
- `RecentActivityUi.Kind` values are matched by `when` (not by ordinal), so adding a new case is a
  compiler-checked exhaustive-`when` change everywhere it's consumed.
- `ic_mingcute_document_2_line` is still present as a drawable resource
  (`composeApp/src/commonMain/composeResources/drawable/ic_mingcute_document_2_line.xml`) even though
  its only Kotlin import (in `HomeScreen.kt`, for the old "Mis respuestas" button) was removed in the
  prior plan — safe to reuse for the new recent-activity icon, keeping it visually distinct from
  `ic_mingcute_doc_line` (still used by the "Responder encuesta" button).
- `HomeStateMapperTest.kt`'s existing calls to `deriveState(...)` always pass `subjects`/`measurements`
  positionally and `online`/`now`/`timeZone` by name — so a new `surveyResponses` parameter with a
  default value, inserted right after `measurements`, requires **zero changes** to existing test call
  sites.
- `FakeSurveyResponseHistoryRepository` (`commonTest/.../fake/FakeSurveyResponseHistoryRepository.kt`)
  already exposes a `localMerged: MutableStateFlow<List<SurveyResponseSummary>>` for
  `observeLocalMerged()` — ready to drive `ObserveSurveyResponseUploadsUseCase` in tests, mirroring
  `FakeSubjectHistoryRepository.merged`.

## Desired End State

- Home's "Actividad reciente" shows survey response rows (title = survey name, subtitle =
  establishment name, sync-state badge) interleaved with subject/measurement rows, sorted by recency,
  capped at the existing 4-row limit.
- Tapping a survey-response row (like any other row) triggers `HomeAction.SeeAllActivityClick` →
  navigates to History, which now has the "Encuestas" tab to see it in full.
- A `FAILED` survey response upload shows the same "Error" badge convention as subjects/measurements
  (no per-row retry action in Home — consistent with today's subject/measurement rows, which also
  aren't individually retryable from this list).

### Verification

- `./scripts/compile.sh` passes after each phase.
- Unit tests pass for `HomeStateMapperTest`/`HomeViewModelTest` (see per-phase criteria).
- Manual: submit a survey response (online or airplane-mode), return to Home without navigating away
  — the response's row appears in "Actividad reciente" with a "Pendiente"/"Sincronizado" badge,
  without any manual refresh.

## What We're NOT Doing

- **Not** adding a "Encuestas" stat card to the "HOY" strip (`TodayStrip`/`todaySubjects`/
  `todayMeasurements`/`todayAlerts`). The user's ask was specifically about the recent-activity list;
  the Today strip is a distinct, unasked-for surface, and adding a 4th stat card is a layout/design
  decision that deserves its own sign-off rather than being bundled in here.
- **Not** adding a per-row retry action for a `FAILED` survey response in Home's list — subjects and
  measurements don't have one here either (retry lives in the History tabs); this change preserves
  that parity rather than introducing an inconsistency.
- **Not** touching `ActivityItemResponse`/`ActivityMapper`/`ActivityApi`/`ActivityItemType` — none of
  that live-fetch/DTO machinery is on Home's code path (see Key Discoveries).
- **Not** re-deriving or duplicating the reactive floor — `ObserveSurveyResponseUploadsUseCase` and
  the cache warm-up already exist and are reused as-is.

## Implementation Approach

Two phases: (1) the pure derivation layer — `HomeState`/`HomeStateMapper` — fully unit-testable
without touching DI or the ViewModel; (2) wiring `HomeViewModel` to the new use case, updating DI and
the icon mapping, and extending `HomeViewModelTest`.

---

## Phase 1: Extend `HomeStateMapper`/`HomeState`

### Overview

Add a third `Kind` and a third input list to the pure `deriveState()` function, with no DI or
ViewModel changes.

### Changes Required

#### 1. `presentation/feature/home/HomeState.kt`
Add the new `Kind` case:
```kotlin
data class RecentActivityUi(
    val id: String,
    val kind: Kind,
    val title: String,
    val subtitle: String?,
    val value: String?,
    val badgeText: String?,
    val badgeTone: AlBadgeTone,
    val createdAt: Long,
) {
    enum class Kind { Subject, Measurement, SurveyResponse }
}
```

#### 2. `presentation/feature/home/HomeStateMapper.kt`
Add the import, the new parameter (defaulted so existing call sites don't need to change), the merge,
the row mapper, the badge mapper, and the ISO-timestamp helper:
```kotlin
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary

internal fun deriveState(
    subjects: List<SubjectHistoryEntry>,
    measurements: List<MeasurementHistoryEntry>,
    surveyResponses: List<SurveyResponseSummary> = emptyList(),
    online: Boolean,
    now: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): HomeState {
    // ...unchanged sync/today derivation (subjects/measurements only)...

    val recent = (
        subjects.map { it.toRecentUi() } +
            measurements.map { it.toRecentUi() } +
            surveyResponses.map { it.toRecentUi() }
        )
        .sortedByDescending { it.createdAt }
        .take(RECENT_LIMIT)

    return HomeState(/* ...unchanged... */)
}

private fun SurveyResponseSummary.toRecentUi(): RecentActivityUi {
    val (label, tone) = surveyResponseSyncBadge(syncState)
    return RecentActivityUi(
        id = id,
        kind = RecentActivityUi.Kind.SurveyResponse,
        title = surveyName,
        subtitle = establishmentName,
        value = null,
        badgeText = label,
        badgeTone = tone,
        createdAt = eventAtMillis(eventAt),
    )
}

private fun surveyResponseSyncBadge(state: SyncState): Pair<String, AlBadgeTone> = when (state) {
    SyncState.Pending -> "Pendiente" to AlBadgeTone.Neutral
    SyncState.Syncing -> "Sincronizando" to AlBadgeTone.Neutral
    SyncState.Synced -> "Sincronizado" to AlBadgeTone.Success
    SyncState.Merged -> "Sincronizado" to AlBadgeTone.Success // unreachable for survey responses
    SyncState.Failed -> "Error" to AlBadgeTone.Danger
}

/** ISO-8601 `eventAt` → epoch millis, matching SurveyResponseHistoryRepositoryImpl's own parsing;
 *  malformed input sorts to the very bottom instead of crashing derivation. */
private fun eventAtMillis(iso: String): Long =
    runCatching { Instant.parse(iso).toEpochMilliseconds() }.getOrDefault(0L)
```
`Instant` is already imported in this file (used by `startOfTodayMillis`/`formatLastSynced`).

#### 3. `composeApp/src/commonTest/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/home/HomeStateMapperTest.kt`
Add a `surveyResponse(...)` test factory (mirroring `subject()`/`measurement()`) and new test cases:
```kotlin
private fun surveyResponse(
    id: String = "sr1",
    syncState: SyncState = SyncState.Pending,
    eventAt: String = "2026-06-29T12:00:00Z",
    surveyName: String = "Encuesta nutricional",
    establishmentName: String = "Escuela 1",
) = SurveyResponseSummary(
    id = id,
    surveyName = surveyName,
    establishmentName = establishmentName,
    eventAt = eventAt,
    syncState = syncState,
    isLocal = true,
)
```
- `survey response recent row carries establishment subtitle and a sync badge` — mirrors the existing
  "subject recent row..." test: assert `kind == SurveyResponse`, `subtitle == establishmentName`,
  `value == null`, badge label/tone per `syncState`.
- `recent merges all three sources, sorts desc, caps at 4` — extend the existing
  "recent merges both sources..." test (or add a sibling) to include a `surveyResponses` list and
  assert the cap/sort still holds across three sources.

### Success Criteria

#### Automated Verification
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./scripts/compile.sh` passes
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:testDebugUnitTest --no-daemon --tests "ar.com.ingenieriaalimentacion.presentation.feature.home.HomeStateMapperTest"` passes

#### Manual Verification
- [ ] None yet — no UI wiring until Phase 2

---

## Phase 2: Wire `HomeViewModel`, DI, and the icon mapping

### Overview

Inject the existing `ObserveSurveyResponseUploadsUseCase` into `HomeViewModel`, thread it through the
`combine()`, register it in DI, and map the new `Kind` to an icon.

### Changes Required

#### 1. `presentation/feature/home/HomeViewModel.kt`
```kotlin
import ar.com.ingenieriaalimentacion.domain.usecase.survey.ObserveSurveyResponseUploadsUseCase

class HomeViewModel(
    observeSubjectUploads: ObserveSubjectUploadsUseCase,
    observeMeasurements: ObserveMeasurementsUseCase,
    observeSurveyResponseUploads: ObserveSurveyResponseUploadsUseCase,
    private val getMyProfile: GetMyProfileUseCase,
    // ...rest unchanged...
) : ViewModel() {
    // ...
    init {
        // ...identity load unchanged...
        viewModelScope.launch {
            combine(
                observeSubjectUploads(),
                observeMeasurements(),
                observeSurveyResponseUploads(),
                connectivity.isOnline,
            ) { subjects, measurements, surveyResponses, online ->
                deriveState(subjects, measurements, surveyResponses, online, now())
            }.collect { derived ->
                _state.update {
                    derived.copy(/* ...unchanged identity-field preservation... */)
                }
            }
        }
        loadEstablishments()
    }
    // ...rest unchanged...
}
```

#### 2. `presentation/feature/home/HomeScreen.kt`
```kotlin
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_document_2_line

private fun RecentActivityUi.Kind.icon(): DrawableResource = when (this) {
    RecentActivityUi.Kind.Subject -> Res.drawable.ic_mingcute_user_3_line
    RecentActivityUi.Kind.Measurement -> Res.drawable.ic_mingcute_ruler_line
    RecentActivityUi.Kind.SurveyResponse -> Res.drawable.ic_mingcute_document_2_line
}
```

#### 3. `di/ViewModelsModule.kt`
```kotlin
viewModel {
    HomeViewModel(
        observeSubjectUploads = get(),
        observeMeasurements = get(),
        observeSurveyResponseUploads = get(),
        getMyProfile = get(),
        getMyEstablishments = get(),
        getCurrentEstablishment = get(),
        setCurrentEstablishment = get(),
        syncCoordinator = get(),
        connectivity = get(),
        now = { Clock.System.now().toEpochMilliseconds() },
    )
}
```

#### 4. `composeApp/src/commonTest/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/home/HomeViewModelTest.kt`
Add a defaulted fake to the `viewModel()` test helper and wire it in:
```kotlin
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary
import ar.com.ingenieriaalimentacion.domain.usecase.survey.ObserveSurveyResponseUploadsUseCase
import ar.com.ingenieriaalimentacion.fake.FakeSurveyResponseHistoryRepository

private fun viewModel(
    subjectRepo: FakeSubjectHistoryRepository = FakeSubjectHistoryRepository(),
    measurementRepo: FakeMeasurementHistoryRepository = FakeMeasurementHistoryRepository(),
    surveyResponseRepo: FakeSurveyResponseHistoryRepository = FakeSurveyResponseHistoryRepository(),
    profileRepo: FakeProfileRepository,
    connectivity: FakeConnectivityObserver = FakeConnectivityObserver(initial = true),
): HomeViewModel {
    val coordinator = SyncCoordinator(emptyList(), connectivity, AppScope())
    return HomeViewModel(
        observeSubjectUploads = ObserveSubjectUploadsUseCase(subjectRepo),
        observeMeasurements = ObserveMeasurementsUseCase(measurementRepo),
        observeSurveyResponseUploads = ObserveSurveyResponseUploadsUseCase(surveyResponseRepo),
        getMyProfile = GetMyProfileUseCase(profileRepo),
        getMyEstablishments = GetMyEstablishmentsUseCase(profileRepo),
        getCurrentEstablishment = GetCurrentEstablishmentUseCase(profileRepo),
        setCurrentEstablishment = SetCurrentEstablishmentUseCase(profileRepo),
        syncCoordinator = coordinator,
        connectivity = connectivity,
        now = { 0L },
    )
}
```
Add a new test, following the same "establish a baseline state, then emit and assert" shape as
`canRespondToSurveys survives a queue emission after profile load`:
```kotlin
@Test
fun `a survey response appears in recent activity`() = runTest(dispatcher) {
    val surveyResponseRepo = FakeSurveyResponseHistoryRepository()
    val profileRepo = FakeProfileRepository().apply {
        myEstablishmentsResult = Result.success(emptyList())
    }
    val vm = viewModel(surveyResponseRepo = surveyResponseRepo, profileRepo = profileRepo)

    vm.state.test {
        awaitState { it.roleLabel != null }

        surveyResponseRepo.localMerged.value = listOf(
            SurveyResponseSummary(
                id = "sr1",
                surveyName = "Encuesta nutricional",
                establishmentName = "Escuela 1",
                eventAt = "2026-06-29T12:00:00Z",
                syncState = SyncState.Pending,
                isLocal = true,
            ),
        )
        val afterEmission = awaitState { it.recent.any { row -> row.id == "sr1" } }
        val row = afterEmission.recent.single { it.id == "sr1" }
        assertEquals(RecentActivityUi.Kind.SurveyResponse, row.kind)
        assertEquals("Escuela 1", row.subtitle)
        assertEquals("Pendiente", row.badgeText)
        cancelAndIgnoreRemainingEvents()
    }
}
```

### Success Criteria

#### Automated Verification
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./scripts/compile.sh` passes
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:testDebugUnitTest --no-daemon --tests "ar.com.ingenieriaalimentacion.presentation.feature.home.HomeViewModelTest"` passes
      — **Note**: `:composeApp:testDebugUnitTest` (unscoped) previously failed to *compile* on a
      pre-existing, unrelated bug in `SqlDelightDatabaseCleanerTest.kt`
      (`cachedSurveyQueries.upsertSummary` — a query name that doesn't exist; the real query is
      `insertOrReplace`, which also takes a `detailJson` param). Because this is a compile error in
      a shared test source set, `--tests` filtering could not route around it, so it was fixed
      directly (renamed the call and added `detailJson = null`) with the user's go-ahead.

#### Manual Verification
- [ ] Submit a survey response (online), stay on/return to Home without navigating elsewhere — its
      row appears in "Actividad reciente" with the correct survey name / establishment / badge
- [ ] Airplane mode + submit a survey response — row appears instantly as "Pendiente"; reconnect —
      badge flips to "Sincronizado" with zero manual refresh
- [ ] The new row's icon is visually distinct from the subject/measurement rows' icons
- [ ] Tapping the survey-response row navigates to History (same as tapping any other row)
- [ ] With more than 4 total recent items across all three types, only the 4 most recent show,
      regardless of type mix

---

## Testing Strategy

### Manual Testing Steps
1. Fresh Home load with existing synced survey responses (from the cache warm-up) — confirm they can
   appear in "Actividad reciente" if recent enough to make the top-4 cut.
2. Submit a new survey response, online — row appears immediately as "Pendiente", flips to
   "Sincronizado" without leaving Home.
3. Submit a new survey response, offline — same as above, but the flip happens after reconnecting.
4. Mix subjects/measurements/survey-responses so more than 4 total exist — confirm only the 4 most
   recent (by their respective timestamp) show, and the ordering interleaves correctly across types.

### Performance Considerations

None beyond what subjects/measurements already do — same `combine()` shape, one more cheap Flow
input; no additional network or DB queries introduced (the cache warm-up call was already added in
the prior plan).

## Migration Notes

No data migration — purely additive to `RecentActivityUi.Kind`, `deriveState()`'s signature (new
param has a default), and `HomeViewModel`'s constructor (DI updated alongside).

## References

- Prior plan (built the reactive floor this plan reuses):
  `thoughts/plans/survey_response_activities_tab.md`
- Research this stemmed from: `thoughts/research/2026-07-13_14-34-55_survey-response-activities-sync.md`
- ADR-011 (why `ActivityItemResponse` is a flat DTO, and why it's irrelevant to Home's path):
  `thoughts/grilling/adr-011-flat-dto-no-polymorphic-serializer.md`
