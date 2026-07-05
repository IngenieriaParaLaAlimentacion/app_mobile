# Home "Current Establishment" Implementation Plan

## Overview

Let a field worker pick a **current establishment** from Home so it pre-fills the establishment
field on every subsequent measurement upload, instead of picking it per submission. The Measurement
screen keeps its own picker as a one-off override — it never changes the Home default.

## Current State Analysis

- `MeasurementViewModel.loadEstablishments()` (`.../measurement/create_measurement/MeasurementViewModel.kt:106`)
  fetches approved establishments every time the screen opens and auto-selects only when there is
  exactly one (`approved.singleOrNull() ?: it.selectedEstablishment`). With ≥2 approved
  establishments, the field worker must pick manually on every single upload — this is the pain
  point the feature addresses.
- Establishments already have a full read-through cache: `EstablishmentWithStatus` /
  `CachedEstablishment` table, `EstablishmentLocalStore` / `DefaultEstablishmentLocalStore`,
  cache-first in `ProfileRepositoryImpl.getMyEstablishments()`, warmed at `AuthedShell` entry via
  `RefreshMyEstablishmentsUseCase`. This plan adds no new establishment data source.
- Home (`HomeState`/`HomeViewModel`/`HomeScreen`) has no establishment concept at all today.
- The project has no key-value/settings primitive — every user-scoped local value lives in
  SQLDelight and is wiped centrally by `SqlDelightDatabaseCleaner.clearAll()`. Schema is currently
  at v4 (`1.sqm`/`2.sqm`/`3.sqm` exist; latest snapshot is `databases/4.db`); a new migration is
  `4.sqm` → regenerated `5.db` (see `sqldelight-migration-workflow` memory: any `.sq` change without
  a matching `.sqm` fails the build under `verifyMigrations`).
- No shared establishment-picker composable exists — `MeasurementScreen.kt:122-143` hand-rolls a
  `ModalBottomSheet` + `AlListRow`-per-item sheet. There's no cross-feature "shared composables"
  convention in this codebase (`presentation/` only has per-feature folders), so this plan
  duplicates that small, stable pattern in Home rather than inventing a new shared-component seam
  for two call sites.

## Desired End State

