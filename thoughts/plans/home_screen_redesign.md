# Home Screen Redesign Implementation Plan

## Overview

Replace the placeholder Home screen (centered title + two equal buttons) with a field-tool home that
answers, in priority order, the three questions a field worker (*agente de campo*) actually has:

1. **"Is my data safe?"** — a state-driven **sync-status card** (the centerpiece), wired to the local
   upload queues + connectivity, with a manual *Sincronizar* / *Revisar* action.
2. **"Let me do the next task, fast."** — a **primary `Nueva medición`** action + **secondary
   `Nuevo sujeto`**, with real visual hierarchy (filled vs outline) instead of two equal buttons.
3. **"What did I just do — did anything flag?"** — a **"Hoy" stat strip** (measurements / subjects /
   alerts today) and a **recent-activity list** (last few uploads with sync-state icon, IMC, and
   inline clinical-alert badge) routing to History.

Everything is built from existing design-system components and existing use cases — **no backend
changes, no new domain models, no schema migration.**

## Current State Analysis

The placeholder lives in `presentation/feature/home/` (six files). Today:

- `HomeState` holds only `destination`, a static `title`, and `subtitle` (`HomeState.kt:3-8`).
- `HomeViewModel` has **no dependencies** (`viewModelOf(::HomeViewModel)`,
  `di/ViewModelsModule.kt:28`) and only maps two click actions to destinations
  (`HomeViewModel.kt:21-28`).
- `HomeScreen` renders a centered `Column` with the title, subtitle, and two `AlButton`s
  ("Cargar sujeto", "Cargar medición") (`HomeScreen.kt:46-77`).
- The screen is wired in the **inner** NavHost of `AuthedShell` via an inline
  `composable<Route.Home> { HomeScreenRoot(navigate = …) }` (`AuthedShell.kt:76-86`). That `navigate`
  lambda routes `NavigateToCreateSubject`/`NavigateToCreateMeasurement` through the **outer** nav
  (`LocalOuterNavController`), and `Back` through `outerNav.popBackStack()`. (The separate
  `HomeNavigation.kt` `navigate()` is the standalone variant; its `when` block must stay exhaustive
  too.)

### Key Discoveries

- **Upload flows are ready and unchanged by in-flight work.**
  `ObserveSubjectUploadsUseCase(): Flow<List<SubjectUpload>>`
  (`domain/usecase/subject/ObserveSubjectUploadsUseCase.kt:8`) and
  `ObserveMeasurementsUseCase(): Flow<List<MeasurementUpload>>`
  (`domain/usecase/measurement/ObserveMeasurementsUseCase.kt:9`) are the same flows the History
  segments consume (`SubjectUploadsViewModel.kt:31`, `MeasurementUploadsViewModel.kt:31`).
- **`SyncState`** = `Pending, Syncing, Synced, Merged, Failed` (`domain/model/subject/SyncState.kt:3`).
- **`SyncCoordinator` exposes no observable state** — only `requestSync()` (fire-and-forget,
  single-flight mutex) and `start()` (`data/subject/sync/SyncCoordinator.kt:43,28`). So Home must
  **derive** "is syncing / pending / failed / last synced" from the two upload flows, exactly like the
  History rows derive their own badges. There is **no `lastSyncAt`** — derive it from
  `max(updatedAt)` of `Synced`/`Merged` rows.
- **`ConnectivityObserver.isOnline: Flow<Boolean>`** (distinct-until-changed) + a one-shot
  `suspend fun isOnline()` (`data/network/connectivity/ConnectivityObserver.kt:12,15`). Registered as
  a Koin `single` (consumed by `SyncCoordinator`), so it's injectable.
- **`MeasurementUpload`** carries `subjectName, subjectLastName, subjectDni, weightKg, heightCm,
  measuredAt, syncState, errorMessage, attemptCount, bmi: Double?, alerts: List<ClinicalAlert>,
  createdAt: Long, updatedAt: Long` (`domain/model/measurement/MeasurementUpload.kt:10-28`).
  `SubjectUpload` carries `name, lastName, dni, syncState, createdAt, updatedAt` among others
  (`domain/model/subject/SubjectUpload.kt:12-27`). `ClinicalAlert(alertType, severity, detail)`
  (`domain/model/measurement/ClinicalAlert.kt:12-16`).
