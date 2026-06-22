# Cargar Medición Antropométrica (Historia 2) — Implementation Plan

## Overview

Build the **measurement capture screen**: a relevador, entering with a subject
already chosen (Historia 1), records **weight (kg)**, **height (cm)** and a
measurement **date**, picks the **establishment**, and saves. Saving is
**offline-first**: a client UUID is generated, a `PENDING` row is written to a new
local SQLDelight queue **instantly**, the form confirms without waiting for the
network, and a background sync engine pushes it to `POST /api/v1/measurements` with
retry/backoff. The success response carries server-computed `bmi` and clinical
`alerts[]`, which are persisted onto the local row so the history (Historia 3) can
show them once the row syncs.

This mirrors the **already-shipped subjects offline-first stack**
(`thoughts/plans/subjects_offline_first_sync.md`) on a **separate table and
repository** — the two queues never mix. It also reuses the **subject selector**
from Historia 1 (`thoughts/plans/select_subject_screen.md`) as the subject source.

Bottom-up, each phase compiles before the next.

## Current State Analysis

- **The subjects offline-first machinery is fully built and is the template.** All of
  `SyncState`, `SyncSummary`, `IdGenerator`/`UuidGenerator`, `ConnectivityObserver`
  (3 files), `AppScope`, the SQLDelight `AppDatabase` + `DatabaseDriverFactory`
  plumbing, `DefaultSubjectLocalStore`, `SubjectRepositoryImpl` (sync engine),
  `SubjectUploadOutcome`, `SubjectApi` (status-branching), `SyncCoordinator`, and the
  `create_subject` + `uploads` feature bundles exist and pass tests. The measurement
  queue is a **parallel copy** of this template.
- **Reusable as-is (no copy):** `domain/model/subject/SyncState.kt` (`Pending,
  Syncing, Synced, Merged, Failed` — generic), `SyncSummary.kt`, `domain/util/
  IdGenerator.kt` + `UuidGenerator`, `data/network/connectivity/*` (3 files),
  `di/AppScope.kt`, the SQLDelight `AppDatabase` (a second `.sq` file just adds a
  second generated `*Queries` accessor — no `databases {}` change), and
  `DatabaseDriverFactory` (same physical `ingenieria_alimentacion.db`).
- **No measurement endpoint exists.** `docs/backend.openapi.generated.json` has no
  `/measurements`, no `bmi`, no `alerts`. The contract is **invented here** per the
  story (verify against the backend before shipping; `ApiConfig.BASE_URL` is still a
  placeholder). The success response's `bmi` + `alerts[]` are the new wrinkle vs
  subjects (subjects store only a `canonicalId`).
- **`SyncCoordinator` is hard-typed to `SubjectRepository`** (`data/subject/sync/
  SyncCoordinator.kt`, bound in `di/DatabaseModule.kt:12`, started in
  `app/AppViewModel.kt:30`). To drive a second queue it must be generalized (Phase 1).