- Home shows an always-visible "Establecimiento actual" field. Tapping it opens a picker (identical
  interaction to Measurement's) listing the worker's approved establishments; picking one persists
  it locally and updates the Home display immediately.
- The persisted choice survives app restarts and is wiped on explicit sign-out (not on the
  forced-refresh-failure partial logout, which today only drops the establishment cache).
- Opening the Measurement screen pre-fills `selectedEstablishment` from the current establishment
  (when set and still approved); the field worker can still change it there for a single upload
  without affecting the Home default. With zero current establishment, Measurement's existing
  auto-select-when-single / manual-pick-when-multiple behavior is unchanged.
- A current establishment that's no longer in the approved list (revoked, deleted) silently resolves
  to "unset" everywhere it's read — no error banners.

**Verification:** compile + full unit test suite pass (see per-phase success criteria), plus the
manual walkthroughs in Testing Strategy below.

### Key Discoveries
- `HomeViewModel.init` already merges reactive queue/connectivity state with once-loaded identity
  fields (`getMyProfile`) via `derived.copy(destination = ..., greetingName = ..., roleLabel = ...)`
  (`HomeViewModel.kt:44-50`) — the new establishment fields need the same "preserve across
  re-derive" treatment or they'll be wiped back to defaults on every queue/connectivity emission.
- `MeasurementViewModelTest.kt` and `ProfileUseCasesTest.kt` (package
  `ar.com.ingenieriaalimentacion.domain.usecase`) establish the fake/test conventions to follow:
  hand-written fakes (`FakeProfileRepository`), no mocking framework, use cases tested by wrapping a
  fake repository directly.
- `AppViewModel.kt:36-43`: explicit sign-out calls `clearLocalData()` (full `DatabaseCleaner.clearAll()`
  wipe); a forced (refresh-failure) logout only calls `clearEstablishmentCache()`. The new table is
  wired only into the former, matching how `PendingSubject`/`PendingMeasurement` are already handled.

## What We're NOT Doing

- No changes to subject creation — subjects are not establishment-scoped.
- No server sync of the current establishment — it's a local-only UX default, never POSTed.
- No new shared/cross-feature picker composable — the bottom-sheet picker is duplicated between
  Home and Measurement (see Current State Analysis).
- No live/reactive propagation from Home's pick into an already-open Measurement screen — the only
  entry point to Measurement is from Home today, so by the time the worker navigates there, Home has
  already persisted the choice.
- No warning UI for a stale/revoked current establishment — it silently resolves to null (ADR-014).

## Implementation Approach

Bottom-up, matching `thoughts/grilling/current-establishment-synthesis.md`'s build order: local
store → repository/use cases → Home UI → Measurement pre-fill. Each phase compiles and is testable
independently; nothing in the earlier phases depends on the later ones.

---

## Phase 1: Schema + Local Store

### Overview
Add the persisted single-row table for the current establishment id, wire it into the sign-out
cleaner, and add a small store class mirroring `EstablishmentLocalStore`/`DefaultEstablishmentLocalStore`.

### Changes Required:

#### 1. New SQLDelight table
**File**: `composeApp/src/commonMain/sqldelight/ar/com/ingenieriaalimentacion/data/db/CurrentEstablishment.sq` (new)
```sql
-- Single-row store for the Home "current establishment" pick (id only; display fields are
-- resolved against the CachedEstablishment/EstablishmentWithStatus list at read time).
CREATE TABLE CurrentEstablishment (
    singleton       INTEGER NOT NULL PRIMARY KEY,
    establishmentId TEXT,
    updatedAt       INTEGER NOT NULL
);

select:
SELECT establishmentId FROM CurrentEstablishment WHERE singleton = 0;

set:
INSERT OR REPLACE INTO CurrentEstablishment(singleton, establishmentId, updatedAt)
VALUES (0, ?, ?);
```

#### 2. Migration
**File**: `composeApp/src/commonMain/sqldelight/ar/com/ingenieriaalimentacion/data/db/4.sqm` (new)
```sql
-- v4 -> v5: adds CurrentEstablishment, a single-row store for the Home "current establishment" pick.
CREATE TABLE IF NOT EXISTS CurrentEstablishment (
    singleton       INTEGER NOT NULL PRIMARY KEY,
    establishmentId TEXT,
    updatedAt       INTEGER NOT NULL
);
```
Then regenerate the schema snapshot (writes `databases/5.db`):
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:generateCommonMainAppDatabaseSchema --no-daemon
```
Commit `4.sqm` and the new `5.db` together.

#### 3. Local store
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/profile/local/CurrentEstablishmentLocalStore.kt` (new)
```kotlin
package ar.com.ingenieriaalimentacion.data.profile.local

/**
 * Local single-value store for the Home "current establishment" pick. Stores only the id; display
 * fields are resolved against the establishment list at read time (see `GetCurrentEstablishmentUseCase`).
 */
interface CurrentEstablishmentLocalStore {
    suspend fun get(): String?
    suspend fun set(establishmentId: String?)
}
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/profile/local/DefaultCurrentEstablishmentLocalStore.kt` (new)
```kotlin
package ar.com.ingenieriaalimentacion.data.profile.local

import ar.com.ingenieriaalimentacion.data.db.CurrentEstablishmentQueries
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultCurrentEstablishmentLocalStore(
    private val queries: CurrentEstablishmentQueries,
    private val now: () -> Long,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CurrentEstablishmentLocalStore {

    override suspend fun get(): String? = withContext(dispatcher) {
        queries.select().executeAsOneOrNull()
    }

    override suspend fun set(establishmentId: String?): Unit = withContext(dispatcher) {
        queries.set(establishmentId, now())
    }
}
```

#### 4. Wire into the sign-out cleaner
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/db/SqlDelightDatabaseCleaner.kt`
**Changes**: add `database.currentEstablishmentQueries.deleteAll()` inside the existing transaction
(matches how `pendingSubjectQueries`/`pendingMeasurementQueries` are wiped directly, not through
their store abstractions).

#### 5. DI wiring
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/DatabaseModule.kt`
**Changes**: add `single { get<AppDatabase>().currentEstablishmentQueries }` alongside the other
`*Queries` singles.

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/RepositoriesModule.kt`
**Changes**: add
```kotlin
single<CurrentEstablishmentLocalStore> {
    DefaultCurrentEstablishmentLocalStore(queries = get(), now = { Clock.System.now().toEpochMilliseconds() })
}
```

#### 6. Tests
**File**: `composeApp/src/androidUnitTest/kotlin/ar/com/ingenieriaalimentacion/data/profile/local/DefaultCurrentEstablishmentLocalStoreTest.kt` (new)
- In-memory `JdbcSqliteDriver` + `AppDatabase.Schema.create`, mirroring
  `DefaultSubjectLocalStoreTest`/`DefaultMeasurementLocalStoreTest`.
- `get()` returns `null` before anything is set.
- `set("e1")` then `get()` returns `"e1"`.
- `set("e2")` after `set("e1")` overwrites (single-row upsert, not a second row).
- `set(null)` clears it back to `null`.

**File**: `composeApp/src/androidUnitTest/kotlin/ar/com/ingenieriaalimentacion/data/db/SqlDelightDatabaseCleanerTest.kt`
**Changes**: `seedAllTables()` also calls `database.currentEstablishmentQueries.set("est-1", 10L)`;
the `clearAll wipes every local table` test asserts `database.currentEstablishmentQueries.select().executeAsOneOrNull()` is `null` after `clearAll()`.

### Success Criteria:

#### Automated Verification:
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:generateCommonMainAppDatabaseSchema --no-daemon` succeeds and produces `databases/5.db`
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:compileCommonMainKotlinMetadata --no-daemon` passes (verifies migration + `verifyMigrations` guard)
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:testDebugUnitTest --no-daemon` passes, including the new store test and updated cleaner test (3 pre-existing unrelated failures on `main`: `EstablishmentRepositoryImplTest`, `MeasurementViewModelTest.enqueue success...`, `CreateSubjectViewModelTest.enqueue success...`)

#### Manual Verification:
- [ ] N/A — no UI in this phase

---

## Phase 2: Repository + Resolution Use Cases

### Overview
Expose the current-establishment id through `ProfileRepository` (alongside the existing
establishment methods) and add the two use cases: one that resolves a stored id against a caller-
supplied approved list (the single shared ADR-014 stale-resolution rule), one that sets it.

### Changes Required:

#### 1. Repository interface + impl
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/repository/ProfileRepository.kt`
**Changes**: add
```kotlin
/** The locally-remembered "current establishment" id Home sets, for pre-filling measurement uploads. */
suspend fun getCurrentEstablishmentId(): String?

/** Sets (or clears, with `null`) the current establishment id. */
suspend fun setCurrentEstablishmentId(establishmentId: String?)
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/profile/ProfileRepositoryImpl.kt`
**Changes**: constructor gains `private val currentEstablishment: CurrentEstablishmentLocalStore`;
implement the two new methods by delegating (`currentEstablishment.get()` / `.set(id)`).

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/RepositoriesModule.kt`
**Changes**: `ProfileRepositoryImpl(api = get(), local = get(), currentEstablishment = get())`.

#### 2. Use cases
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/usecase/profile/GetCurrentEstablishmentUseCase.kt` (new)
```kotlin
package ar.com.ingenieriaalimentacion.domain.usecase.profile

import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository

/**
 * Resolves the persisted current-establishment id against [approved] — the caller's own,
 * already-Approved-filtered establishment list. An id no longer present there (revoked, deleted)
 * silently resolves to `null`; there's nothing else to check, this is the whole ADR-014 rule.
 */
class GetCurrentEstablishmentUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(approved: List<EstablishmentWithStatus>): EstablishmentWithStatus? {
        val currentId = repository.getCurrentEstablishmentId() ?: return null
        return approved.firstOrNull { it.id == currentId }
    }
}
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/usecase/profile/SetCurrentEstablishmentUseCase.kt` (new)
```kotlin
package ar.com.ingenieriaalimentacion.domain.usecase.profile

