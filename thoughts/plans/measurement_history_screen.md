# Historial de Mediciones (Historia 3) — Implementation Plan

## Overview

Build the **measurement history** screen: a relevador watches the sync state of every
measurement captured on this device, sees server-computed **BMI** and clinical
**alerts** once a row syncs, and can **retry**, **delete**, or **sync now**. The
history observes the local measurement queue via a reactive `Flow`, so rows update
themselves as the background sync engine moves them through
`PENDING → SYNCING → SYNCED/FAILED`.

Per the chosen design, the existing **subjects "Historial" tab becomes a combined
hub** with two segments — **Sujetos | Mediciones** — sharing one bottom-nav entry.
The subjects list is refactored into a reusable body; the measurements list is new;
a small `AlSegmentedControl` switches between them.

This plan is the **read side** of the measurement queue and **assumes Historia 2
(`thoughts/plans/create_measurement_screen.md`) is implemented** — it consumes the
`MeasurementRepository`, `MeasurementUpload` domain model, `ObserveMeasurementsUseCase`,
and the **generalized `SyncCoordinator`** that Historia 2 builds, and adds only the two
use cases Historia 2 explicitly deferred (`Retry`/`Delete`) plus all the presentation.

## Current State Analysis

- **The subjects "Historial" feature is the template and is fully shipped.**
  `presentation/feature/subject/uploads/` has the 7-file bundle
  (`SubjectUploadsAction/Destination/Navigation/Screen/State/ViewModel` + the row UI
  model `SubjectUploadUi`). `SubjectUploadsScreen.kt` is `AlScaffold { Column { Header;
  when { isEmpty -> EmptyState() else -> LazyColumn { SubjectUploadRow } } } }`
  (`SubjectUploadsScreen.kt:54-72`); the `Header` (`:74-102`) holds the title
  "Historial", a caption, and an Outline **"Sincronizar"** button; `SubjectUploadRow`
  (`:104-177`) shows title/subtitle, optional 16dp spinner, an `AlStatusBadge`, inline
  **Reintentar**/**Eliminar** buttons, and a thin divider.
- **The VM pattern to mirror.** `SubjectUploadsViewModel` collects
  `ObserveSubjectUploadsUseCase()` inside `viewModelScope.launch` into a
  `MutableStateFlow`, re-exposes it via `stateIn(viewModelScope,
  WhileSubscribed(5_000), …)`, flips `isLoading` false on first emission, calls
  `syncCoordinator.requestSync()` opportunistically in `init`, and on `Retry` runs
  `retryUpload(id)` then `requestSync()`; `Delete` just deletes (the Flow re-emits).
- **`SubjectUploadUi.toUi()` is the localization seam** (`SubjectUploadUi.kt:19-53`):
  `SyncState → (label, AlBadgeTone)`, `title = "$name $lastName"`, `subtitle = "DNI …"`
  (+ `· errorMessage` on Failed / `· id …` on Merged), `showSpinner = Syncing`,
  `canRetry = Failed`, `canDelete = Pending|Failed|Merged`. **No relative-time** is
  rendered anywhere — timestamps are unused today.
- **The Uploads tab wiring.** `Route.SubjectUploads` (`Route.kt:28-30`,
  `@SerialName("subject_uploads")`), `AlBottomNavTab.Uploads("Historial",
  ic_mingcute_upload_line)` (`AlBottomNavBar.kt:29-33`), and `AuthedShell.kt:44-48,
  51-57, 103-111` map the tab to a `composable<Route.SubjectUploads>` rendering
  `SubjectUploadsScreenRoot` in the **inner** `NavHost`. Tab switches use
  `popUpTo(start){saveState}; launchSingleTop; restoreState` so each tab's
  `ViewModelStore` (and thus its VM) survives switching.
- **Design-system primitives, no tab control.** `AlStatusBadge(text, tone:
  AlBadgeTone{Success,Warning,Danger,Neutral})` (`badge/AlStatusBadge.kt`) and
  `AlPill(text, tone: AlPillTone{Neutral,Success,Warning,Danger})` (`pill/AlPill.kt`)
  exist; **no `TabRow`/segmented control exists anywhere in commonMain** (verified).
  Tokens available: `AlTheme.colors.{accent,accentContainer,warning,warningContainer,
  danger,dangerContainer,surfaceBase,surfaceMuted,textBase,textMuted,border}`,
  `AlTheme.typography.{title,body,caption}`, `AlTheme.spacing.{xs,sm,md,xl}`,
  `AlTheme.corners.sm`, `AlTheme.borders.thin`.
- **Crash-recovery and GC are already handled by Historia 2's coordinator.** The
  generalized `SyncCoordinator` (Historia 2 Phase 1) runs `resetStuckSyncing()` for
  every engine on `start()` and `pruneTerminal(7.days)` hourly. So Historia 3's
  "Syncing→Pending on launch" and "GC terminal rows" acceptance criteria are satisfied
  by the existing engine — this plan only **verifies** them, it does not re-implement.

### Key Discoveries
- **What Historia 2 leaves for us.** Its Phase 4 builds `MeasurementRepository :
  SyncEngine` with `observeUploads(): Flow<List<MeasurementUpload>>`, `retry(localId)`,
  `delete(localId)`, plus `CreateMeasurementUseCase` and **`ObserveMeasurementsUseCase`**.
  It states verbatim: *"Retry/Delete/SyncNow use cases are Historia 3."* So we add
  `RetryMeasurementUploadUseCase` + `DeleteMeasurementUploadUseCase`; "Sync now" goes
  through the coordinator (no use case), exactly like subjects.
- **`MeasurementUpload` already carries everything the row needs**
  (`create_measurement_screen.md` Phase 2): `localId, subjectName, subjectLastName,
  subjectDni, establishmentName, weightKg: Double, heightCm: Double, measuredAt: String
  (ISO), syncState, errorMessage, attemptCount, bmi: Double?, alerts: List<ClinicalAlert>,
  createdAt, updatedAt`. No extra fetch. `ClinicalAlert(alertType, severity, detail)`.
- **Measurements never reach `SyncState.Merged`** (no canonical dedup). The row UI maps
  only `Pending/Syncing/Synced/Failed`; the shared enum keeps `Merged` but it is never
  produced.
- **The hub is mostly UI.** It owns a `selectedSegment` toggle (UI state via
  `rememberSaveable`, restored by the tab's saved back-stack entry) and hosts the two
  content roots; each child `koinViewModel()` is retained in the back-stack entry's
  `ViewModelStore` for the tab's lifetime, so switching segments does **not** recreate
  the VMs or lose their observed state.
- **Single hub-level "Sincronizar".** The hub injects the (generalized) `SyncCoordinator`
  via `koinInject()` — the exact precedent in `AuthedShell.SyncTriggers`
  (`AuthedShell.kt:126`) — and `requestSync()` syncs **all** engines in one pass, so one
  button covers both segments.

## Desired End State

The bottom-nav **"Historial"** tab opens a hub with a **Sujetos | Mediciones**
segmented control and a single **"Sincronizar"** action. The **Mediciones** segment
lists every locally-captured measurement, newest first, each showing the subject
(name + DNI), **peso**, **talla**, **fecha**, and a state **badge** (Pendiente /
Sincronizando / Sincronizado / Error). **Sincronizado** rows additionally show the
server **IMC** and any clinical **alertas** (tipo · severidad + detalle). **Reintentar**
appears on **Error** rows; **Eliminar** on **Pendiente / Error / Sincronizado** rows.
The list updates itself as rows sync. Loading shows a spinner; empty shows a friendly
message. The **Sujetos** segment renders the existing subjects history unchanged.

### Verification
- `./scripts/compile.sh` passes after every phase; `:composeApp:assembleDebug` after UI
  phases; `:composeApp:testDebugUnitTest` after the test phase.
- Manual: capture a measurement offline (Historia 2) → it appears under **Mediciones**
  as **Pendiente**; go online → **Sincronizado** with IMC + alerts; force 422/403/404 →
  **Error** with message + working **Reintentar**/**Eliminar**; switching segments keeps
  both lists' state; kill mid-sync → on relaunch the row is back to **Pendiente**.