- **Identity:** `GetMyProfileUseCase(): Result<Profile>` (suspend,
  `domain/usecase/profile/GetMyProfileUseCase.kt:7`); `Profile(id,email,name,lastName,role,
  establishments)` with `isManager` (`domain/model/profile/Profile.kt:8-17`). Profile is
  **network-only** → Home must treat it as best-effort (generic greeting offline, never blocks).
- **No global "active establishment"** is persisted — `SelectEstablishment` returns its choice via
  `savedStateHandle` (`SelectEstablishmentNavigation.kt`); only `MeasurementViewModel` holds it
  transiently. ⇒ **v1 header shows name + role only** (see *What We're NOT Doing*).
- **Clock:** codebase uses `kotlin.time.Clock.System` directly; stores are injected `now: () -> Long`
  (`di/RepositoriesModule.kt:49,57,…`); a date-formatting precedent is
  `MeasurementViewModel.friendlyNow()` (`MeasurementViewModel.kt:214-219`). Home will inject
  `now: () -> Long` for testable "today" math.
- **Design system available** (all `commonMain`): `AlScaffold(modifier, content)`;
  `AlScreenHeader(title, modifier, subtitle?)`;
  `AlInfoCard(title, modifier, description?, leadingIcon?, leadingIconTint, onClick?, trailing?)`;
  `AlButton(onClick, modifier, enabled, loading, fullWidth, variant, content: RowScope)` with
  `AlButtonVariant { Default, Destructive, Outline, Secondary }`;
  `AlListRow(title, modifier, subtitle?, value?, leadingIcon?, leadingIconTint, titleColor, onClick?,
  showChevron, showDivider, horizontalPadding, trailing?)`;
  `AlStatusBadge(text, tone)` with `AlBadgeTone { Success, Warning, Danger, Neutral }`;
  `EmptyState(title, caption, modifier)`. Tokens: `AlTheme.colors/spacing/typography/corners/borders`.
- **Icons present:** `ic_mingcute_add_line`, `ic_mingcute_user_3_line`, `ic_mingcute_upload_line`,
  `ic_mingcute_alert_line`, `ic_mingcute_home_line`, `ic_mingcute_right_line`. **No ruler/scale icon
  exists** → add one for the primary action (Phase 3) or fall back to `ic_mingcute_add_line`.

### Compatibility with the in-flight offline-reads plan

`thoughts/plans/measurement_offline_reads.md` changes subject/establishment **reads** and adds
**measurement sync gating** (a measurement can be *held* — kept `Pending`, not attempted — until its
subject syncs). It does **not** touch the upload observe flows, `ConnectivityObserver`, or
`SyncCoordinator`. Two consequences for this plan, both already accommodated:

1. A *held* measurement simply stays `Pending`, so it naturally counts as "sin subir" in the sync
   card — no special handling needed.
2. This plan must not assume current read paths are final and must not add its own
   establishment/session persistence (we don't). Home depends only on the stable write-queue flows.

## Desired End State

Opening the app lands on a Home that:

- Greets the user by name (or a generic greeting offline) with a role label.
- Shows one sync-status card that is honest about queue + connectivity state and offers the right
  one action (Sincronizar when online with pending; Revisar when failures; nothing when clean).
- Presents a clearly-primary `Nueva medición` and a secondary `Nuevo sujeto`.
- Shows today's counts and the last few activity rows with correct sync/alert badges, tappable
  through to History.
- Never crashes or blocks offline; degrades gracefully (generic greeting, "Sin conexión" card copy,
  empty-state for no activity).

Verify via the manual walkthrough in *Testing Strategy* (online, airplane-mode, with-pending,
with-failure, empty).

## What We're NOT Doing

- **No "active establishment" in the header (v1).** Nothing persists it globally; adding a session
  store overlaps the in-flight offline-reads work. Deferred.
- **No new `lastSyncAt`/`isSyncing` state in `SyncCoordinator`.** Home derives both from the upload
  flows. (A real coordinator-level sync-state is a future enhancement.)
- **No backend, domain-model, DTO, or SQLDelight changes.** Read-only consumption of existing flows.
- **No subject/measurement *read* changes** — that's the separate offline-reads plan.
- **No relative "hace 5 min" time engine.** Last-synced is rendered as an absolute `dd/MM HH:mm`
  (reusing the `friendlyNow` formatting style) to avoid a ticking-clock dependency.
- **No pull-to-refresh / SWR** on Home in v1 — flows are reactive; profile loads once on entry.

## Implementation Approach

Three phases, each compiling. Phase 1 builds the entire ViewModel/state/derivation layer (unit-
testable, no UI risk) plus the new navigation destination. Phase 2 builds the UI from existing
components. Phase 3 is polish (new icon, empty/offline copy). The placeholder keeps working until
Phase 2 swaps the body.

---

## Phase 1: Home data layer — state, derivations, ViewModel, navigation destination

### Overview
Turn `HomeViewModel` into a real screen model that merges the two upload flows + connectivity +
profile into a derived `HomeState`, and add a `NavigateToHistory` destination.

### Changes Required

#### 1. State model + UI sub-models
**File**: `presentation/feature/home/HomeState.kt`
```kotlin
data class HomeState(
    val destination: HomeDestination? = null,
    val greetingName: String? = null,          // null -> generic "Hola"
    val roleLabel: String? = null,
    val isOnline: Boolean = true,
    val sync: SyncStatusUi = SyncStatusUi.AllSynced(lastSyncedLabel = null),
    val todayMeasurements: Int = 0,
    val todaySubjects: Int = 0,
    val todayAlerts: Int = 0,
    val recent: List<RecentActivityUi> = emptyList(),
)

sealed interface SyncStatusUi {
    data class AllSynced(val lastSyncedLabel: String?) : SyncStatusUi   // online & clean
    data object OfflineClean : SyncStatusUi                              // offline & nothing queued
    data class Syncing(val count: Int) : SyncStatusUi                   // any row Syncing
    data class PendingOnline(val count: Int) : SyncStatusUi             // pending & online
    data class PendingOffline(val count: Int) : SyncStatusUi           // pending & offline
    data class Failed(val failedCount: Int, val pendingCount: Int) : SyncStatusUi
}

data class RecentActivityUi(
    val id: String,
    val kind: Kind,                 // for the leading icon
    val title: String,              // "Nombre Apellido"
    val subtitle: String?,          // "DNI 12.345.678" (subject) or null
    val value: String?,             // "IMC 24.1" (measurement) or null
    val badgeText: String?,         // alert label or sync label
    val badgeTone: AlBadgeTone,
    val createdAt: Long,
) {
    enum class Kind { Subject, Measurement }
}
```

#### 2. Action + Destination
**File**: `presentation/feature/home/HomeAction.kt` — replace placeholder actions:
```kotlin
sealed interface HomeAction {
    data object NewMeasurementClick : HomeAction
    data object NewSubjectClick : HomeAction
    data object SyncClick : HomeAction          // "Sincronizar" / "Revisar"
    data object SeeAllActivityClick : HomeAction
    data object AlertsClick : HomeAction        // tap the "alertas hoy" stat
}
```
**File**: `presentation/feature/home/HomeDestination.kt` — add History:
```kotlin
sealed class HomeDestination {
    data object Back : HomeDestination()
    data object NavigateToCreateSubject : HomeDestination()
    data object NavigateToCreateMeasurement : HomeDestination()
    data object NavigateToHistory : HomeDestination()   // switch to the History tab
}
```

#### 3. ViewModel — merge flows + derive
**File**: `presentation/feature/home/HomeViewModel.kt`
```kotlin
class HomeViewModel(
    observeSubjectUploads: ObserveSubjectUploadsUseCase,
    observeMeasurements: ObserveMeasurementsUseCase,
    private val getMyProfile: GetMyProfileUseCase,
    private val syncCoordinator: SyncCoordinator,
    connectivity: ConnectivityObserver,
    private val now: () -> Long,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        // identity (best-effort; offline -> generic greeting, never blocks)
        viewModelScope.launch {
            getMyProfile().onSuccess { p ->
                _state.update { it.copy(greetingName = p.name, roleLabel = roleLabel(p)) }
            }
        }
        // reactive queue + connectivity -> derived state
        viewModelScope.launch {
            combine(
                observeSubjectUploads(),
                observeMeasurements(),
                connectivity.isOnline,
            ) { subjects, measurements, online ->
                deriveState(subjects, measurements, online, now())
            }.collect { derived -> _state.update { derived.copy(destination = it.destination,
                greetingName = it.greetingName, roleLabel = it.roleLabel) } }
        }
    }

    fun onAction(action: HomeAction) { /* map to destination / requestSync */ }
    fun clearDestination() { _state.update { it.copy(destination = null) } }
}
```
Key derivation rules (pure function `deriveState`, unit-tested):
- **pending** = rows in `Pending` or `Syncing`; **failed** = rows in `Failed`; counts across *both*
  subjects + measurements.
- `sync` precedence: any `Syncing` → `Syncing`; else `failed>0` → `Failed`; else `pending>0` →
  `PendingOnline`/`PendingOffline` by `online`; else `online` → `AllSynced(lastSyncedLabel)` /
  `OfflineClean`. `lastSyncedLabel` = format(`max(updatedAt)` of `Synced`/`Merged`), else null.
- **today** (local TZ, computed from `now`): `todayMeasurements`/`todaySubjects` = rows with
  `createdAt >= startOfTodayMillis(now)`; `todayAlerts` = today's measurements with
  `alerts.isNotEmpty()`.
- **recent** = (subjects ∪ measurements) → `RecentActivityUi`, sorted by `createdAt` desc,
  `take(4)`. Measurement badge: if `alerts` non-empty → top alert label, tone by severity
  (`Danger`/`Warning`); else sync-state badge. Subject badge: sync-state badge.
- `onAction`: `SyncClick` → `syncCoordinator.requestSync()` (no destination); navigation actions set
  `destination`; `SeeAllActivityClick`/`AlertsClick` → `NavigateToHistory`.
- `roleLabel(p)`: map known roles to Spanish (`p.isManager` → "Responsable de establecimiento";
  else "Agente de campo"); raw `role` as fallback.

Put `deriveState`, `startOfTodayMillis`, `roleLabel`, and the date formatter in a sibling
`HomeStateMapper.kt` (pure, no Android deps) so they're unit-testable.

#### 4. Navigation handlers (keep `when` exhaustive in BOTH places)
**File**: `app/AuthedShell.kt` (the inline `composable<Route.Home>`, `:76-86`) — add the History case
using the inner nav with the same save/restore options used by the bottom bar (`:52-63`):
```kotlin
HomeDestination.NavigateToHistory -> innerNav.navigate(Route.History) {
    popUpTo(innerNav.graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
```
**File**: `presentation/feature/home/HomeNavigation.kt` — add `NavigateToHistory` to the standalone
`navigate()` `when` (route via `navController.navigate(Route.History)`) so the file still compiles.

#### 5. Koin (inject `now`)
**File**: `di/ViewModelsModule.kt:28` — replace `viewModelOf(::HomeViewModel)` with an explicit block
(needed because `now: () -> Long` can't be auto-resolved by type):
```kotlin
viewModel {
    HomeViewModel(
        observeSubjectUploads = get(),
        observeMeasurements = get(),
        getMyProfile = get(),
        syncCoordinator = get(),
        connectivity = get(),
        now = { Clock.System.now().toEpochMilliseconds() },
    )
}
```
(`SyncCoordinator` is a `single` in `databaseModule:19`; `ConnectivityObserver` is a registered
`single`; the use cases are `factory` in `useCasesModule` — all resolvable.)

### Success Criteria

#### Automated Verification:
- [x] commonMain compiles: `./scripts/compile.sh`
- [x] Unit tests for `HomeStateMapper.deriveState` (pure):
  - [x] all `Synced`/`Merged`, online → `AllSynced` with a formatted `lastSyncedLabel`
  - [x] all done, offline → `OfflineClean`
  - [x] a `Syncing` row → `Syncing(count)` regardless of online
  - [x] `Pending` rows online → `PendingOnline`; offline → `PendingOffline`
  - [x] a `Failed` row → `Failed(failedCount, pendingCount)` (failure wins over pending)
  - [x] today counts respect `startOfTodayMillis` boundary (row at 23:59 yesterday excluded)
  - [x] `recent` merges both sources, sorts desc, caps at 4; measurement with alerts shows the alert
        badge (Danger/Warning), measurement without alerts shows the sync badge
- [x] Android build: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:assembleDebug --no-daemon`

#### Manual Verification:
- [ ] App still launches to Home (placeholder UI still rendering) with no crash — VM wiring is sound.

---

## Phase 2: Home UI composition

### Overview
Replace the centered placeholder body with the four stacked sections, top-aligned and vertically
scrollable, all built from existing components.

### Changes Required

#### 1. Rewrite `HomeScreen` body
**File**: `presentation/feature/home/HomeScreen.kt` — `AlScaffold { Column(verticalScroll, padding =
AlTheme.spacing.lg, spacedBy = AlTheme.spacing.lg) { … } }`, sections in order:

1. **Identity header** — `AlScreenHeader(title = greeting, subtitle = roleLabel)` where
   `greeting = "Hola, ${greetingName}"` or `"Hola"` when null. (Left-aligned; replaces centered
   layout.)

2. **Sync-status card** — one `AlInfoCard` whose `title`/`description`/`leadingIcon`/`leadingIconTint`
   /`onClick`/`trailing` are chosen from `state.sync` via a small `@Composable` mapper:
   | `SyncStatusUi` | icon / tint | title | description | trailing action |
   |---|---|---|---|---|
   | `AllSynced` | `upload_line` / `accent` | "Todo sincronizado" | lastSyncedLabel? | — |
   | `OfflineClean` | `upload_line` / `textMuted` | "Sin conexión" | "Todo guardado en el dispositivo" | — |
   | `Syncing(n)` | `upload_line` / `accent` | "Sincronizando…" | "$n en proceso" | — |
   | `PendingOnline(n)` | `upload_line` / `warning` | "$n sin subir" | "Tocá para sincronizar" | `AlButton(Outline,"Sincronizar")` → `SyncClick` |
   | `PendingOffline(n)` | `upload_line` / `textMuted` | "Sin conexión" | "$n se subirán al reconectar" | — |
   | `Failed(f,p)` | `alert_line` / `danger` | "$f con error" | pending hint | `AlButton(Outline,"Revisar")` → `SyncClick`→History (Revisar routes to History via `AlertsClick`/dedicated) |
   Use `accentContainer`/`warningContainer`/`dangerContainer` surfaces where the card supports tone.

3. **Primary actions** — two full-width `AlButton`s with leading icons:
   - `AlButton(fullWidth = true, variant = Default, onClick = NewMeasurementClick)` → icon (ruler,
     Phase 3; fallback `add_line`) + "Nueva medición".
   - `AlButton(fullWidth = true, variant = Outline, onClick = NewSubjectClick)` → `user_3_line` +
     "Nuevo sujeto".

4. **"Hoy" strip** — a small section label `Text("HOY", style = caption, color = textMuted)` over a
   `Row(spacedBy sm)` of three equal-weight stat cards (lightweight `Card`/surface with big number +
   caption, built with tokens): mediciones, sujetos, alertas. The **alertas** card is tinted
   (`warningContainer`/`dangerContainer`) and `onClick = AlertsClick` when `todayAlerts > 0`.

5. **Recent activity** — section header row: `Text("ACTIVIDAD RECIENTE")` + a text button "Ver todo"
   → `SeeAllActivityClick`. Then either `EmptyState("Sin actividad", "Tus cargas aparecerán acá")`
   when `recent.isEmpty()`, or `recent.forEach { AlListRow(title, subtitle, value, leadingIcon by
   kind, leadingIconTint by sync, onClick = SeeAllActivityClick, trailing = { it.badgeText?.let {
   t -> AlStatusBadge(t, badgeTone) } }) }`.

`HomeScreenRoot` keeps its existing `LaunchedEffect(state.destination)` → `navigate` →
`clearDestination()` pattern (`HomeScreen.kt:26-33`); only the `HomeScreen` composable body changes.

### Success Criteria

#### Automated Verification:
- [x] commonMain compiles: `./scripts/compile.sh`
- [x] Android build: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:assembleDebug --no-daemon`

#### Manual Verification (Android + iOS):
- [ ] Header greets by name when online; "Hola" generic when profile fails/offline.
- [ ] `Nueva medición` is visually primary (filled), `Nuevo sujeto` secondary (outline); both
      navigate correctly via the outer nav.
- [ ] Sync card renders the right variant for: clean, pending+online (Sincronizar button works),
      pending+offline (airplane mode), failed (Revisar → History).
- [ ] "Hoy" counts match what was created today; alerts card highlights and is tappable when > 0.
- [ ] Recent list shows newest first, correct IMC + alert/sync badges; empty-state shows on a fresh
      account; "Ver todo" and rows switch to the History tab (back stack preserved).
- [ ] Content scrolls without overflow on a small screen; no regressions in History/Profile tabs.

---

## Phase 3: Polish — measurement icon, copy, offline nuance

### Overview
Small finishing touches that depend on Phase 2 being visible.

### Changes Required

#### 1. Measurement icon
Add a ruler/scale MingCute icon via the **`add-mingcute-icon` skill** (e.g.
`ic_mingcute_ruler_line` or `ic_mingcute_scales_line`), rebuild once so the `Res.drawable.*`
accessor generates, then point the `Nueva medición` button at it. If skipped, the button keeps the
`ic_mingcute_add_line` fallback from Phase 2 (no blocker).

#### 2. Copy + tone review
- Confirm Spanish microcopy reads naturally on device (card titles, "Ver todo", empty-state).
- Verify alert severity → tone mapping against real `ClinicalAlert.severity` strings observed from a
  synced measurement (adjust the `Danger`/`Warning` cutoff to match actual server values).

### Success Criteria

#### Automated Verification:
- [x] commonMain compiles: `./scripts/compile.sh`
- [x] If an icon was added: `Res.drawable.<new_icon>` resolves (build regenerates accessors). Added
      `ic_mingcute_ruler_line`; wired to the `Nueva medición` button + measurement recent rows.
- [x] Android build: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:assembleDebug --no-daemon`

#### Manual Verification:
- [ ] Primary action shows the measurement icon (or documented fallback).
- [ ] Alert badges use the correct tone for the real severity values.

---

## Testing Strategy

### Unit tests (Phase 1, pure `HomeStateMapper`)
The sync-state precedence matrix, today-boundary math, and recent merge/sort/cap/badge logic — fully
covered without Compose, using hand-built `SubjectUpload`/`MeasurementUpload` lists.

### Manual end-to-end walkthrough
1. **Online, with history** → header has name; card "Todo sincronizado" + last-synced; today counts
   correct; recent list populated.
2. **Create activity** → load a subject + a measurement; return to Home → counts increment, rows
   appear with `Pending` then `Synced` badges as sync completes.
3. **Airplane mode** → card flips to "Sin conexión / se subirán al reconectar"; create a measurement
   offline → it appears `Pending`; reconnect → card returns to clean, rows flip to `Synced`.
4. **Failure path** → force a dead-letter (per offline-reads gating, e.g. a measurement whose subject
   fails) → card shows "$n con error" + Revisar → History.
5. **Fresh account** → empty-state in recent activity; zeros in "Hoy"; generic greeting if offline.

## Performance Considerations

- Two already-active local SQLDelight flows `combine`d with a connectivity flow — same data the
  History tab already streams; negligible extra cost. Derivations are O(n) over a small local queue.
- Profile is fetched once per Home entry (best-effort); no polling, no SWR.

## Migration Notes

None — no schema, DTO, or persisted-state changes. Pure presentation-layer addition over existing
flows.

## References

- In-flight, must-stay-consistent: `thoughts/plans/measurement_offline_reads.md`
- Related history pattern: `thoughts/plans/measurement_history_screen.md`
- Key code:
  `presentation/feature/home/*` (placeholder),
  `app/AuthedShell.kt:76-86,52-63,104-116`,
  `di/ViewModelsModule.kt:28`, `di/DatabaseModule.kt:19`,
  `domain/usecase/subject/ObserveSubjectUploadsUseCase.kt:8`,
  `domain/usecase/measurement/ObserveMeasurementsUseCase.kt:9`,
  `data/subject/sync/SyncCoordinator.kt:43`,
  `data/network/connectivity/ConnectivityObserver.kt:12,15`,
  `domain/model/measurement/MeasurementUpload.kt:10-28`,
  `domain/model/subject/SubjectUpload.kt:12-27`,
  `domain/model/measurement/ClinicalAlert.kt:12-16`,
  `domain/usecase/profile/GetMyProfileUseCase.kt:7`, `domain/model/profile/Profile.kt:8-17`,
  `presentation/.../create_measurement/MeasurementViewModel.kt:214-219` (date format precedent)
</content>
</invoke>