import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository

class SetCurrentEstablishmentUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(establishment: EstablishmentWithStatus) {
        repository.setCurrentEstablishmentId(establishment.id)
    }
}
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/UseCasesModule.kt`
**Changes**: add `factory { GetCurrentEstablishmentUseCase(get()) }` and
`factory { SetCurrentEstablishmentUseCase(get()) }`.

#### 3. Test fake
**File**: `composeApp/src/commonTest/kotlin/ar/com/ingenieriaalimentacion/fake/FakeProfileRepository.kt`
**Changes**: add `var currentEstablishmentId: String? = null` and implement the two new interface
methods against it (`setCurrentEstablishmentId` also records a call count, matching the existing
`updateCount`/`refreshCount` style).

#### 4. Tests
**File**: `composeApp/src/commonTest/kotlin/ar/com/ingenieriaalimentacion/domain/usecase/ProfileUseCasesTest.kt`
**Changes**: add cases (same file/package as the other thin profile use-case tests):
- `GetCurrentEstablishment returns null when nothing is stored`
- `GetCurrentEstablishment returns the matching approved establishment`
- `GetCurrentEstablishment returns null when the stored id is no longer approved` (ADR-014 — the
  core behavior worth locking down)
- `SetCurrentEstablishment forwards the establishment's id`