## What We're NOT Doing

- **Not building the measurement write side / queue.** The table, repository, sync
  engine, API, DTOs, and `create_measurement` form are Historia 2. This plan **depends
  on them**. If Historia 2 is not merged yet, it must land first (or in the same branch);
  see *Migration Notes*.
- **Not re-implementing crash recovery or GC** — Historia 2's generalized coordinator
  already does `resetStuckSyncing()` on start and hourly `pruneTerminal(7.days)`. We
  only verify them.
- **Not a separate measurements bottom-nav tab** and **not a pushed route** — the chosen
  design is one combined "Historial" hub tab.
- **Not adding relative-time / "hace X" formatting beyond a compact date** for the
  measurement row (subjects render no time; we add a simple `dd/MM HH:mm` for
  measurements only, where the story asks for "fecha").
- **No measurement detail screen** — alerts render inline on the synced row (the story
  allows "historial / detalle"; inline satisfies it). A drill-in detail is a later option.
- **No edit-before-retry** — retry re-queues as-is; fix-by-delete-and-recapture (matches
  subjects).

## Implementation Approach

Bottom-up so each phase compiles. **Phase 1** adds the two deferred measurement use cases
+ DI (compiles against Historia 2's repo). **Phase 2** adds the `AlSegmentedControl`
design-system component (standalone). **Phase 3** builds the measurement uploads
presentation (UI model, state, action, VM, content/row, content root) + VM DI — compiles
but not yet on screen. **Phase 4** refactors the subjects list into a reusable body and
builds the **History hub**, then **Phase 5** rewires the tab/route to the hub. **Phase 6**
adds focused tests.

Data flow mirrors subjects: **sync engine writes the DB → VM observes via Flow →
UI renders**; user actions (retry/delete/sync) call use cases / the coordinator, and the
Flow re-emits.

---

## Phase 1: Measurement Retry/Delete use cases + DI

### Overview
Add the two use cases Historia 2 deferred; wire them in Koin. `ObserveMeasurementsUseCase`
already exists (Historia 2).

### Changes Required

#### 1. `domain/usecase/measurement/RetryMeasurementUploadUseCase.kt` (new)
```kotlin
class RetryMeasurementUploadUseCase(private val repository: MeasurementRepository) {
    suspend operator fun invoke(localId: String): Result<Unit> = repository.retry(localId)
}
```

#### 2. `domain/usecase/measurement/DeleteMeasurementUploadUseCase.kt` (new)
```kotlin
class DeleteMeasurementUploadUseCase(private val repository: MeasurementRepository) {
    suspend operator fun invoke(localId: String): Result<Unit> = repository.delete(localId)
}
```
> Mirrors `domain/usecase/subject/{Retry,Delete}SubjectUploadUseCase.kt` exactly.

#### 3. DI — `di/UseCasesModule.kt`
```kotlin
factory { RetryMeasurementUploadUseCase(get()) }
factory { DeleteMeasurementUploadUseCase(get()) }
// ObserveMeasurementsUseCase already registered by Historia 2
```

### Success Criteria
#### Automated Verification:
- [x] Common compiles: `./scripts/compile.sh`
#### Manual Verification: none.

---

## Phase 2: `AlSegmentedControl` design-system component

### Overview
A small two-plus-segment toggle styled with theme tokens (selected = accent
container/text, unselected = muted), used by the hub. Reusable.

### Changes Required

#### 1. `core/design_system/components/segmented/AlSegmentedControl.kt` (new)
```kotlin
@Composable
fun AlSegmentedControl(
    segments: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AlTheme.corners.sm))
            .background(AlTheme.colors.surfaceMuted)
            .padding(AlTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs),
    ) {
        segments.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(AlTheme.corners.sm))
                    .background(if (selected) AlTheme.colors.accentContainer else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = AlTheme.spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = AlTheme.typography.body.copy(
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    ),
                    color = if (selected) AlTheme.colors.accent else AlTheme.colors.textMuted,
                )
            }
        }
    }
}
```
> Styling mirrors `AlPill`/`AlStatusBadge` (clip + tokenized container/foreground). No
> new tokens. Designed states (selected/unselected) satisfy the design-quality
> hover/focus/active expectation for a toggle.

### Success Criteria
#### Automated Verification:
- [x] Common compiles: `./scripts/compile.sh`
#### Manual Verification: rendered in Phase 4/5.

---

## Phase 3: Measurement uploads presentation bundle

### Overview
The measurements list: a row UI model + `toUi()` mapper (badge, subtitle, BMI, alerts,
action eligibility), state, action, ViewModel (observe + retry/delete), the stateless
content/row composables, and a content root that owns the VM. No navigation files — this
is hosted inside the hub, not a standalone destination. DI registers the VM.

### Changes Required

#### 1. Row UI model + mapper — `presentation/feature/measurement/uploads/MeasurementUploadUi.kt` (new)
```kotlin
data class ClinicalAlertUi(val label: String, val detail: String, val tone: AlPillTone)

data class MeasurementUploadUi(
    val localId: String,
    val title: String,                 // "Nombre Apellido"
    val subtitle: String,              // "DNI … · 70 kg · 1,72 m · 21/06 18:30" (+ error on Failed)
    val badgeLabel: String,
    val badgeTone: AlBadgeTone,
    val showSpinner: Boolean,
    val bmiLabel: String?,             // "IMC 23,7" when Synced & bmi != null
    val alerts: List<ClinicalAlertUi>, // only populated on Synced
    val canRetry: Boolean,
    val canDelete: Boolean,
)

internal fun MeasurementUpload.toUi(): MeasurementUploadUi {
    val (label, tone) = when (syncState) {
        SyncState.Pending -> "Pendiente"     to AlBadgeTone.Neutral
        SyncState.Syncing -> "Sincronizando" to AlBadgeTone.Neutral
        SyncState.Synced  -> "Sincronizado"  to AlBadgeTone.Success
        SyncState.Failed  -> "Error"         to AlBadgeTone.Danger
        SyncState.Merged  -> "Sincronizado"  to AlBadgeTone.Success // unreachable for measurements
    }
    val subtitle = buildString {
        append("DNI "); append(subjectDni)
        append(" · "); append(formatKg(weightKg)); append(" kg")
        append(" · "); append(formatCm(heightCm)); append(" cm")
        append(" · "); append(formatMeasuredAt(measuredAt))
        if (syncState == SyncState.Failed && !errorMessage.isNullOrBlank()) {
            append(" · "); append(errorMessage)
        }
    }
    val synced = syncState == SyncState.Synced
    return MeasurementUploadUi(
        localId = localId,
        title = "$subjectName $subjectLastName",
        subtitle = subtitle,
        badgeLabel = label,
        badgeTone = tone,
        showSpinner = syncState == SyncState.Syncing,
        bmiLabel = bmi?.takeIf { synced }?.let { "IMC ${formatBmi(it)}" },
        alerts = if (synced) alerts.map { it.toUi() } else emptyList(),
        canRetry = syncState == SyncState.Failed,
        canDelete = syncState == SyncState.Pending || syncState == SyncState.Failed || synced,
    )
}

private fun ClinicalAlert.toUi() = ClinicalAlertUi(
    label = "$alertType · $severity",
    detail = detail,
    tone = when (severity.trim().lowercase()) {
        "critical", "severe", "high", "alta", "critica", "crítica" -> AlPillTone.Danger
        "moderate", "medium", "warning", "media", "moderada"        -> AlPillTone.Warning
        else                                                         -> AlPillTone.Neutral
    },
)
```
Formatting helpers in the same file (kotlinx-datetime 0.7.1 is on the classpath; `String.format`
is **not** available in commonMain — use manual padding):
```kotlin
private fun formatBmi(v: Double): String = formatDecimal(v, 1)            // "23,7"
private fun formatKg(v: Double): String = formatDecimal(v, 1)
private fun formatCm(v: Double): String = formatDecimal(v, 1)

private fun formatDecimal(v: Double, decimals: Int): String { /* round, drop trailing .0, use ',' */ }

private fun formatMeasuredAt(iso: String): String = runCatching {
    val ldt = Instant.parse(iso).toLocalDateTime(TimeZone.currentSystemDefault())
    val dd = ldt.dayOfMonth.toString().padStart(2, '0')
    val mm = ldt.monthNumber.toString().padStart(2, '0')
    val hh = ldt.hour.toString().padStart(2, '0')
    val min = ldt.minute.toString().padStart(2, '0')
    "$dd/$mm $hh:$min"
}.getOrDefault(iso)
```

#### 2. State — `.../uploads/MeasurementUploadsState.kt` (new)
```kotlin
data class MeasurementUploadsState(
    val uploads: List<MeasurementUploadUi> = emptyList(),
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && uploads.isEmpty()
}
```
> No `destination` field — the measurements list has no outward navigation (it lives in
> the hub tab). This is the one deliberate deviation from the 6-file pattern; justified
> because the content is a tab body, like the subjects body after Phase 4.

#### 3. Action — `.../uploads/MeasurementUploadsAction.kt` (new)
```kotlin
sealed interface MeasurementUploadsAction {
    data class Retry(val localId: String) : MeasurementUploadsAction
    data class Delete(val localId: String) : MeasurementUploadsAction
}
```
> "Sincronizar" is owned by the hub (single button → coordinator), so there is no
> `SyncNow` here.

#### 4. ViewModel — `.../uploads/MeasurementUploadsViewModel.kt` (new)
```kotlin
class MeasurementUploadsViewModel(
    private val observeUploads: ObserveMeasurementsUseCase,
    private val retryUpload: RetryMeasurementUploadUseCase,
    private val deleteUpload: DeleteMeasurementUploadUseCase,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(MeasurementUploadsState())
    val state = _state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), _state.value)

    init {
        viewModelScope.launch {
            observeUploads().collect { uploads ->
                _state.update { it.copy(uploads = uploads.map { u -> u.toUi() }, isLoading = false) }
            }
        }
        syncCoordinator.requestSync()
    }

    fun onAction(action: MeasurementUploadsAction) {
        when (action) {
            is MeasurementUploadsAction.Retry -> viewModelScope.launch {
                retryUpload(action.localId); syncCoordinator.requestSync()
            }
            is MeasurementUploadsAction.Delete -> viewModelScope.launch { deleteUpload(action.localId) }
        }
    }
}
```
> Byte-for-byte the subjects VM shape, minus destination/SyncNow.

#### 5. Content + row — `.../uploads/MeasurementUploadsContent.kt` (new)
Stateless body (no `AlScaffold`, no header — the hub provides those):
```kotlin
@Composable
fun MeasurementUploadsContent(
    state: MeasurementUploadsState,
    onAction: (MeasurementUploadsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = AlTheme.colors.accent)
        }
        state.isEmpty -> EmptyState(
            title = "Sin mediciones todavía",
            caption = "Las mediciones que cargues aparecerán acá con su estado de sincronización.",
        )
        else -> LazyColumn(modifier.fillMaxSize()) {
            items(state.uploads, key = { it.localId }) { MeasurementUploadRow(it, onAction) }
        }
    }
}
```
`MeasurementUploadRow` mirrors `SubjectUploadRow` (`SubjectUploadsScreen.kt:104-177`)
plus, when `bmiLabel != null` / `alerts` non-empty, an extra block under the title row:
```kotlin
// inside the row Column, after the title/badge Row:
upload.bmiLabel?.let {
    Text(it, style = AlTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
        color = AlTheme.colors.textBase)
}
upload.alerts.forEach { alert ->
    Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs)) {
        AlPill(text = alert.label, tone = alert.tone)
        Text(alert.detail, style = AlTheme.typography.caption, color = AlTheme.colors.textMuted)
    }
}
// then the same Reintentar/Eliminar Row (canRetry/canDelete) + thin divider as subjects
```
A shared `EmptyState(title, caption)` is extracted (used by both segments — see Phase 4).

#### 6. Content root — `.../uploads/MeasurementUploadsContentRoot.kt` (new)
```kotlin
@Composable
fun MeasurementUploadsContentRoot(
    viewModel: MeasurementUploadsViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MeasurementUploadsContent(state = state, onAction = viewModel::onAction, modifier = modifier)
}
```

#### 7. DI — `di/ViewModelsModule.kt`
```kotlin
viewModelOf(::MeasurementUploadsViewModel)
```

### Success Criteria
#### Automated Verification:
- [x] Common compiles: `./scripts/compile.sh`
- [x] Android assembles: `./gradlew :composeApp:assembleDebug --no-daemon`
#### Manual Verification: not yet reachable (wired in Phase 5).

---

## Phase 4: Refactor subjects body + build the History hub

### Overview
Extract the subjects list into a header-less reusable body, factor the shared
`EmptyState`, and build the hub that hosts both segment bodies under one
`AlScaffold` + shared header (title + `AlSegmentedControl` + single "Sincronizar").

### Changes Required

#### 1. Shared empty state — `core/design_system/components/EmptyState.kt` (new, or `presentation/common/`)
Extract the centered title+caption block from `SubjectUploadsScreen.kt:179-202` into
`EmptyState(title: String, caption: String)`. Both segment bodies reuse it.

#### 2. Refactor `presentation/feature/subject/uploads/SubjectUploadsScreen.kt`
- **Extract** `SubjectUploadsContent(state, onAction, modifier)` = the current
  `when { isEmpty -> EmptyState() else -> LazyColumn { SubjectUploadRow } }` body, with
  an added `state.isLoading` spinner branch (parity with measurements). Keep
  `SubjectUploadRow` private here.
- **Add** `SubjectUploadsContentRoot(viewModel = koinViewModel(), modifier)` that
  collects state and renders `SubjectUploadsContent`. (No destination effect — the body
  has no navigation.)
- **Remove** the `AlScaffold` + `Header` + `SubjectUploadsScreenRoot`/`SubjectUploadsScreen`
  (title/caption/"Sincronizar" move to the hub). `Header` is deleted; the standalone
  Root is replaced by the hub.
- **Optional cleanup:** `SubjectUploadsAction.SyncNow` is no longer dispatched (button
  moved to hub). Remove it from the action + the VM `when` to avoid dead code, and drop
  `SubjectUploadsDestination`/`SubjectUploadsNavigation` (already unused — `AuthedShell`
  inlined `Back`). Guard with `grep -rn "SubjectUploadsScreenRoot\|SubjectUploadsAction.SyncNow"`.

> The subjects `ViewModel`/`State`/`SubjectUploadUi` are otherwise untouched. The VM still
> requests an opportunistic sync in `init`.

#### 3. The hub — `presentation/feature/history/` (new)
**`HistorySegment.kt`**:
```kotlin
enum class HistorySegment(val label: String) { Subjects("Sujetos"), Measurements("Mediciones") }
```
**`HistoryHubScreen.kt`**:
```kotlin
@Composable
fun HistoryHubScreenRoot(coordinator: SyncCoordinator = koinInject()) {
    var segment by rememberSaveable { mutableStateOf(HistorySegment.Subjects) }
    HistoryHubScreen(
        segment = segment,
        onSegmentChange = { segment = it },
        onSyncNow = { coordinator.requestSync() },
    )
}

@Composable
private fun HistoryHubScreen(
    segment: HistorySegment,
    onSegmentChange: (HistorySegment) -> Unit,
    onSyncNow: () -> Unit,
) {
    AlScaffold {
        Column(Modifier.fillMaxSize()) {
            HubHeader(segment, onSegmentChange, onSyncNow)
            when (segment) {
                HistorySegment.Subjects     -> SubjectUploadsContentRoot(Modifier.fillMaxSize())
                HistorySegment.Measurements -> MeasurementUploadsContentRoot(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun HubHeader(
    segment: HistorySegment,
    onSegmentChange: (HistorySegment) -> Unit,
    onSyncNow: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(AlTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Historial", style = AlTheme.typography.title, color = AlTheme.colors.textBase)
            AlButton(onClick = onSyncNow, variant = AlButtonVariant.Outline) { Text("Sincronizar") }
        }
        AlSegmentedControl(
            segments = HistorySegment.entries.map { it.label },
            selectedIndex = segment.ordinal,
            onSelect = { onSegmentChange(HistorySegment.entries[it]) },
        )
    }
}
```
> The hub injecting `SyncCoordinator` via `koinInject()` matches `AuthedShell.SyncTriggers`.
> `coordinator.requestSync()` syncs **all** engines (subjects + measurements) in one pass,
> so a single button serves both segments. Both `*ContentRoot`s create their VMs lazily
> in the active branch; once created, each VM is retained by the tab's back-stack
> `ViewModelStore`, so toggling segments preserves each list's observed state.

### Success Criteria
#### Automated Verification:
- [x] Common compiles: `./scripts/compile.sh`
- [x] No dangling refs: `grep -rn "SubjectUploadsScreenRoot" composeApp/src` returns nothing.
#### Manual Verification: reachable after Phase 5.

---

## Phase 5: Rewire the tab to the hub

### Overview
Point the bottom-nav "Historial" tab at the hub. Rename the route/tab from the
subjects-specific name to a neutral `History` so the combined hub reads correctly.

### Changes Required

#### 1. Route — `presentation/navigation/Route.kt`
Rename `SubjectUploads` → `History`:
```kotlin
@Serializable @SerialName("history") data object History : Route()
```

#### 2. Bottom-nav tab — `core/design_system/components/navbar/AlBottomNavBar.kt`
Rename the enum entry (keep the "Historial" label and the existing upload icon, or swap
to a history-flavored MingCute icon via the `add-mingcute-icon` skill if preferred):
```kotlin
History("Historial", Res.drawable.ic_mingcute_upload_line),
```

#### 3. `app/AuthedShell.kt`
- `selectedTab`: `hasRoute<Route.History>()` → `AlBottomNavTab.History`.
- bottom-bar mapping: `AlBottomNavTab.History -> Route.History`.
- Replace the `composable<Route.SubjectUploads> { SubjectUploadsScreenRoot(...) }` block
  with:
```kotlin
composable<Route.History> { HistoryHubScreenRoot() }
```
- Drop the now-unused `SubjectUploadsDestination`/`SubjectUploadsScreenRoot` imports.

> Tab save/restore behavior is unchanged; the hub's `rememberSaveable` segment + both
> child VMs ride the existing `saveState`/`restoreState` back-stack entry.

#### 4. Sweep references
`grep -rn "SubjectUploads\b\|AlBottomNavTab.Uploads" composeApp/src` → update any
stragglers (there should be only the ones above).

### Success Criteria
#### Automated Verification:
- [x] Common compiles: `./scripts/compile.sh`
- [x] Android assembles: `./gradlew :composeApp:assembleDebug --no-daemon` (+ iOS metadata: `compileKotlinIosSimulatorArm64`)
- [x] No stale names: `grep -rn "Route.SubjectUploads\|AlBottomNavTab.Uploads" composeApp/src` returns nothing.
#### Manual Verification:
- [ ] "Historial" tab opens the hub; **Sujetos** segment shows the unchanged subjects list.
- [ ] **Mediciones** segment lists measurements newest-first with the right badges.
- [ ] **Sincronizado** rows show IMC + alert pills + detail; **Error** rows show the message.
- [ ] **Reintentar** (Error) re-queues and the row moves to Pendiente→Sincronizando; **Eliminar**
      removes Pendiente/Error/Sincronizado rows.
- [ ] "Sincronizar" triggers a manual pass (both queues).
- [ ] Switching segments preserves each list's scroll/state; empty + loading states look right.
- [ ] iOS + Android parity.

---

## Phase 6: Tests

### Overview
Focused unit tests for the mapping seam and the ViewModel, mirroring the subjects tests.

### Changes Required (`commonTest`)
- **`MeasurementUploadUiTest`** (pure mapping, no coroutines): for each `SyncState`,
  assert `badgeLabel`/`badgeTone`, `showSpinner`, `canRetry`, `canDelete`; a `Synced` row
  with `bmi` + alerts populates `bmiLabel` + `alerts` (and a non-synced row does **not**,
  even if it somehow has them); `ClinicalAlert.toUi()` severity→tone mapping
  (critical→Danger, moderate→Warning, unknown→Neutral); subtitle contains DNI/kg/cm/date
  and appends `errorMessage` only on `Failed`; `formatMeasuredAt` handles a valid ISO
  string and falls back to the raw string on garbage.
- **`MeasurementUploadsViewModelTest`** (Turbine + fakes + `runTest`): initial
  `isLoading = true`; on first repo emission `isLoading=false` and rows map via `toUi`;
  `Retry` calls `repository.retry(id)` (assert via fake) and does not throw on
  `requestSync()`; `Delete` calls `repository.delete(id)`; ordering preserved (repo emits
  newest-first). Build the VM with a real `SyncCoordinator(engines = emptyList(),
  connectivity = FakeConnectivityObserver, scope = AppScope())` so `requestSync()` is a
  no-op harness (mirrors how the subjects VM consumes the concrete coordinator).
- **`FakeMeasurementRepository`** (new commonTest fake) implementing
  `MeasurementRepository`: a `MutableStateFlow<List<MeasurementUpload>>` for
  `observeUploads()`, recorded `retry`/`delete` calls, and no-op `enqueue`/SyncEngine
  members. Model on the subjects fake if one exists; otherwise on the testing-rules fake
  shape.
- (Optional) reuse/extend Historia 2's `DefaultMeasurementLocalStore` JVM test for the
  `selectAll ORDER BY createdAt DESC` ordering the history relies on (already covered if
  Historia 2 added it).

### Success Criteria
#### Automated Verification:
- [x] Tests pass: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:testDebugUnitTest --no-daemon`
- [x] Common compiles + Android assembles + iOS metadata.
#### Manual Verification:
- [ ] Crash recovery: kill the app mid-sync (row `SYNCING`) → on relaunch it is `PENDING`
      again (Historia 2 coordinator's `resetStuckSyncing`), and the history reflects it.
- [ ] GC: a long-synced row is eventually pruned (coordinator's hourly `pruneTerminal`),
      so the history doesn't grow unbounded.

---

## Testing Strategy

### Unit (automated)
The `toUi` mapping (badges, BMI/alert visibility by state, severity tones, subtitle,
date fallback) and the ViewModel (loading→data, retry/delete delegation, ordering) cover
the screen's logic without a network or real DB. Crash-recovery/GC are exercised by
Historia 2's coordinator tests; we add manual checks only.

### Manual (iOS + Android)
Phase 5–6 criteria exercise: list newest-first, all four badges, IMC + alerts on synced
rows, retry/delete eligibility, single "Sincronizar", segment-switch state retention,
loading/empty states, crash recovery, and GC — with the subjects segment unchanged.

## Performance Considerations
- Each segment body observes its DB via `asFlow().mapToList(...)`; recomposition is
  bounded by queue size (low-volume captures). Add a `LIMIT` to `selectAll` later if
  un-pruned `SYNCED` rows accumulate.
- Both VMs use `stateIn(WhileSubscribed(5_000))`, so the inactive segment's collection is
  cheap and pauses when not subscribed; the VM instances persist in the tab's
  `ViewModelStore`, so segment toggles don't re-create them.
- "Sincronizar" / retry route through the single-flight coordinator (`Mutex.tryLock`),
  so taps coalesce instead of stacking passes.

## Migration Notes
- **Hard dependency on Historia 2.** This plan does not compile until
  `MeasurementRepository`, `MeasurementUpload`, `ObserveMeasurementsUseCase`, and the
  generalized `SyncCoordinator` (Historia 2 Phase 1 `List<SyncEngine>`) exist. Land
  Historia 2 first, or implement both in one branch (Historia 2 phases, then these).
- **No data migration.** Read-only over the existing `PendingMeasurement` table.
- **Route rename is the only breaking change.** `Route.SubjectUploads → Route.History`
  and `AlBottomNavTab.Uploads → AlBottomNavTab.History` — both internal; the grep sweeps
  catch references. The `@SerialName` changes the serialized route key, which is fine for
  an in-memory nav graph (no persisted deep links today).
- **Reversible.** Revert by restoring the standalone `SubjectUploadsScreenRoot` + the
  `composable<Route.SubjectUploads>` mapping and removing `presentation/feature/history/`,
  `presentation/feature/measurement/uploads/`, `AlSegmentedControl`, the two use cases,
  and the VM DI entry.

## Risks
- **Historia 2 contract drift.** Severity strings and the alert shape are from the
  invented Historia 2 contract; the `severity → tone` map degrades unknown values to
  `Neutral` rather than failing, and `formatMeasuredAt` falls back to the raw string —
  so a contract surprise shows a plain pill / raw date, never a crash.
- **Two segments, one tab — discoverability.** Users must notice the segmented control to
  find measurements. Mitigated by placing it prominently in the header; revisit if
  feedback shows it's missed (the "pushed route" / "dedicated tab" alternatives remain
  available without touching the data layer).
- **PII at rest unencrypted** (subject name/DNI denormalized on the measurement row) —
  same posture as the subjects queue; flagged for an encryption follow-up.

## References
- Story: `thoughts/stories/create_measurement_screen.md` (Historia 3)
- **Hard dependency (write side + queue + coordinator):**
  `thoughts/plans/create_measurement_screen.md` (Historia 2) — `MeasurementRepository`,
  `MeasurementUpload`/`ClinicalAlert`, `ObserveMeasurementsUseCase`, generalized
  `SyncCoordinator`/`SyncEngine`.
- Subject selector dependency (for Historia 2): `thoughts/plans/select_subject_screen.md`.
- **Template to mirror (shipped):** `presentation/feature/subject/uploads/*`
  (`SubjectUploadsScreen.kt:54-202`, `SubjectUploadsViewModel.kt`, `SubjectUploadUi.kt:19-53`),
  `domain/usecase/subject/{Retry,Delete}SubjectUploadUseCase.kt`,
  `thoughts/plans/subjects_offline_first_sync.md`.
- Tab/nav wiring: `app/AuthedShell.kt:44-117`, `presentation/navigation/Route.kt:28-30`,
  `core/design_system/components/navbar/AlBottomNavBar.kt:29-75`.
- Design-system primitives: `core/design_system/components/{badge/AlStatusBadge.kt,
  pill/AlPill.kt,button/AlButton.kt,scaffold/AlScaffold.kt}`; theme tokens via `AlTheme`.
- Coordinator injection precedent: `app/AuthedShell.kt:126` (`SyncTriggers` `koinInject`).
- Datetime: `kotlinx-datetime 0.7.1` (`Instant.parse(...).toLocalDateTime(...)`).
</content>
</invoke>