- **Establishments come from the profile.** `GetMyProfileUseCase` →
  `Profile.establishments: List<EstablishmentWithStatus>?` (each has `id, name,
  formattedAddress, …, status: EstablishmentStatus`). The measurement screen filters
  to `EstablishmentStatus.Approved`. The unauthenticated `GET /establishments`
  catalog is the **wrong** source (it's for registration).
- **No decimal range validator.** `core/validation/Rules.kt` has `digitsInRange`
  (integer only via `toIntOrNull`) and `matches` (regex). Decimal weight/height needs
  a new rule or an inline `Validator` lambda (the birthDate validator in
  `CreateSubjectViewModel.kt:179-190` is the precedent for inline lambdas).
- **No `ModalBottomSheet` is used anywhere**, but Material3 is on the classpath
  (`Switch`, `CircularProgressIndicator` are used). `AlSelectField`
  (`core/design_system/components/input/AlSelectField.kt`) is the read-only tappable
  "picker field" trigger.

### Key Discoveries (resolved decisions)

- **Subject entry — reuse Historia 1.** `SelectSubject` returns a
  `SubjectSummary(id, dni, name, lastName, sex)` JSON via the result key
  `SELECTED_SUBJECT_RESULT`, written to `previousBackStackEntry.savedStateHandle`
  (`select_subject/SelectSubjectNavigation.kt`). The Historia 1 plan explicitly says
  its provisional Home banner *"será reemplazado en Historia 2 por la pantalla de
  medición… usará el `previousBackStackEntry` simple, como Register↔
  SelectEstablishment."* So the **measurement screen lives on the outer nav and owns
  the selector** via that key. `SubjectSummary` carries everything the screen needs
  (id for the body; name/lastName/dni/sex for display) — **no `GET /subjects/{id}`
  detail fetch is needed** (alerts are computed server-side and returned in the
  measurement POST response, not derived from subject detail).
- **`measured_at` = capture instant, UTC with zone.** `kotlin.time.Clock.System
  .now().toString()` yields ISO-8601 with `Z` (e.g. `2026-06-21T18:30:00Z`). Stamped
  at enqueue from the repository's injected `now: () -> Long` via
  `Instant.fromEpochMilliseconds(now()).toString()` (deterministic in tests,
  consistent with how subjects stamp `createdAt`). **No date picker**; the field is
  display-only on the form (manual editing is out of scope per the story).
- **Establishment picker — inline, profile-sourced, auto-select-if-one.**
  `AlSelectField` trigger opens an inline single-select list of the profile's
  `Approved` establishments (Material3 `ModalBottomSheet`, or a custom selectable list
  modeled on `SexSelector`/`AlListRow` if a sheet is undesirable). If exactly one
  approved establishment exists, preselect it. Obligatory; a plain `String?` error
  (like `sexError`) is set on submit if none chosen.
- **Sync — generalize `SyncCoordinator` to a `List<SyncEngine>`.** `SubjectRepository`
  already declares `syncPending`/`resetStuckSyncing`/`pruneTerminal`; extracting a
  `SyncEngine` interface and letting the coordinator loop over a list keeps **one**
  coordinator and **one** set of triggers (`AppViewModel.init` + `AuthedShell
  .SyncTriggers`). Small, test-covered refactor.

## Desired End State

A signed-in relevador:

1. From Home taps **"Cargar medición"** → the measurement screen opens; on first
   entry (no subject yet) it opens the Historia 1 selector, and the chosen subject
   returns into the screen (name/apellido/DNI shown, with a **"Cambiar sujeto"**
   control that re-opens the selector).
2. Picks an **establishment** (auto-selected if only one approved), enters **peso**
   and **talla** (decimal, numeric keyboard, validated locally against range). The
   **measurement date** defaults to now (shown read-only). **Guardar** is disabled
   until subject + establishment + valid peso + valid talla are present.
3. Taps **Guardar** → the measurement is written locally **instantly** as `PENDING`
   (client UUID = idempotency key), a confirmation appears, peso/talla clear, the
   subject + establishment persist, and the relevador stays to load another. No
   network wait. `requestSync()` fires in the background.
4. The sync engine POSTs with `id = localId`. **201 and 200 (idempotent replay) are
   treated identically** → `SYNCED`, persisting `bmi` + `alerts[]`. `404`/`403`/`422`
   → terminal `FAILED` with a clear Spanish message. `401`/network/5xx → stay
   `PENDING` with exponential backoff, dead-lettering to `FAILED` after the cap.
   Errors are never silently dropped.

### Verification

- `./scripts/compile.sh` passes after every phase; `:composeApp:assembleDebug` after
  UI phases; `:composeApp:testDebugUnitTest` after the test phase.
- Manual: save offline → row `PENDING`; go online → `SYNCED` with `bmi`/alerts stored;
  force 422/403 → `FAILED` with message; kill mid-queue → row persists and resumes.

## What We're NOT Doing

- **No measurement history screen (Historia 3).** This plan builds the **write side**
  (form + queue) and persists `bmi`/`alerts`, but the history tab, status badges,
  retry/delete UI, "Sincronizar ahora", and GC display are Historia 3. The
  `MeasurementRepository.observeUploads()` Flow + local store are built here so
  Historia 3 can consume them.
- **No subject selector build** — Historia 1 owns it. This plan **depends on it** and
  replaces its provisional Home banner consumption.
- **No manual `measured_at` editing / date picker** — default-to-now, read-only.
- **No subject-detail fetch** (`GET /subjects/{id}`) — `SubjectSummary` is sufficient.
- **No backend coordination** — the contract is invented; sending `id`/handling
  200-as-success is additive and back-compatible. Verify field names with the backend.
- **No new offline-first primitives** — reuse `SyncState`, `IdGenerator`,
  `ConnectivityObserver`, `AppScope`, SQLDelight plumbing unchanged.
- **No clinical-alert UI styling work** — alerts are persisted as data now; rendering
  is Historia 3.

## Implementation Approach

Bottom-up. **Phase 1** generalizes the sync coordinator (isolated refactor; subjects
keep working). **Phases 2–4** are the non-UI measurement queue (table, domain, network,
repository/sync engine, DI), verifiable via `./scripts/compile.sh`. **Phase 5** is the
form UI + navigation + Home entry change. **Phase 6** adds focused tests for the
highest-risk logic (outcome mapping, state transitions incl. bmi/alerts persistence,
the decimal rule, ViewModel enqueue/validation).

Data flow mirrors subjects: **UI → local DB (instant)**, **sync engine: DB pending →
Ktor → DB result (with bmi/alerts)**, **UI observes DB via Flow**.

---

## Phase 1: Generalize SyncCoordinator to multiple engines

### Overview
Extract a `SyncEngine` interface; make `SyncCoordinator` drive a `List<SyncEngine>`
so subjects and measurements share one coordinator and one trigger set. No behavior
change for subjects.

### Changes Required

#### 1. `domain/sync/SyncEngine.kt` (new)
```kotlin
package ar.com.ingenieriaalimentacion.domain.sync

import ar.com.ingenieriaalimentacion.domain.model.subject.SyncSummary
import kotlin.time.Duration

/** A per-entity offline-first queue the SyncCoordinator can drive. */
interface SyncEngine {
    suspend fun syncPending(): SyncSummary
    suspend fun resetStuckSyncing()
    suspend fun pruneTerminal(olderThan: Duration)
}
```
> `SyncSummary` already lives under `domain/model/subject/`; leave it there (it is a
> generic counts holder) to avoid churn, or move to `domain/sync/` — either compiles.

#### 2. `SubjectRepository` extends `SyncEngine`
`domain/repository/SubjectRepository.kt`: `interface SubjectRepository : SyncEngine {
… }` — it already declares `syncPending`/`resetStuckSyncing`/`pruneTerminal`, so the
three move to the interface (or stay; both satisfy it). No impl change.

#### 3. `SyncCoordinator` drives a list
`data/subject/sync/SyncCoordinator.kt` (consider moving to `data/sync/` later):
```kotlin
class SyncCoordinator(
    private val engines: List<SyncEngine>,
    private val connectivity: ConnectivityObserver,
    private val scope: AppScope,
) {
    private val mutex = Mutex()
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            engines.forEach { runCatching { it.resetStuckSyncing() } }   // crash recovery, all queues
            connectivity.isOnline.collect { online -> if (online) requestSync() }
        }
        scope.launch {
            while (isActive) {
                delay(1.hours)
                engines.forEach { runCatching { it.pruneTerminal(7.days) } }
            }
        }
    }

    fun requestSync() {
        scope.launch {
            if (!mutex.tryLock()) return@launch          // single-flight across all engines
            try { engines.forEach { runCatching { it.syncPending() } } } finally { mutex.unlock() }
        }
    }
}
```

#### 4. DI — `di/DatabaseModule.kt`
```kotlin
single<List<SyncEngine>> { listOf(get<SubjectRepository>(), get<MeasurementRepository>()) }
single { SyncCoordinator(engines = get(), connectivity = get(), scope = get()) }
```
> `MeasurementRepository` is added in Phase 4; until then, list only the subject repo
> (or land Phase 1 with a single-element list and append in Phase 4).

No change to `AppViewModel.start()` or `AuthedShell.SyncTriggers` (they call
`start()`/`requestSync()`, signatures unchanged). `SubjectUploadsViewModel`'s
`syncCoordinator.requestSync()` is unaffected.

### Success Criteria
#### Automated Verification:
- [ ] Common compiles: `./scripts/compile.sh`
- [ ] Existing subject sync tests still pass: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:testDebugUnitTest --no-daemon`
#### Manual Verification:
- [ ] Subjects still sync (offline create → SYNCED) — no regression.

---

## Phase 2: Measurement local store + domain model

### Overview
New SQLDelight table `PendingMeasurement`, domain `MeasurementUpload` /
`NewMeasurement` / `ClinicalAlert`, the `MeasurementLocalStore` (interface + `Default*`),
and mappers. Reuse `SyncState`, `SyncSummary`, `MAX_SYNC_ATTEMPTS`.

### Changes Required

#### 1. Schema — `composeApp/src/commonMain/sqldelight/ar/com/ingenieriaalimentacion/data/db/PendingMeasurement.sq` (new)
```sql
CREATE TABLE PendingMeasurement (
    localId          TEXT    NOT NULL PRIMARY KEY,  -- v4 UUID; also the request `id`
    subjectId        TEXT    NOT NULL,              -- server UUID (from SubjectSummary)
    subjectName      TEXT    NOT NULL,              -- denormalized for history display
    subjectLastName  TEXT    NOT NULL,
    subjectDni       INTEGER NOT NULL,
    establishmentId  TEXT    NOT NULL,
    establishmentName TEXT   NOT NULL,              -- denormalized for history display
    weightKg         REAL    NOT NULL,
    heightCm         REAL    NOT NULL,
    measuredAt       TEXT    NOT NULL,              -- ISO 8601 UTC (…Z)
    syncState        TEXT    NOT NULL,              -- PENDING|SYNCING|SYNCED|FAILED
    errorMessage     TEXT,                          -- short Spanish reason when FAILED
    attemptCount     INTEGER NOT NULL DEFAULT 0,
    bmi              REAL,                           -- server-computed, set on SYNCED
    alertsJson       TEXT,                           -- serialized List<ClinicalAlert>, set on SYNCED
    createdAt        INTEGER NOT NULL,
    updatedAt        INTEGER NOT NULL
);

CREATE INDEX PendingMeasurement_state ON PendingMeasurement(syncState);

insert:
INSERT INTO PendingMeasurement(localId, subjectId, subjectName, subjectLastName, subjectDni,
    establishmentId, establishmentName, weightKg, heightCm, measuredAt, syncState, errorMessage,
    attemptCount, bmi, alertsJson, createdAt, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

selectAll:
SELECT * FROM PendingMeasurement ORDER BY createdAt DESC;

selectSyncable:                                     -- all PENDING; backoff gating applied in Kotlin
SELECT * FROM PendingMeasurement WHERE syncState = 'PENDING' ORDER BY createdAt ASC;

selectById:
SELECT * FROM PendingMeasurement WHERE localId = ?;

markState:
UPDATE PendingMeasurement SET syncState = ?, updatedAt = ? WHERE localId = ?;

markSynced:                                          -- 201/200: persist bmi + alerts, clear error
UPDATE PendingMeasurement SET syncState = 'SYNCED', bmi = ?, alertsJson = ?, errorMessage = NULL,
    updatedAt = ? WHERE localId = ?;

bumpRetry:                                           -- transient 401/network: stay PENDING, count attempt
UPDATE PendingMeasurement SET syncState = 'PENDING', attemptCount = attemptCount + 1,
    errorMessage = ?, updatedAt = ? WHERE localId = ?;

markFailed:                                          -- 404/403/422 or dead-letter
UPDATE PendingMeasurement SET syncState = 'FAILED', errorMessage = ?, attemptCount = attemptCount + 1,
    updatedAt = ? WHERE localId = ?;

resetForRetry:                                       -- manual Retry (Historia 3): fresh start
UPDATE PendingMeasurement SET syncState = 'PENDING', errorMessage = NULL, attemptCount = 0,
    updatedAt = ? WHERE localId = ?;

resetSyncingToPending:                               -- crash recovery on coordinator start
UPDATE PendingMeasurement SET syncState = 'PENDING', updatedAt = ? WHERE syncState = 'SYNCING';

pruneTerminalOlderThan:                              -- GC SYNCED rows older than cutoff
DELETE FROM PendingMeasurement WHERE syncState = 'SYNCED' AND updatedAt < :cutoff;

deleteById:
DELETE FROM PendingMeasurement WHERE localId = ?;
```
> `SyncState.Merged` is unused for measurements (no canonical dedup); the shared enum
> keeps the value, the queries just never produce it. Reusing the same `AppDatabase`
> means the generated `pendingMeasurementQueries` accessor appears automatically — no
> `composeApp/build.gradle.kts` change.

#### 2. Domain models
**`domain/model/measurement/ClinicalAlert.kt`** (new):
```kotlin
@Serializable
data class ClinicalAlert(val alertType: String, val severity: String, val detail: String)
```
**`domain/model/measurement/NewMeasurement.kt`** (new) — form output:
```kotlin
data class NewMeasurement(
    val subject: SubjectSummary,        // carries id + display fields
    val establishmentId: String,
    val establishmentName: String,
    val weightKg: Double,
    val heightCm: Double,
)
```
> `measured_at`, `localId`, and timestamps are stamped in the repository at enqueue
> (not by the VM), mirroring how subjects stamp `id`/`createdAt`.

**`domain/model/measurement/MeasurementUpload.kt`** (new) — queued row + sync metadata:
```kotlin
data class MeasurementUpload(
    val localId: String,
    val subjectId: String,
    val subjectName: String,
    val subjectLastName: String,
    val subjectDni: Int,
    val establishmentId: String,
    val establishmentName: String,
    val weightKg: Double,
    val heightCm: Double,
    val measuredAt: String,             // ISO 8601 UTC
    val syncState: SyncState,
    val errorMessage: String?,
    val attemptCount: Int,
    val bmi: Double?,
    val alerts: List<ClinicalAlert>,
    val createdAt: Long,
    val updatedAt: Long,
)

fun MeasurementUpload.isEligibleAt(nowMs: Long): Boolean {
    if (attemptCount == 0) return true
    val backoff = minOf(30_000L, 1_000L shl attemptCount) + (createdAt % 1_000L)
    return nowMs >= updatedAt + backoff
}
```
> Reuse the existing `MAX_SYNC_ATTEMPTS = 5` constant from
> `domain/model/subject/SubjectUpload.kt` (or lift it to a shared `domain/sync/`
> constant). The backoff math is identical to subjects.

#### 3. Local store — `data/measurement/local/MeasurementLocalStore.kt` (+ `Default*`)
Interface mirroring `SubjectLocalStore` (interface + `DefaultMeasurementLocalStore`
split, injecting `now: () -> Long` + `dispatcher`):
```kotlin
interface MeasurementLocalStore {
    fun observeAll(): Flow<List<MeasurementUpload>>
    suspend fun insert(upload: MeasurementUpload)
    suspend fun syncable(): List<MeasurementUpload>
    suspend fun markSyncing(localId: String)
    suspend fun markSynced(localId: String, bmi: Double?, alertsJson: String?)
    suspend fun markFailed(localId: String, reason: String)
    suspend fun bumpRetry(localId: String, reason: String)
    suspend fun resetForRetry(localId: String)
    suspend fun delete(localId: String)
    suspend fun resetStuckSyncing()
    suspend fun pruneTerminalOlderThan(cutoff: Long)
}
```
`observeAll` uses `queries.selectAll().asFlow().mapToList(dispatcher).map { it.map(PendingMeasurement::toDomain) }`.

#### 4. Mapper — `data/measurement/mapper/MeasurementUploadMapper.kt` (new)
- `PendingMeasurement (DB row) → MeasurementUpload` — parse `syncState` string,
  decode `alertsJson` (`Json.decodeFromString<List<ClinicalAlert>>` with `?:
  emptyList()`).
- `NewMeasurement.toPendingUpload(id, measuredAt, now)` → a fresh `PENDING`
  `MeasurementUpload` (alerts empty, bmi null).
- `MeasurementUpload.toRequest()` → `MeasurementRequest` (Phase 3).
- `SyncState ↔ DB-string` converters (reuse the subject ones or duplicate the small map).

### Success Criteria
#### Automated Verification:
- [ ] Common compiles (generates `PendingMeasurementQueries`): `./scripts/compile.sh`
- [ ] Android assembles: `./gradlew :composeApp:assembleDebug --no-daemon`
#### Manual Verification: none yet.

---

## Phase 3: Network contract (invented)

### Overview
DTOs for the request and the enriched success response, a `MeasurementUploadOutcome`
sealed type, and a status-branching `MeasurementApi` (mirrors `SubjectApi`).

### Changes Required

#### 1. Request DTO — `data/measurement/dto/MeasurementRequest.kt`
```kotlin
@Serializable
data class MeasurementRequest(
    val id: String,                                  // client UUID (idempotency key)
    @SerialName("subject_id") val subjectId: String,
    @SerialName("establishment_id") val establishmentId: String,
    @SerialName("weight_kg") val weightKg: Double,
    @SerialName("height_cm") val heightCm: Double,
    @SerialName("measured_at") val measuredAt: String, // ISO 8601 UTC (…Z)
)                                                      // NB: no `bmi` — server computes it
```

#### 2. Response DTOs — `data/measurement/dto/MeasurementResponse.kt`
```kotlin
@Serializable
data class AlertDto(
    @SerialName("alert_type") val alertType: String,
    val severity: String,
    val detail: String,
)

@Serializable
data class MeasurementResponse(
    val id: String,
    @SerialName("subject_id") val subjectId: String,
    @SerialName("establishment_id") val establishmentId: String,
    @SerialName("weight_kg") val weightKg: Double,
    @SerialName("height_cm") val heightCm: Double,
    @SerialName("measured_at") val measuredAt: String,
    val bmi: Double? = null,
    val alerts: List<AlertDto> = emptyList(),
)
```

#### 3. Outcome — `data/measurement/MeasurementUploadOutcome.kt`
```kotlin
sealed interface MeasurementUploadOutcome {
    data class Created(val response: MeasurementResponse) : MeasurementUploadOutcome // 201 or 200
    data object NotFound : MeasurementUploadOutcome        // 404 → terminal
    data object Forbidden : MeasurementUploadOutcome       // 403 → terminal
    data object InvalidData : MeasurementUploadOutcome     // 422 → terminal
    data object Unauthorized : MeasurementUploadOutcome    // 401 → retry/dead-letter
    data class Retryable(val cause: Throwable?) : MeasurementUploadOutcome // network/5xx
}
```
> No `Duplicate`/`Merged`: measurements have no canonical-dedup; a same-`id` replay
> returns `200` and is mapped to `Created` (idempotent).

#### 4. `MeasurementApi` — status-driven (mirrors `SubjectApi`)
```kotlin
class MeasurementApi(private val client: HttpClient) {     // authed client, no expectSuccess
    suspend fun create(body: MeasurementRequest): MeasurementUploadOutcome =
        runCatching {
            val response = client.post("api/v1/measurements") {
                contentType(ContentType.Application.Json); setBody(body)
            }
            when (response.status.value) {
                200, 201 -> MeasurementUploadOutcome.Created(response.body())
                401 -> MeasurementUploadOutcome.Unauthorized
                403 -> MeasurementUploadOutcome.Forbidden
                404 -> MeasurementUploadOutcome.NotFound
                422 -> MeasurementUploadOutcome.InvalidData
                else -> MeasurementUploadOutcome.Retryable(null)   // 5xx etc.
            }
        }.getOrElse { t ->
            if (t is CancellationException) throw t
            MeasurementUploadOutcome.Retryable(t)                  // transport failure
        }
}
```
DI: register `MeasurementApi(get())` in `di/ClientsModule.kt` using the same authed
`HttpClient` as `SubjectApi`.

### Success Criteria
#### Automated Verification:
- [ ] Common compiles: `./scripts/compile.sh`
#### Manual Verification: none (exercised in Phase 6 tests + Phase 5 flow).

---

## Phase 4: Measurement repository (sync engine) + use cases + DI

### Overview
`MeasurementRepository : SyncEngine` and its impl (outcome → state transitions, with
`bmi`/`alerts` persistence), thin use cases, and DI wiring (registered into the
`List<SyncEngine>`).

### State-transition rules
| Outcome | New state | Stored | Notes |
|---|---|---|---|
| `Created` (201/200) | `SYNCED` | `bmi`, `alertsJson` (serialized) | idempotent replay also lands here |
| `NotFound` (404) | `FAILED` | – | "El sujeto o establecimiento no existe." terminal |
| `Forbidden` (403) | `FAILED` | – | "No tenés acceso a ese establecimiento." terminal |
| `InvalidData` (422) | `FAILED` | – | "Datos inválidos (peso/talla/fecha)." terminal (should be caught locally) |
| `Unauthorized` (401) | `PENDING` (`bumpRetry`) < cap, else `FAILED` | – | backoff-gated dead-letter |
| `Retryable` (net/5xx) | `PENDING` (`bumpRetry`) < cap, else `FAILED` | – | exponential backoff |

### Changes Required

#### 1. Interface — `domain/repository/MeasurementRepository.kt`
```kotlin
interface MeasurementRepository : SyncEngine {
    suspend fun enqueue(measurement: NewMeasurement): Result<Unit>
    fun observeUploads(): Flow<List<MeasurementUpload>>
    suspend fun retry(localId: String): Result<Unit>
    suspend fun delete(localId: String): Result<Unit>
    // syncPending / resetStuckSyncing / pruneTerminal from SyncEngine
}
```

#### 2. Impl — `data/measurement/MeasurementRepositoryImpl.kt`
```kotlin
class MeasurementRepositoryImpl(
    private val api: MeasurementApi,
    private val local: MeasurementLocalStore,
    private val idGenerator: IdGenerator,
    private val now: () -> Long,
) : MeasurementRepository {

    override suspend fun enqueue(measurement: NewMeasurement): Result<Unit> = runCatching {
        val nowMs = now()
        val measuredAt = Instant.fromEpochMilliseconds(nowMs).toString()  // ISO 8601 …Z
        local.insert(measurement.toPendingUpload(id = idGenerator.newId(), measuredAt = measuredAt, now = nowMs))
    }

    override fun observeUploads() = local.observeAll()

    override suspend fun syncPending(): SyncSummary {
        var synced = 0; var failed = 0; var pending = 0
        local.syncable().filter { it.isEligibleAt(now()) }.forEach { upload ->
            local.markSyncing(upload.localId)
            when (val outcome = api.create(upload.toRequest())) {
                is MeasurementUploadOutcome.Created -> {
                    val alertsJson = Json.encodeToString(outcome.response.alerts.map { it.toDomain() })
                    local.markSynced(upload.localId, outcome.response.bmi, alertsJson); synced++
                }
                MeasurementUploadOutcome.NotFound ->
                    { local.markFailed(upload.localId, "El sujeto o establecimiento no existe."); failed++ }
                MeasurementUploadOutcome.Forbidden ->
                    { local.markFailed(upload.localId, "No tenés acceso a ese establecimiento."); failed++ }
                MeasurementUploadOutcome.InvalidData ->
                    { local.markFailed(upload.localId, "Datos inválidos. Revisá peso, talla y fecha."); failed++ }
                MeasurementUploadOutcome.Unauthorized ->
                    if (retryOrDeadLetter(upload, "Sesión expirada. Reintentando…")) pending++ else failed++
                is MeasurementUploadOutcome.Retryable ->
                    if (retryOrDeadLetter(upload, "Sin conexión. Reintentando…")) pending++ else failed++
            }
        }
        return SyncSummary(synced = synced, merged = 0, failed = failed, stillPending = pending)
    }

    private suspend fun retryOrDeadLetter(upload: MeasurementUpload, retryMsg: String): Boolean =
        if (upload.attemptCount + 1 >= MAX_SYNC_ATTEMPTS) {
            local.markFailed(upload.localId, "No se pudo sincronizar tras varios intentos."); false
        } else { local.bumpRetry(upload.localId, retryMsg); true }

    override suspend fun retry(localId: String) = runCatching { local.resetForRetry(localId) }
    override suspend fun delete(localId: String) = runCatching { local.delete(localId) }
    override suspend fun resetStuckSyncing() = local.resetStuckSyncing()
    override suspend fun pruneTerminal(olderThan: Duration) =
        local.pruneTerminalOlderThan(now() - olderThan.inWholeMilliseconds)
}
```
> (Pseudo-Kotlin; implementer uses real `when` bodies. `AlertDto.toDomain()` →
> `ClinicalAlert`.)

#### 3. Use cases — `domain/usecase/measurement/`
Thin `operator fun invoke` wrappers (mirror `domain/usecase/subject/`):
`CreateMeasurementUseCase` (enqueue), `ObserveMeasurementsUseCase` (observeUploads) —
the latter for Historia 3 but built now. (Retry/Delete/SyncNow use cases are Historia 3.)

#### 4. DI
- `di/ClientsModule.kt`: `single { MeasurementApi(get()) }` (authed client).
- `di/RepositoriesModule.kt`:
  `single<MeasurementLocalStore> { DefaultMeasurementLocalStore(queries = get(), now = { Clock.System.now().toEpochMilliseconds() }) }`,
  `single { get<AppDatabase>().pendingMeasurementQueries }` (or in `DatabaseModule`),
  `single<MeasurementRepository> { MeasurementRepositoryImpl(api = get(), local = get(), idGenerator = get(), now = { Clock.System.now().toEpochMilliseconds() }) }`.
- `di/DatabaseModule.kt`: append `get<MeasurementRepository>()` to the
  `List<SyncEngine>` started in Phase 1.
- `di/UseCasesModule.kt`: `factory { CreateMeasurementUseCase(get()) }`, `factory { ObserveMeasurementsUseCase(get()) }`.

### Success Criteria
#### Automated Verification:
- [ ] Common compiles: `./scripts/compile.sh`
- [ ] Android assembles + iOS metadata: `./gradlew :composeApp:assembleDebug compileKotlinIosSimulatorArm64 --no-daemon`
#### Manual Verification: none yet.

---

## Phase 5: create_measurement screen + navigation + Home entry

### Overview
The 6-file feature bundle (form with blur-aware validation, inline establishment
picker, subject from the Historia 1 result), a decimal validation rule, the route, and
the Home "Cargar medición" entry (replacing Historia 1's provisional banner).

### Changes Required

#### 1. Decimal rule — `core/validation/Rules.kt`
```kotlin
/** Passes on blank (so `required` owns presence). Accepts ',' or '.'; enforces min < x <= max. */
fun decimalInRange(min: Double, max: Double, message: String): Validator = { value ->
    val v = value.trim().replace(',', '.').toDoubleOrNull()
    if (value.isNotBlank() && (v == null || v <= min || v > max)) message else null
}
```
> Story bounds: peso `> 0,5 && <= 300`; talla `> 20,0 && <= 250,0` — both exclusive-low,
> inclusive-high, exactly `min < x <= max`.

#### 2. Route — `presentation/navigation/Route.kt`
```kotlin
@Serializable @SerialName("create_measurement")
data object CreateMeasurement : Route()
```
> Subject arrives via the `SELECTED_SUBJECT_RESULT` savedStateHandle result (not a
> route param), so the route is parameterless.

#### 3. State — `.../create_measurement/MeasurementState.kt`
```kotlin
data class MeasurementState(
    val destination: MeasurementDestination? = null,
    val subject: SubjectSummary? = null,
    val establishmentOptions: List<EstablishmentWithStatus> = emptyList(),
    val selectedEstablishment: EstablishmentWithStatus? = null,
    val establishmentError: String? = null,
    val isLoadingProfile: Boolean = true,
    val profileError: String? = null,
    val showEstablishmentPicker: Boolean = false,
    val weight: TextFieldState = TextFieldState(),
    val height: TextFieldState = TextFieldState(),
    val measuredAtDisplay: String = "",            // formatted "ahora" for display
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val successMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = subject != null && selectedEstablishment != null &&
            weight.value.isNotBlank() && height.value.isNotBlank() && !isSubmitting
}
```

#### 4. Action — `.../MeasurementAction.kt`
`WeightChanged`/`WeightFocusChanged`, `HeightChanged`/`HeightFocusChanged`,
`SubjectSelected(SubjectSummary)`, `ChangeSubjectClick`, `EstablishmentPickerOpen`/
`EstablishmentPickerDismiss`, `EstablishmentSelected(EstablishmentWithStatus)`,
`Submit`, `DismissSuccess`, `RetryProfile`, `BackClick`.

#### 5. Destination — `.../MeasurementDestination.kt`
```kotlin
sealed class MeasurementDestination {
    data object Back : MeasurementDestination()
    data object NavigateToSelectSubject : MeasurementDestination()
}
```

#### 6. ViewModel — `.../MeasurementViewModel.kt`
- ctor: `CreateMeasurementUseCase`, `GetMyProfileUseCase`, `SyncCoordinator`.
- `init`: load profile → `establishmentOptions = profile.establishments?.filter { it.status == Approved } ?: emptyList()`; if exactly one → `selectedEstablishment = it`; on failure set `profileError`. Set `measuredAtDisplay` to a friendly "ahora".
- Validators: `weightValidator = Rules.of(required("Ingresá el peso."), decimalInRange(0.5, 300.0, "Peso entre 0,5 y 300 kg."))`; `heightValidator` analogous (`20.0, 250.0`).
- One-line reducers for weight/height `onChange`/`onFocus` (sanitize input to digits +
  a single separator, mirroring `DniChanged`'s `filter`). `SubjectSelected` sets the
  subject (clears nothing else). `ChangeSubjectClick` → `destination =
  NavigateToSelectSubject`. `EstablishmentSelected` sets it + clears
  `establishmentError` + closes the picker.
- `submit()`: force-show weight/height; `establishmentError = if (selected == null)
  "Elegí el establecimiento." else null`; guard `subject != null`. If valid, build
  `NewMeasurement` (subject, establishment id+name, weight/height as `Double` from the
  `,`→`.` normalized strings) → `createMeasurement(it)` → on success **reset
  weight/height (keep subject + establishment)** + `successMessage = "Medición
  guardada. Se sincronizará automáticamente."` → `syncCoordinator.requestSync()`
  (fire-and-forget). On failure: `submitError`.
- `setSubjectFromResult(json)` decodes `SubjectSummary` (called from the Root's
  LaunchedEffect on the nav result) and dispatches `SubjectSelected`.

#### 7. Screen — `.../MeasurementScreen.kt`
- `AlScaffold` → `Column.imePadding()` → `AlBackTopAppBar(title = "Cargar medición",
  onBackClick = BackClick)` → weighted `.verticalScroll(scrollState)` content.
- **Subject row**: if `subject == null`, an `AlSelectField`/`AlInfoCard` "Seleccionar
  sujeto" → `ChangeSubjectClick`; else show name/apellido + "DNI …" with a trailing
  **"Cambiar"** → `ChangeSubjectClick`.
- **Establishment**: `AlSelectField(value = selectedEstablishment?.name ?: "",
  placeholder = "Elegí el establecimiento", error = establishmentError, onClick =
  EstablishmentPickerOpen)`. When `showEstablishmentPicker`, a Material3
  `ModalBottomSheet` (or inline selectable list modeled on `SexSelector`/`AlListRow`)
  of `establishmentOptions` → `EstablishmentSelected`. If `establishmentOptions` is
  empty, show "No tenés establecimientos aprobados" and keep Guardar disabled.
- **Peso** / **Talla**: `AlInput(keyboardOptions = KeyboardOptions(keyboardType =
  KeyboardType.Decimal), onFocusChange = …, error = displayError, scrollState,
  isImeVisible)` (the scroll-to-field plumbing is built into `AlInput`).
- **Fecha**: read-only `AlInput`/text showing `measuredAtDisplay` (default ahora).
- **Guardar**: `AlButton(enabled = canSubmit, loading = isSubmitting, fullWidth = true)`.
- Success banner (tokenized `Row`) when `successMessage != null`, dismissable.
- Profile loading → spinner; `profileError` → message + `RetryProfile`.
- `MeasurementScreenRoot(pickedSubjectResult: String?, onResultConsumed, navigate)`:
  `LaunchedEffect(pickedSubjectResult)` decodes the subject + `onResultConsumed()`;
  `LaunchedEffect(state.destination)` runs `navigate(it)` + `clearDestination()`.
  **First-entry behavior**: if `subject == null` and the screen was opened fresh,
  emit `NavigateToSelectSubject` once so the user "enters with a subject chosen"
  (guard with a `rememberSaveable` flag so it fires once and not after a back-out).

#### 8. Navigation — `.../MeasurementNavigation.kt`
```kotlin
fun NavController.navigateToCreateMeasurement(navOptions: NavOptions? = null) =
    navigate(Route.CreateMeasurement, navOptions)

fun NavGraphBuilder.createMeasurementScreen(navController: NavController) {
    composable<Route.CreateMeasurement> { entry ->
        val pickedJson by entry.savedStateHandle
            .getStateFlow<String?>(SELECTED_SUBJECT_RESULT, null)   // reused Historia 1 key
            .collectAsStateWithLifecycle()
        MeasurementScreenRoot(
            pickedSubjectResult = pickedJson,
            onResultConsumed = { entry.savedStateHandle[SELECTED_SUBJECT_RESULT] = null },
            navigate = { d -> navigate(d, navController) },
        )
    }
}

internal fun navigate(destination: MeasurementDestination, navController: NavController) {
    when (destination) {
        MeasurementDestination.Back -> navController.popBackStack()
        MeasurementDestination.NavigateToSelectSubject -> navController.navigateToSelectSubject()
    }
}
```
> Both `CreateMeasurement` and `SelectSubject` are on the **outer** nav, so
> `SelectSubject` writes the result to `previousBackStackEntry` (= the measurement
> entry) — the simple `Register↔SelectEstablishment` pattern the Historia 1 plan
> anticipated. No outer↔inner crossing.

#### 9. Home entry — replace Historia 1's provisional banner
- `app/AppNavHost.kt`: add `createMeasurementScreen(navController)` (outer NavHost).
- Home's "Cargar medición": instead of Historia 1's `NavigateToSelectSubject` +
  `pickedSubject` banner, dispatch a destination that calls
  `outerNav.navigateToCreateMeasurement()`. Remove the provisional `pickedSubject`
  banner + `SubjectPicked` handling from `HomeScreen`/`HomeViewModel`/`AuthedShell`
  (the measurement screen now owns subject selection). If Historia 1 is not yet
  merged, add the Home button here.

#### 10. DI — `di/ViewModelsModule.kt`
`viewModelOf(::MeasurementViewModel)`.

### Success Criteria
#### Automated Verification:
- [ ] Common compiles: `./scripts/compile.sh`
- [ ] Android assembles: `./gradlew :composeApp:assembleDebug --no-daemon`
- [ ] No provisional-banner leftovers: `grep -rn "pickedSubject" composeApp/src` returns nothing (once Historia 1's provisional consumption is replaced).
#### Manual Verification:
- [ ] Home → "Cargar medición" → selector opens; choosing a subject lands on the form with the subject shown; "Cambiar" re-opens the selector and updates it.
- [ ] Establishment picker lists approved establishments; auto-selected when only one; required error shows on submit when none.
- [ ] Peso/Talla use a decimal keyboard; out-of-range values block submit with a Spanish message; in-range pass; focused lower field scrolls above the keyboard (iOS + Android).
- [ ] Guardar (airplane mode) → confirmation, peso/talla clear, subject + establishment persist, no spinner hang.

---

## Phase 6: Tests

### Overview
Focused unit tests for the highest-risk logic, mirroring the existing subject tests.

### Changes Required (`commonTest` unless noted)
- `MeasurementUploadOutcomeTest` (Ktor `MockEngine`): 201→Created, 200→Created,
  401→Unauthorized, 403→Forbidden, 404→NotFound, 422→InvalidData, 5xx→Retryable,
  connection failure→Retryable; a `Created` body parses `bmi` + `alerts`.
- `MeasurementSyncEngineTest` (fake `MeasurementLocalStore` + fake `MeasurementApi`):
  each outcome → correct state transition + `SyncSummary` counts; **`Created`
  persists `bmi` and serialized `alerts`**; 200 idempotent replay lands `SYNCED`;
  401/network keep rows `PENDING` and bump `attemptCount`; a row at
  `attemptCount == MAX-1` hitting `Retryable` dead-letters to `FAILED`;
  `isEligibleAt` gates a freshly-bumped row and re-admits it after the window;
  `resetStuckSyncing()` flips `SYNCING`→`PENDING`.
- `RulesDecimalInRangeTest`: blank passes; `0.5`/`300.1` fail; `0.6`/`300.0` pass;
  `","` accepted; non-numeric fails (covers peso + talla bounds).
- `MeasurementViewModelTest` (Turbine + fakes): profile load filters to `Approved` and
  auto-selects when one; enqueue success resets peso/talla, keeps subject +
  establishment, sets confirmation, calls `requestSync()`; missing establishment /
  out-of-range blocks enqueue and shows errors; subject result decodes into state.
- (Optional `androidUnitTest`) `DefaultMeasurementLocalStoreTest` with
  `JdbcSqliteDriver(IN_MEMORY)`: insert → `observeAll` emits; `markSynced` stores
  `bmi`/`alertsJson`; `selectSyncable` filters; `resetSyncingToPending` works.

### Success Criteria
#### Automated Verification:
- [ ] Tests pass: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:testDebugUnitTest --no-daemon`
- [ ] Common compiles + Android assembles + iOS metadata.
#### Manual Verification:
- [ ] Save offline → row persists; go online → `SYNCED` with `bmi`/alerts stored (inspect via Historia 3 or DB).
- [ ] Force 422/403/404 → `FAILED` with the right Spanish message.
- [ ] Kill + relaunch with a pending measurement → it persists and resumes; subjects unaffected.
- [ ] iOS + Android both exhibit the above.

---

## Testing Strategy

### Unit (automated)
Outcome mapping (`MockEngine`), the sync state-transition engine incl. bmi/alerts
persistence and dead-letter (fakes), the decimal rule, and ViewModel behavior
(Turbine) — these cover the invented contract and the offline reconciliation without a
network or real DB. DB queries via in-memory `JdbcSqliteDriver` (androidUnitTest).

### Manual (iOS + Android)
The Phase 5–6 criteria exercise: subject round-trip from the selector, establishment
auto-select, decimal validation, scroll-to-field, offline save + confirmation,
auto-sync, idempotent replay (200), terminal failures (404/403/422), and persistence
across restarts — with subjects sync untouched.

## Performance Considerations
- UI observes the DB via `asFlow().mapToList(dispatcher)`; recomposition is bounded by
  queue size (low-volume captures). Add a `LIMIT` later if un-pruned `SYNCED` rows grow.
- Sync is single-flight across both engines (`Mutex.tryLock`, overlapping triggers
  dropped) and sequential per row. Pruning ships in the coordinator's hourly loop
  (`pruneTerminal(7.days)` per engine).
- All DB I/O on `Dispatchers.Default`/IO; sync on `AppScope` — off the main thread.

## Migration Notes
- **Fresh table, no migration.** `PendingMeasurement` is added to the existing
  `AppDatabase`; first launch creates it (SQLDelight auto-creates new tables in the
  shared schema). Future schema changes need `.sqm` migrations (note for later).
- **Reversible.** Revert by removing `data/measurement/*`, `domain/{model,repository,
  usecase}/measurement/*`, `PendingMeasurement.sq`, the `create_measurement` feature,
  the route/nav/Home wiring, the `MeasurementApi` binding, and the `Measurement
  Repository` entry in the `List<SyncEngine>`. The Phase 1 `SyncEngine` generalization
  is independently useful and can stay.

## Risks
- **Invented contract.** Field names/paths/error codes are from the story, not a real
  spec — confirm with the backend before shipping. The status-branching design absorbs
  unexpected non-2xx as `Retryable` (stays `PENDING`), so a mismatch degrades to
  "keeps retrying" rather than data loss.
- **PII at rest unencrypted** (subject name/DNI denormalized onto the measurement row),
  same posture as the subjects queue — flagged for an encryption follow-up.
- **Establishment source for non-manager roles.** The screen reads
  `Profile.establishments`; if `professional`/`admin` profiles carry no establishments,
  the picker is empty and Guardar stays disabled — the story flags this as
  "a definir". Surface the empty state clearly; do not crash.

## References
- Story: `thoughts/stories/create_measurement_screen.md` (Historia 2)
- Offline-first template (shipped): `thoughts/plans/subjects_offline_first_sync.md` and
  `data/subject/{SubjectRepositoryImpl,SubjectApi,SubjectUploadOutcome}.kt`,
  `data/subject/{local,sync,mapper}/*`, `domain/model/subject/{SyncState,SubjectUpload,SyncSummary}.kt`,
  `domain/util/IdGenerator.kt`, `data/network/connectivity/*`, `di/{DatabaseModule,RepositoriesModule}.kt`,
  `app/{AppViewModel,AuthedShell}.kt`.
- Subject selector (dependency): `thoughts/plans/select_subject_screen.md`,
  `presentation/feature/subject/select_subject/SelectSubjectNavigation.kt`
  (`SELECTED_SUBJECT_RESULT`, `SubjectSummary`).
- Form template: `presentation/feature/subject/create_subject/*`, `core/validation/{TextFieldState,Rules}.kt`.
- Design system: `core/design_system/components/{input/AlInput.kt,input/AlSelectField.kt,scaffold/AlScaffold.kt,top_app_bar/AlBackTopAppBar.kt,button/AlButton.kt}`.
- Profile/establishments: `domain/usecase/profile/GetMyProfileUseCase.kt`,
  `domain/model/profile/{Profile,EstablishmentWithStatus,EstablishmentStatus}.kt`.
- Navigate-back-with-result + datetime: `CLAUDE.md`; `kotlinx-datetime 0.7.1` (`Clock.System.now().toString()` → ISO-8601 `…Z`).
</content>
</invoke>