### Success Criteria:

#### Automated Verification:
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:compileCommonMainKotlinMetadata --no-daemon` passes
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:testDebugUnitTest --no-daemon` passes, including the new `ProfileUseCasesTest` cases (same 3 pre-existing unrelated failures)

#### Manual Verification:
- [ ] N/A — no UI in this phase

---

## Phase 3: Home UI

### Overview
Home gets the always-visible current-establishment affordance (ADR-015): loads the approved list +
resolved current establishment once, lets the worker open a picker and select, persists the choice.

### Changes Required:

#### 1. State + actions
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/home/HomeState.kt`
**Changes**: add to `HomeState`:
```kotlin
val currentEstablishment: EstablishmentWithStatus? = null,
val establishmentOptions: List<EstablishmentWithStatus> = emptyList(),
val showEstablishmentPicker: Boolean = false,
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/home/HomeAction.kt`
**Changes**: add
```kotlin
data object EstablishmentPickerOpen : HomeAction
data object EstablishmentPickerDismiss : HomeAction
data class EstablishmentSelected(val establishment: EstablishmentWithStatus) : HomeAction
```

#### 2. ViewModel
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/home/HomeViewModel.kt`
**Changes**:
- Constructor gains `getMyEstablishments: GetMyEstablishmentsUseCase`,
  `getCurrentEstablishment: GetCurrentEstablishmentUseCase`,
  `setCurrentEstablishment: SetCurrentEstablishmentUseCase`.
- `init` gains a `loadEstablishments()` call (own `viewModelScope.launch`, independent of the
  identity/profile load) that fetches + filters to Approved, resolves the current establishment
  against that list, and updates state:
```kotlin
private fun loadEstablishments() {
    viewModelScope.launch {
        getMyEstablishments().onSuccess { all ->
            val approved = all.filter { it.status == EstablishmentStatus.Approved }
            val current = getCurrentEstablishment(approved)
            _state.update { it.copy(establishmentOptions = approved, currentEstablishment = current) }
        }
        // Failure: leave establishmentOptions/currentEstablishment at their defaults — Home never
        // blocks on this (ADR-015); the picker just shows nothing to pick yet.
    }
}
```
- The reactive `combine(...).collect { derived -> ... }` merge (`HomeViewModel.kt:44-50`) must
  additionally preserve the establishment fields, or every queue/connectivity emission resets them:
```kotlin
_state.update {
    derived.copy(
        destination = it.destination,
        greetingName = it.greetingName,
        roleLabel = it.roleLabel,
        currentEstablishment = it.currentEstablishment,
        establishmentOptions = it.establishmentOptions,
        showEstablishmentPicker = it.showEstablishmentPicker,
    )
}
```
- `onAction` gains:
```kotlin
HomeAction.EstablishmentPickerOpen -> _state.update { it.copy(showEstablishmentPicker = true) }
HomeAction.EstablishmentPickerDismiss -> _state.update { it.copy(showEstablishmentPicker = false) }
is HomeAction.EstablishmentSelected -> {
    _state.update { it.copy(currentEstablishment = action.establishment, showEstablishmentPicker = false) }
    viewModelScope.launch { setCurrentEstablishment(action.establishment) }
}
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/ViewModelsModule.kt`
**Changes**: extend the explicit `HomeViewModel { ... }` block with
`getMyEstablishments = get(), getCurrentEstablishment = get(), setCurrentEstablishment = get(),`.

#### 3. Screen
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/home/HomeScreen.kt`
**Changes**: render an `AlSelectField` (same component Measurement uses) right after
`AlScreenHeader`, before `SyncStatusCard`:
```kotlin
AlSelectField(
    label = "Establecimiento actual",
    value = state.currentEstablishment?.name ?: "",
    placeholder = "Elegí el establecimiento",
    enabled = state.establishmentOptions.isNotEmpty(),
    onClick = { onAction(HomeAction.EstablishmentPickerOpen) },
)
```
Add the same `ModalBottomSheet` + `AlListRow`-per-item picker `MeasurementScreen.kt:122-143` uses
(new `rememberModalBottomSheetState()`, `@OptIn(ExperimentalMaterial3Api::class)`), driven by
`state.showEstablishmentPicker` / `state.establishmentOptions`, dispatching
`HomeAction.EstablishmentPickerDismiss` / `HomeAction.EstablishmentSelected`.

#### 4. Tests
**File**: `composeApp/src/commonTest/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/home/HomeViewModelTest.kt` (new)
Following `MeasurementViewModelTest`'s fake+Turbine style:
- Loads approved establishments and resolves a previously-set current establishment on init.
- A stored current-establishment id that's no longer in the approved list resolves to `null`
  (state shows nothing selected) — the Home-level manifestation of ADR-014.
- `EstablishmentSelected` updates `currentEstablishment` immediately and calls
  `setCurrentEstablishmentId` on the fake repository exactly once.
- A subject/measurement-queue emission (via the existing `combine`) after a selection does not
  reset `currentEstablishment`/`establishmentOptions`/`showEstablishmentPicker` back to defaults.

### Success Criteria:

#### Automated Verification:
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:compileCommonMainKotlinMetadata --no-daemon` passes
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:testDebugUnitTest --no-daemon` passes, including the new `HomeViewModelTest` (same 3 pre-existing unrelated failures)
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:assembleDebug --no-daemon` succeeds

#### Manual Verification:
- [ ] Home shows "Establecimiento actual" for a worker with a single approved establishment (field
      is populated, tappable, picker lists that one option)
- [ ] Home shows it for a worker with multiple approved establishments; picking one updates the
      field immediately and persists across an app restart
- [ ] Field is disabled/shows empty for a worker with zero approved establishments
- [ ] Switching the current establishment mid-session updates Home without a full reload glitching
      the sync-status card or recent-activity list

---

## Phase 4: Measurement Pre-fill

### Overview
`MeasurementViewModel` reads the resolved current establishment and lets it win over the existing
`singleOrNull()` auto-select — everything else (picker UI, one-off override, submit validation)
stays as-is.

### Changes Required:

#### 1. ViewModel
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/measurement/create_measurement/MeasurementViewModel.kt`
**Changes**: constructor gains `private val getCurrentEstablishment: GetCurrentEstablishmentUseCase`;
`loadEstablishments()` (`MeasurementViewModel.kt:106-131`) resolves it and splices it into the
precedence chain ahead of `singleOrNull()`, per ADR-012:
```kotlin
private fun loadEstablishments() {
    _state.update { it.copy(isLoadingProfile = true, profileError = null) }
    viewModelScope.launch {
        getMyEstablishments().fold(
            onSuccess = { establishments ->
                val approved = establishments.filter { it.status == EstablishmentStatus.Approved }
                val current = getCurrentEstablishment(approved)
                _state.update {
                    it.copy(
                        isLoadingProfile = false,
                        establishmentOptions = approved,
                        // Current establishment (Home's default) wins over auto-select-when-single;
                        // a manual pick already in state (e.g. a RetryProfile re-load) is untouched.
                        selectedEstablishment = current ?: approved.singleOrNull() ?: it.selectedEstablishment,
                    )
                }
            },
            onFailure = {
                _state.update {
                    it.copy(
                        isLoadingProfile = false,
                        profileError = "No se pudieron cargar los establecimientos. Intentá nuevamente.",
                    )
                }
            },
        )
    }
}
```
No changes to `MeasurementState`, `MeasurementAction`, or `MeasurementScreen` — the existing
`EstablishmentSelected` handler (`MeasurementViewModel.kt:79-87`) already just updates local state
and must **not** gain a call to `setCurrentEstablishment` (that's the ADR-012 one-off-override rule).
`viewModelOf(::MeasurementViewModel)` in `ViewModelsModule.kt` auto-wires the new constructor param
via Koin — no DI file change needed there.

#### 2. Tests
**File**: `composeApp/src/commonTest/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/measurement/create_measurement/MeasurementViewModelTest.kt`
**Changes**: update the `viewModel(...)` test helper to pass a `GetCurrentEstablishmentUseCase`
wrapping the same `FakeProfileRepository`, and add cases:
- A current establishment set (and approved) pre-fills `selectedEstablishment`, even with 2+
  approved options (overriding the "not auto-selected" case from
  `two approved establishments are not auto-selected`).
- A current establishment id that's stale (not in the approved list) falls back to today's
  behavior (`singleOrNull()`, or nothing selected with 2+ options).
- Manually overriding via `EstablishmentSelected` in the Measurement screen does **not** call
  `setCurrentEstablishmentId` on the fake repository (asserts the one-off-override guarantee from
  ADR-012 — the count stays 0).

### Success Criteria:

#### Automated Verification:
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:compileCommonMainKotlinMetadata --no-daemon` passes
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:testDebugUnitTest --no-daemon` passes, including updated `MeasurementViewModelTest` (same 3 pre-existing unrelated failures)
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:assembleDebug --no-daemon` succeeds

#### Manual Verification:
- [ ] With a current establishment set on Home, opening "Nueva medición" shows it pre-filled
- [ ] Changing the establishment inside Measurement and submitting does not change Home's current
      establishment (re-open Home, or re-open Measurement, and confirm it's back to the original)
- [ ] With no current establishment set, Measurement behaves exactly as before (auto-select when
      exactly one approved establishment, manual pick otherwise)
- [ ] Revoking/losing access to the current establishment (or simulating a stale id) falls back
      gracefully — Home shows nothing selected, Measurement falls back to its existing behavior

---

## Testing Strategy

### Manual Testing Steps:
1. Fresh install, single approved establishment: confirm Home auto-shows it, Measurement pre-fills it.
2. Fresh install, multiple approved establishments: pick one on Home, confirm Measurement pre-fills
   it; kill and relaunch the app, confirm it's still set.
3. On Measurement, override to a different establishment for one submission; confirm Home's current
   establishment is unchanged afterward.
4. Sign out (explicit): confirm the current establishment is cleared (Home shows the empty picker
   again after next sign-in as a different/same user).
5. Force a refresh-failure logout (if reproducible in a dev build) and confirm un-synced
   subjects/measurements survive while re-authenticating — verifies the new table wasn't
   accidentally wired into the partial-clear path.

## Performance Considerations

None beyond existing establishment-cache reads (already warmed at shell entry); the new table is a
single row, read/written on the main establishment-load path only.

## Migration Notes

Schema v4 → v5 via `4.sqm` (append-only, `CREATE TABLE IF NOT EXISTS`); regenerate and commit
`databases/5.db` alongside it. Never edit or delete `1.sqm`/`2.sqm`/`3.sqm` or their `.db` snapshots.

## References

- Grilling synthesis: `thoughts/grilling/current-establishment-synthesis.md`
- ADRs: `thoughts/grilling/adr-012-current-establishment-prefill-not-gate.md`,
  `adr-013-current-establishment-local-store.md`, `adr-014-current-establishment-stale-resolution.md`,
  `adr-015-current-establishment-home-ux.md`
- Glossary: `thoughts/grilling/current-establishment-glossary.md`
- Existing establishment cache precedent: `EstablishmentLocalStore.kt`,
  `DefaultEstablishmentLocalStore.kt`, `ProfileRepositoryImpl.kt`
- Existing picker UI to mirror: `MeasurementScreen.kt:122-143`
- Migration workflow: `sqldelight-migration-workflow` memory
