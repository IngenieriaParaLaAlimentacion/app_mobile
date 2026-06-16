# Subjects Offline-First Sync Implementation Plan

## Overview

Turn the existing online-only **create-subject** flow into an **offline-first** one: the local
SQLDelight database becomes the source of truth. Tapping "Guardar" generates a client-side UUID,
writes the subject to local storage as `PENDING`, and returns immediately (optimistic). A
background **sync engine** pushes pending rows to `POST /api/v1/subjects` whenever connectivity and
app lifecycle allow, and reconciles the three backend contract changes (request `id`, `200`-as-
success, enriched `409`). A new **Uploads** screen lets the user watch each subject's sync state and
manually retry or delete rows.

This is a multi-file, multi-layer change. Build bottom-up so each phase compiles before the next.

## Current State Analysis

- **Create-subject is fully implemented but online-only and ephemeral.**
  `CreateSubjectViewModel.submit()` (`presentation/feature/subject/create_subject/CreateSubjectViewModel.kt:94-120`)
  calls `CreateSubjectUseCase` → `SubjectRepositoryImpl.create()` directly, and on success **clears the
  form** (`CreateSubjectState()` reset, line 107-111). Nothing is persisted. The plan that built it
  explicitly excluded "offline queue, draft persistence, retry policy, or optimistic UI"
  (`thoughts/plans/create_subject_screen.md:52`).
- **Wire contract today (old):** `SubjectApi.create()` (`data/subject/SubjectApi.kt:18-22`) does
  `client.post("api/v1/subjects").body()`, deserializing straight into `SubjectResponse`. It assumes
  `201` and has no `id` in the request. `SubjectRepositoryImpl.mapToError()`
  (`data/subject/SubjectRepositoryImpl.kt:18-27`) maps `400/401/403` to typed errors and everything
  else (including `409`) to `Network`/`Unknown` — the `409` body is discarded.
- **In-repo OpenAPI reflects the OLD contract.** `docs/backend.openapi.generated.json` `SubjectRequest`
  has **no `id`**, `ProblemDetail` has **no `canonical_subject`**, and only `201`/`409` are declared
  for success/conflict. The three contract changes described in the task are therefore net-new on the
  client and not yet in the generated spec. **The task description is the source of truth** for the
  new contract until the spec is regenerated.
- **No local persistence library exists.** No SQLDelight, Room, DataStore, or settings lib in
  `gradle/libs.versions.toml` or `composeApp/build.gradle.kts`. The only persistence is hand-rolled
  per-platform secure stores via `expect/actual`: `TokenStore` and `DeviceIdStore`
  (`data/auth/storage/`, `data/device/`), provided through `platformModule`
  (`di/Modules.android.kt`, `di/Modules.ios.kt`). This is the precedent we mirror for the SqlDriver
  factory and connectivity observer.
- **No app-level CoroutineScope or lifecycle observer.** All async work is `viewModelScope`-bound or
  plain `suspend`. App entry: `AlApp.onCreate` (Android) starts Koin; `MainViewController` (iOS).
  Root `AppViewModel` runs auth bootstrap in `init` (`app/AppViewModel.kt:25-27`). `App.kt` is the
  only lifecycle-aware spot (`collectAsStateWithLifecycle`).
- **Authed shell** has a bottom nav (Home / Profile) over an inner `NavHost`
  (`app/AuthedShell.kt:34-79`) — the natural place to add an "Uploads" tab.
- **DI topology:** `clientsModule` (APIs + `HttpClient`s), `repositoriesModule`, `useCasesModule`,
  `viewModelsModule`, plus `expect/actual platformModule`. `SubjectApi` uses the default authed
  `HttpClient` (`di/ClientsModule.kt:45`).

### Key Discoveries

- **Generated UUID doubles as the idempotency key.** The client-generated `localId` (a v4 UUID) is
  BOTH the SQLDelight primary key AND the `id` sent in the request body. On `201`/`200` the server
  confirms that id; on `409` the server returns a **different** `canonical_subject.id` (the pre-
  existing subject), so `canonicalId != localId` is the dedup signal.
- **The authed `HttpClient` does NOT set `expectSuccess`** (only `createRefreshClient` does —
  `data/network/HttpClientFactory.kt:35`). So non-2xx responses on the authed client do **not** throw;
  `.body()` would mis-deserialize a `409` `ProblemDetail` into `SubjectResponse`. The robust fix is to
  branch on `response.status` explicitly in `SubjectApi` and deserialize the matching body — this is
  status-code-driven and independent of `expectSuccess`.
- **Kotlin 2.3.20** → `kotlin.uuid.Uuid.random()` is built-in (experimental, opt-in). No dependency.
- **SQLDelight 2.3.2** (`app.cash.sqldelight`): driver via `expect/actual` factory, Flow observation
  via `coroutines-extensions` (`.asFlow().mapToList(...)`). This is the offline-first backbone: UI
  observes the DB, sync writes into it.
- **CMP lifecycle:** `LocalLifecycleOwner` + `LifecycleStartEffect`/`LifecycleResumeEffect` work in
  commonMain (lifecycle 2.10.0 already on classpath). There is **no** cross-platform
  `ProcessLifecycleOwner`; the composition lifecycle is sufficient for foreground/background triggers.
- **True OS background sync** (Android `WorkManager` + iOS `BGTaskScheduler`) has no cross-platform
  abstraction and is **out of scope** for this plan (separate milestone).
- **Test infra exists** (`commonTest`: `kotlin.test`, `kotlinx-coroutines-test`, `turbine`,
  `ktor-client-mock`). SQLDelight JVM in-memory driver (`JdbcSqliteDriver`) is needed only for DB
  integration tests in `androidUnitTest`.

### Battle-tested precedent: the `../../extra` outbox implementations
The sister CMP marketplace app (`/Users/juanduzac/dev/extra`) ships **two** SQLDelight outbox queues we
mirror here. The closest template is **telemetry `BatchProcessor`** (app-global scope), not chat
(per-screen `viewModelScope`). Adopted patterns (each woven into the phases below):
- **Reset in-flight rows on `start()`** — both queues run `resetSendingToPending` / `resetStuckEvents()`
  so a kill mid-POST never leaves a row stuck in the in-flight state. (Telemetry
  `BatchProcessor.start()`; chat `MessageRequest.sq:resetSentToPending`.)
- **Retry cap + exponential backoff with deterministic jitter** — chat: `failedCount >= MAX(5)` →
  permanently ineligible (dead-letter the UI shows); `minOf(30_000L, 1_000L shl failedCount) +
  createdAtMs % 1_000L`. Eligibility is computed in Kotlin (`MessageScheduler.isEligibleAt`), not SQL.
- **Single-flight via `tryLock()`** (telemetry `flushMutex.tryLock()`), so overlapping triggers are
  *dropped*, not queued.
- **Pruning shipped, not deferred** — telemetry runs `cleanup(maxEventAgeMs, maxRetries)` on an hourly
  loop (prunes old + over-cap rows).
- **Client-UUID PK == idempotency key, mark-in-flight-before-the-call, delete/terminal-on-success** —
  all already in this plan.
- **No connectivity observer exists in `extra` at all** — the marketplace rides entirely on
  poll + backoff. See Risks: this makes our hand-rolled `ConnectivityObserver` an *optimization layer*,
  not the backbone.
- Where this plan already **improves on extra**: `extra` passes raw `now: Long` from callers (chat
  scheduler is therefore hard to unit-test); we inject `now: () -> Long` via Koin. And `extra` telemetry
  *deletes* permanent failures (data-loss acceptable for analytics); we keep `400/403 → FAILED` as a
  user-actionable dead-letter because the rows hold subject PII.

## Desired End State

A signed-in establishment manager can:

1. Fill the create-subject form and tap "Guardar" → the subject is saved locally **instantly** as
   `PENDING`, the form clears, and a confirmation appears ("Guardado. Se sincronizará
   automáticamente."). No network wait.
2. Continue adding subjects fully offline; all queue locally.
3. Open the **Uploads** tab and see every subject with its state: `PENDING`, `SYNCING`, `SYNCED`,
   `MERGED` (already existed on the server — shows the canonical id), or `FAILED` (with a short
   Spanish reason). Manual **Retry** and **Delete** actions are available where applicable.
4. When the app comes to foreground, connectivity returns, or a periodic foreground tick fires,
   pending rows sync. Re-POSTs are idempotent (`200` replay → `SYNCED`).
5. A duplicate DNI (`409`) marks the row `MERGED`, stores `canonical_subject.id`, and is surfaced as
   "ya existía" — not a hard error.

### Verification

- `./scripts/compile.sh` passes after every phase.
- `./gradlew :composeApp:assembleDebug --no-daemon` passes after UI phases.
- Manual: create offline → row is `PENDING`; go online → row becomes `SYNCED`; create a duplicate DNI
  → `MERGED` with canonical id; kill the app mid-queue → rows persist and resume on next launch.

## What We're NOT Doing

- **No true OS background sync** (WorkManager / BGTaskScheduler). Foreground + connectivity +
  periodic-while-foregrounded only. (Separate milestone; the shared sync engine is reusable when it
  lands.)
- **No edit-before-retry** on the Uploads screen (the user chose retry + delete only). Fixing a failed
  row's data is out of scope; delete + re-create instead.
- **No offline-first for any other entity** (profile, establishments stay online-only). Only subjects.
- **No local read-cache of server-side subjects.** The DB holds the **upload queue** (locally created
  subjects), not a mirror of all server subjects. There is no GET-subjects endpoint to mirror.
- **No child-record remapping logic.** The app has no records linked to subjects yet; we persist the
  canonical id so the remap can be wired in later, but build no remap engine now.
- **No third-party connectivity/uuid/background libraries.** Hand-rolled `expect/actual`
  `ConnectivityObserver`; stdlib `Uuid`.
- **No new design-system components** beyond what the Uploads list needs inline; extract later.
- **No DB encryption.** The queue holds subject PII (DNI, name, birth date). Encryption-at-rest
  (SQLCipher / per-platform) is noted as a follow-up risk, not implemented here.

## Implementation Approach

Bottom-up. Phases 1–4 are non-UI and verifiable via `./scripts/compile.sh` + unit tests. Phase 5
rewires the existing create screen. Phase 6 adds the Uploads UI. Phase 7 wires lifecycle/connectivity
triggers and adds focused tests for the sync engine and outcome mapping (the highest-risk logic).

The data flow inverts: **UI → local DB (instant)**, then **sync engine: DB pending → Ktor → DB
result**, and **UI observes DB via Flow**. The repository hides both the DB and the network.

---

## Phase 1: SQLDelight + app-scope infrastructure

### Overview
Add SQLDelight (plugin, deps, driver factory, schema scaffold), an app-level `CoroutineScope`, and
Koin wiring. No business logic yet.

### Changes Required

#### 1. Version catalog — `gradle/libs.versions.toml`
```toml
[versions]
sqlDelight = "2.3.2"

[plugins]
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqlDelight" }

[libraries]
sqldelight-runtime        = { module = "app.cash.sqldelight:runtime", version.ref = "sqlDelight" }
sqldelight-coroutines     = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqlDelight" }
sqldelight-android-driver = { module = "app.cash.sqldelight:android-driver", version.ref = "sqlDelight" }
sqldelight-native-driver  = { module = "app.cash.sqldelight:native-driver", version.ref = "sqlDelight" }
sqldelight-jvm-driver     = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqlDelight" } # tests only
```

#### 2. Module build — `composeApp/build.gradle.kts`
Add the plugin alias, the `sqldelight { }` block, and per-source-set drivers:
```kotlin
plugins {
    // ...existing...
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("ar.com.ingenieriaalimentacion.data.db")
        }
    }
}

// in kotlin { sourceSets { } }:
commonMain.dependencies {
    // ...existing...
    implementation(libs.sqldelight.runtime)
    implementation(libs.sqldelight.coroutines)
}
androidMain.dependencies {
    // ...existing...
    implementation(libs.sqldelight.android.driver)
}
nativeMain.dependencies {
    // ...existing (ktor darwin)...
    implementation(libs.sqldelight.native.driver)
}
commonTest.dependencies {
    // ...existing...
    implementation(libs.sqldelight.jvm.driver) // JdbcSqliteDriver for in-memory DB tests
}
```
> Note: iOS driver goes in `nativeMain` (matches where `ktor-client-darwin` lives today). The
> `iosArm64` + `iosSimulatorArm64` targets both inherit `nativeMain`.

#### 3. Schema scaffold — `composeApp/src/commonMain/sqldelight/ar/com/ingenieriaalimentacion/data/db/PendingSubject.sq`
Minimal table now; full columns/queries land in Phase 2. Created here so the `AppDatabase` accessor
generates and compiles:
```sql
CREATE TABLE PendingSubject (
    localId TEXT NOT NULL PRIMARY KEY
);
```

#### 4. Driver factory — `expect/actual`
**`data/db/DatabaseDriverFactory.kt`** (commonMain):
```kotlin
package ar.com.ingenieriaalimentacion.data.db

import app.cash.sqldelight.db.SqlDriver

interface DatabaseDriverFactory {
    fun create(): SqlDriver
}
```
**`data/db/DatabaseDriverFactory.android.kt`** (androidMain):
```kotlin
package ar.com.ingenieriaalimentacion.data.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

class AndroidDatabaseDriverFactory(private val context: Context) : DatabaseDriverFactory {
    override fun create(): SqlDriver =
        AndroidSqliteDriver(AppDatabase.Schema, context, "ingenieria_alimentacion.db")
}
```
**`data/db/DatabaseDriverFactory.ios.kt`** (iosMain):
```kotlin
package ar.com.ingenieriaalimentacion.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

class IosDatabaseDriverFactory : DatabaseDriverFactory {
    override fun create(): SqlDriver =
        NativeSqliteDriver(AppDatabase.Schema, "ingenieria_alimentacion.db")
}
```

#### 5. App-level scope — `di/AppScope.kt` (commonMain)
The sync engine needs a scope that outlives any ViewModel.
```kotlin
package ar.com.ingenieriaalimentacion.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)
```

#### 6. DI wiring
- **`di/Modules.android.kt`**: `single<DatabaseDriverFactory> { AndroidDatabaseDriverFactory(androidContext()) }`
- **`di/Modules.ios.kt`**: `single<DatabaseDriverFactory> { IosDatabaseDriverFactory() }`
- **New `di/DatabaseModule.kt`** (commonMain), added to `initKoin` module list:
```kotlin
val databaseModule = module {
    single { AppDatabase(get<DatabaseDriverFactory>().create()) }
    single { get<AppDatabase>().pendingSubjectQueries }
    single { AppScope() }
}
```
- **`di/InitKoin.kt`**: add `databaseModule` to the `modules(...)` list.

### Success Criteria

#### Automated Verification:
- [x] Common compilation passes: `./scripts/compile.sh`
- [x] Android assembles (generates SQLDelight interface + driver): `./gradlew :composeApp:assembleDebug --no-daemon`

#### Manual Verification:
- None (no behavior yet).

---

## Phase 2: Local store + domain model

### Overview
Define the full `PendingSubject` schema and queries, the domain `SubjectUpload` model and `SyncState`,
a `SubjectLocalStore` wrapping the queries (with Flow observation), and the mappers. Redefine
`SubjectRepository` as an offline-first contract.

### Changes Required

#### 1. Full schema — `PendingSubject.sq`
```sql
CREATE TABLE PendingSubject (
    localId        TEXT    NOT NULL PRIMARY KEY,  -- v4 UUID; also the request `id`
    dni            INTEGER NOT NULL,
    sex            TEXT    NOT NULL,              -- 'male' | 'female'
    name           TEXT    NOT NULL,
    lastName       TEXT    NOT NULL,
    birthDate      TEXT    NOT NULL,              -- ISO YYYY-MM-DD
    isRural        INTEGER NOT NULL,             -- 0/1
    isIndigenous   INTEGER NOT NULL,             -- 0/1
    syncState      TEXT    NOT NULL,             -- PENDING|SYNCING|SYNCED|MERGED|FAILED
    canonicalId    TEXT,                         -- server id (own id on 201/200; canonical on 409)
    errorMessage   TEXT,                         -- short Spanish reason when FAILED
    attemptCount   INTEGER NOT NULL DEFAULT 0,
    createdAt      INTEGER NOT NULL,             -- epoch millis
    updatedAt      INTEGER NOT NULL
);

CREATE INDEX PendingSubject_state ON PendingSubject(syncState);

insert:
INSERT INTO PendingSubject(localId, dni, sex, name, lastName, birthDate, isRural, isIndigenous,
    syncState, canonicalId, errorMessage, attemptCount, createdAt, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

selectAll:
SELECT * FROM PendingSubject ORDER BY createdAt DESC;

selectSyncable:                                  -- All PENDING rows (fresh or auto-retrying). Backoff
SELECT * FROM PendingSubject                     -- gating (updatedAt + delay <= now) is applied in Kotlin,
WHERE syncState = 'PENDING' ORDER BY createdAt ASC; -- mirroring extra's MessageScheduler.isEligibleAt.
                                                 -- FAILED is terminal (dead-letter / 400 / 403), never auto-retried.

selectById:
SELECT * FROM PendingSubject WHERE localId = ?;

bumpRetry:                                        -- transient 401/network: back to PENDING (from the SYNCING set
UPDATE PendingSubject SET syncState = 'PENDING', attemptCount = attemptCount + 1, errorMessage = ?,  -- by markSyncing),
    updatedAt = ? WHERE localId = ?;              -- count the attempt, restart the backoff window. (FIX: the
                                                  -- syncState='PENDING' reset is required or the row stays stuck SYNCING.)

resetSyncingToPending:                            -- CRASH RECOVERY: run once on SyncCoordinator.start().
UPDATE PendingSubject SET syncState = 'PENDING', updatedAt = ? WHERE syncState = 'SYNCING';

pruneTerminalOlderThan:                           -- GC SYNCED/MERGED rows older than a cutoff (epoch ms).
DELETE FROM PendingSubject WHERE syncState IN ('SYNCED', 'MERGED') AND updatedAt < :cutoff;

markState:
UPDATE PendingSubject SET syncState = ?, updatedAt = ? WHERE localId = ?;

markSynced:                                      -- 201/200 or 409
UPDATE PendingSubject SET syncState = ?, canonicalId = ?, errorMessage = NULL, updatedAt = ?
WHERE localId = ?;

markFailed:
UPDATE PendingSubject SET syncState = 'FAILED', errorMessage = ?, attemptCount = attemptCount + 1,
    updatedAt = ? WHERE localId = ?;

resetForRetry:                                   -- manual Retry on a dead-letter FAILED row: fresh start.
UPDATE PendingSubject SET syncState = 'PENDING', errorMessage = NULL, attemptCount = 0, updatedAt = ?
WHERE localId = ?;

deleteById:
DELETE FROM PendingSubject WHERE localId = ?;
```

#### 2. Domain — `SyncState` and `SubjectUpload`
**`domain/model/subject/SyncState.kt`**:
```kotlin
package ar.com.ingenieriaalimentacion.domain.model.subject

enum class SyncState { Pending, Syncing, Synced, Merged, Failed }
```
**`domain/model/subject/SubjectUpload.kt`**: the queued subject + its sync metadata (wraps the data
of `NewSubject` plus `localId`, `syncState`, `canonicalId`, `errorMessage`, `attemptCount`, timestamps).
Keep `NewSubject`/`Subject` as-is for form input / server echo. Add the backoff eligibility helper
(ported from extra `MessageScheduler.isEligibleAt` / `nextRetryEligibleAtMs`):
```kotlin
const val MAX_SYNC_ATTEMPTS = 5

fun SubjectUpload.isEligibleAt(nowMs: Long): Boolean {
    if (attemptCount == 0) return true                          // fresh row → sync immediately
    val backoff = minOf(30_000L, 1_000L shl attemptCount) + (createdAt % 1_000L) // exp + per-row jitter
    return nowMs >= updatedAt + backoff
}
```

#### 3. Local store — `data/subject/local/SubjectLocalStore.kt`
Wraps `PendingSubjectQueries`. All writes stamp `updatedAt` via an injected time source (see note).
```kotlin
class SubjectLocalStore(
    private val queries: PendingSubjectQueries,
    private val now: () -> Long,                 // inject for testability
) {
    fun observeAll(): Flow<List<SubjectUpload>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.IO).map { it.map(PendingSubject::toDomain) }

    suspend fun insert(upload: SubjectUpload) { /* queries.insert(...) on Dispatchers.IO */ }
    suspend fun syncable(): List<SubjectUpload> { /* selectSyncable — all PENDING */ }
    suspend fun markSyncing(localId: String) { /* markState SYNCING */ }
    suspend fun markSynced(localId: String, state: SyncState, canonicalId: String) { /* markSynced */ }
    suspend fun markFailed(localId: String, reason: String) { /* markFailed; bumps attemptCount → FAILED */ }
    suspend fun bumpRetry(localId: String, reason: String, nowMs: Long) { /* bumpRetry; stays PENDING */ }
    suspend fun resetForRetry(localId: String) { /* resetForRetry; attemptCount → 0 */ }
    suspend fun delete(localId: String) { /* deleteById */ }

    /** CRASH RECOVERY — un-stick rows left mid-POST. Call once on SyncCoordinator.start(). */
    suspend fun resetStuckSyncing() { /* resetSyncingToPending */ }
    /** GC terminal rows older than [cutoff] (epoch ms). Call from the periodic cleanup loop. */
    suspend fun pruneTerminalOlderThan(cutoff: Long) { /* pruneTerminalOlderThan */ }
}
```
> **Time source:** inject `now: () -> Long` (bound in Koin to
> `{ Clock.System.now().toEpochMilliseconds() }`) so tests are deterministic. Mirrors the VM's use of
> `kotlin.time.Clock`.

#### 4. Mapper — `data/subject/mapper/SubjectUploadMapper.kt`
`PendingSubject` (DB row) ↔ `SubjectUpload` (domain), plus `NewSubject` → `SubjectUpload` (new
`PENDING` row with a freshly generated `localId`). Sex/bools/date ↔ string conversions live here.

#### 5. UUID generator — `domain/util/IdGenerator.kt`
```kotlin
interface IdGenerator { fun newId(): String }

@OptIn(ExperimentalUuidApi::class)
class UuidGenerator : IdGenerator {
    override fun newId(): String = Uuid.random().toString()
}
```
Bound in Koin; injected where new uploads are created (so tests can supply deterministic ids).

#### 6. Repository contract — rewrite `domain/repository/SubjectRepository.kt`
```kotlin
interface SubjectRepository {
    /** Persist a new subject locally as PENDING. Returns immediately (offline-first). */
    suspend fun enqueue(newSubject: NewSubject): Result<Unit>

    /** Observe all locally-tracked uploads for the Uploads screen. */
    fun observeUploads(): Flow<List<SubjectUpload>>

    /** Push all syncable (PENDING/FAILED) rows to the backend; reconcile outcomes locally. */
    suspend fun syncPending(): SyncSummary

    /** Re-queue a single FAILED row for the next sync pass. */
    suspend fun retry(localId: String): Result<Unit>

    /** Remove a not-yet-synced row. */
    suspend fun delete(localId: String): Result<Unit>

    /** CRASH RECOVERY: un-stick rows left in SYNCING by a kill mid-POST. Call once on coordinator start. */
    suspend fun resetStuckSyncing()

    /** GC terminal (SYNCED/MERGED) rows older than [olderThan]. Called from the periodic cleanup loop. */
    suspend fun pruneTerminal(olderThan: Duration)
}
```
`SyncSummary` (domain): counts of `synced` / `merged` / `failed` / `stillPending` for logging/UX.
`SubjectRepositoryImpl` is rewritten in Phase 4 (needs the API outcome type from Phase 3); for now,
add the interface + a stub impl that compiles (enqueue + observe + delete via the local store; sync
returns empty). Koin binding stays.

### Success Criteria

#### Automated Verification:
- [x] Common compilation passes: `./scripts/compile.sh`
- [x] Android assembles: `./gradlew :composeApp:assembleDebug --no-daemon`

#### Manual Verification:
- None yet.

---

## Phase 3: Wire the backend contract changes

### Overview
Implement the three contract changes in the network layer: add `id` to the request, treat `200` and
`201` both as success, and parse the enriched `409` `canonical_subject`. `SubjectApi` returns a typed
`SubjectUploadOutcome` by branching on the HTTP status — robust regardless of `expectSuccess`.

### Changes Required

#### 1. Request DTO — `data/subject/dto/SubjectRequest.kt`
Add the optional idempotency id (always sent by the offline-first client):
```kotlin
@Serializable
data class SubjectRequest(
    val id: String,                              // NEW: client-generated UUID (idempotency key)
    val dni: Int,
    val sex: SexDto,
    val name: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("birth_date") val birthDate: String,
    @SerialName("is_rural") val isRural: Boolean,
    @SerialName("is_indigenous") val isIndigenous: Boolean,
)
```
> `encodeDefaults = true` is already set in `HttpClientFactory.json()`, so a non-null `id` always
> serializes. Omitting `id` (old behavior) is not needed — every offline-first row has a `localId`.

#### 2. Conflict DTO — `data/subject/dto/SubjectConflictProblem.kt`
Focused view of the `409` `problem+json` body (extra fields ignored via `ignoreUnknownKeys`):
```kotlin
@Serializable
data class SubjectConflictProblem(
    @SerialName("canonical_subject") val canonicalSubject: CanonicalSubjectDto? = null,
)

@Serializable
data class CanonicalSubjectDto(val id: String)
```

#### 3. Outcome type — `data/subject/SubjectUploadOutcome.kt`
```kotlin
sealed interface SubjectUploadOutcome {
    data class Created(val response: SubjectResponse) : SubjectUploadOutcome   // 201 or 200 (replay)
    data class Duplicate(val canonicalId: String) : SubjectUploadOutcome       // 409 + canonical_subject
    data object InvalidData : SubjectUploadOutcome                             // 400
    data object Unauthorized : SubjectUploadOutcome                            // 401 (after refresh failed)
    data object Forbidden : SubjectUploadOutcome                              // 403
    data class Retryable(val cause: Throwable?) : SubjectUploadOutcome         // network/5xx/unknown
}
```

#### 4. `SubjectApi` — status-driven, no mis-deserialization
```kotlin
class SubjectApi(private val client: HttpClient) {
    suspend fun create(body: SubjectRequest): SubjectUploadOutcome =
        runCatching {
            val response = client.post("api/v1/subjects") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            when (response.status.value) {
                200, 201 -> SubjectUploadOutcome.Created(response.body())
                409 -> {
                    val problem = runCatching { response.body<SubjectConflictProblem>() }.getOrNull()
                    val canonical = problem?.canonicalSubject?.id
                    if (canonical != null) SubjectUploadOutcome.Duplicate(canonical)
                    else SubjectUploadOutcome.Retryable(null) // 409 without extension → retry/surface
                }
                400 -> SubjectUploadOutcome.InvalidData
                401 -> SubjectUploadOutcome.Unauthorized
                403 -> SubjectUploadOutcome.Forbidden
                else -> SubjectUploadOutcome.Retryable(null)  // 5xx etc.
            }
        }.getOrElse { t ->
            if (t is CancellationException) throw t
            SubjectUploadOutcome.Retryable(t)                 // connection failures
        }
}
```
> Reading `response.body()` on a non-2xx works because the authed client doesn't set `expectSuccess`.
> The `runCatching` covers transport failures (no connectivity) → `Retryable`, which keeps the row
> `PENDING`. `CancellationException` is rethrown per Kotlin coroutine rules.

#### 5. Keep `SubjectResponse`/`toDomain` as-is
The success body is unchanged; `SubjectResponse.id` is the server's confirmation of our `localId`.

### Success Criteria

#### Automated Verification:
- [x] Common compilation passes: `./scripts/compile.sh`

#### Manual Verification:
- None yet (exercised in Phase 7 tests + Phase 5/6 manual flow).

---

## Phase 4: Sync engine

### Overview
Implement `SubjectRepositoryImpl.syncPending()` (outcome → state transitions), the `ConnectivityObserver`
(`expect/actual`), and a single-flight `SyncCoordinator` running on `AppScope`.

### State-transition rules (the heart of the feature)
For each syncable row, mark `SYNCING`, POST with `id = localId`, then:

| Outcome | New state | canonicalId | Notes |
|---|---|---|---|
| `Created` (201/200) | `SYNCED` | `response.id` (== localId) | idempotent replay also lands here |
| `Duplicate` (409) | `MERGED` | `canonical_subject.id` | terminal, not an error; surfaced as "ya existía" |
| `InvalidData` (400) | `FAILED` | – | terminal; data is bad, retrying won't help |
| `Forbidden` (403) | `FAILED` | – | terminal; role/permission |
| `Unauthorized` (401) | `PENDING` (`bumpRetry`) while `attemptCount+1 < MAX`, else `FAILED` | – | refresh already attempted by Auth plugin; backoff-gated |
| `Retryable` (network/5xx) | `PENDING` (`bumpRetry`) while `attemptCount+1 < MAX`, else `FAILED` | – | backoff-gated; dead-letters to `FAILED` at the cap |

> 400/403 are terminal `FAILED` immediately (user-actionable: delete). 401/network **stay `PENDING` but
> count the attempt and apply exponential backoff** (`bumpRetry`), so the row keeps its "retrying" badge
> while not hammering the server, and **dead-letters to `FAILED`** only after `MAX_SYNC_ATTEMPTS`. This
> keeps the plan's clean `PENDING`=auto-retrying / `FAILED`=needs-attention UI split (a deliberate
> *improvement* over extra, whose chat queue has no such split), while fixing the original plan's
> infinite `Retryable → PENDING` loop.
>
> **Backoff + cap (extra `MessageScheduler.isEligibleAt` / `nextRetryEligibleAtMs`):**
> `const val MAX_SYNC_ATTEMPTS = 5`. A PENDING row with `attemptCount > 0` is eligible at
> `updatedAt + minOf(30_000L, 1_000L shl attemptCount) + (createdAt % 1_000L)` — exponential
> (2s,4s,8s,16s,30s-cap) plus deterministic per-row jitter (`createdAt % 1000`, no RNG, stable across
> reads). Fresh rows (`attemptCount == 0`) are eligible immediately. `syncPending()` reads
> `local.syncable()` (all PENDING) then filters to eligible-now rows in Kotlin before POSTing. (extra's
> telemetry uses the same shape: `maxRetries=5`, ×2.0 backoff, `1s→60s` cap.)

### Changes Required

#### 1. `SubjectRepositoryImpl` (rewrite)
```kotlin
class SubjectRepositoryImpl(
    private val api: SubjectApi,
    private val local: SubjectLocalStore,
    private val idGenerator: IdGenerator,
    private val now: () -> Long,
) : SubjectRepository {

    override suspend fun enqueue(newSubject: NewSubject): Result<Unit> = runCatching {
        local.insert(newSubject.toPendingUpload(id = idGenerator.newId(), now = now()))
    }

    override fun observeUploads(): Flow<List<SubjectUpload>> = local.observeAll()

    override suspend fun syncPending(): SyncSummary {
        var synced = 0; var merged = 0; var failed = 0; var pending = 0
        // Backoff: read all PENDING, then keep only rows whose backoff window has elapsed.
        local.syncable().filter { it.isEligibleAt(now()) }.forEach { upload ->
            local.markSyncing(upload.localId)
            when (val outcome = api.create(upload.toRequest())) {
                is SubjectUploadOutcome.Created ->
                    { local.markSynced(upload.localId, SyncState.Synced, outcome.response.id); synced++ }
                is SubjectUploadOutcome.Duplicate ->
                    { local.markSynced(upload.localId, SyncState.Merged, outcome.canonicalId); merged++ }
                SubjectUploadOutcome.InvalidData ->
                    { local.markFailed(upload.localId, "Los datos no son válidos."); failed++ }
                SubjectUploadOutcome.Forbidden ->
                    { local.markFailed(upload.localId, "No tenés permisos para cargar sujetos."); failed++ }
                // 401/network: stay PENDING + backoff until the cap, then dead-letter to FAILED.
                SubjectUploadOutcome.Unauthorized ->
                    if (retryOrDeadLetter(upload, "Sesión expirada. Reintentando…")) pending++ else failed++
                is SubjectUploadOutcome.Retryable ->
                    if (retryOrDeadLetter(upload, "Sin conexión. Reintentando…")) pending++ else failed++
            }
        }
        return SyncSummary(synced, merged, failed, pending)
    }

    // Stay PENDING + count the attempt while under the cap (returns true); dead-letter to FAILED at the cap (false).
    private suspend fun retryOrDeadLetter(upload: SubjectUpload, retryMsg: String): Boolean =
        if (upload.attemptCount + 1 >= MAX_SYNC_ATTEMPTS) {
            local.markFailed(upload.localId, "No se pudo sincronizar tras varios intentos."); false  // dead-letter
        } else {
            local.bumpRetry(upload.localId, retryMsg, now()); true                                    // stays PENDING
        }

    override suspend fun retry(localId: String) = runCatching { local.resetForRetry(localId) }
    override suspend fun delete(localId: String) = runCatching { local.delete(localId) }
}
```
> (Pseudo-Kotlin; implementer uses proper `when` branch bodies, not lambda blocks. Exhaustive `when`
> over the sealed `SubjectUploadOutcome`, no `else`.)

#### 2. `ConnectivityObserver` — `expect/actual`
**`data/network/connectivity/ConnectivityObserver.kt`** (commonMain):
```kotlin
interface ConnectivityObserver {
    val isOnline: Flow<Boolean>     // distinct-until-changed online/offline stream
    suspend fun isOnline(): Boolean // one-shot check
}
```
**Android** (`...android.kt`): `ConnectivityManager.registerNetworkCallback` over a `callbackFlow`,
checking `NetworkCapabilities.NET_CAPABILITY_VALIDATED`. Constructed with `androidContext()`.
**iOS** (`...ios.kt`): `NWPathMonitor` over a `callbackFlow`, mapping `path.status == .satisfied`.
Bound in each `platformModule`. (Library alternative: `dev.jordond.connectivity:connectivity-device:2.4.1`
— not adopted, to stay consistent with the project's hand-rolled `expect/actual` philosophy.)

#### 3. `SyncCoordinator` — `data/subject/sync/SyncCoordinator.kt`
Single-flight orchestrator on `AppScope`. A `Mutex` prevents overlapping passes; collects
connectivity to auto-sync when online returns.
```kotlin
class SyncCoordinator(
    private val repository: SubjectRepository,
    private val connectivity: ConnectivityObserver,
    private val scope: AppScope,
) {
    private val mutex = Mutex()
    private var started = false

    fun start() {
        if (started) return                          // idempotent (extra BatchProcessor.start guard)
        started = true
        scope.launch {
            repository.resetStuckSyncing()           // CRASH RECOVERY: un-stick rows left in SYNCING
            connectivity.isOnline.collect { online -> if (online) requestSync() }  // optimization layer
        }
        scope.launch {                               // periodic cleanup (extra telemetry hourly loop)
            while (isActive) { delay(1.hours); repository.pruneTerminal(olderThan = 7.days) }
        }
    }

    fun requestSync() {
        scope.launch {
            if (!mutex.tryLock()) return@launch       // single-flight: DROP overlapping triggers, don't queue
            try { runCatching { repository.syncPending() } } finally { mutex.unlock() } // never crash app scope
        }
    }
}
```
> **`tryLock()` not `withLock()`** (extra telemetry `flushMutex.tryLock()`): connectivity + foreground +
> periodic triggers commonly fire together; we want the in-flight pass to absorb them, not enqueue N
> back-to-back passes. **`resetStuckSyncing()` on start** is the crash-recovery counterpart to the
> `SYNCING` write in `syncPending` (extra's `resetStuckEvents` / `MessageTimeoutController`). The
> connectivity collector is now an *optimization* (instant resync on reconnect), not the backbone — the
> periodic foreground tick + backoff already guarantee progress even if the observer misbehaves (extra
> ships **no** connectivity observer at all and relies solely on poll + backoff).

#### 4. Domain use cases — `domain/usecase/subject/`
- Rewrite `CreateSubjectUseCase` → `repository.enqueue(newSubject)`.
- `ObserveSubjectUploadsUseCase(repo).invoke(): Flow<List<SubjectUpload>>`
- `SyncSubjectsUseCase(repo).invoke(): SyncSummary`
- `RetrySubjectUploadUseCase(repo).invoke(localId)`
- `DeleteSubjectUploadUseCase(repo).invoke(localId)`

#### 5. DI
- `repositoriesModule`: update `SubjectRepositoryImpl` binding with new deps (`local`, `idGenerator`,
  `now`). Add `single { SubjectLocalStore(get(), now = { Clock.System.now().toEpochMilliseconds() }) }`,
  `single<IdGenerator> { UuidGenerator() }`, `single { now provider }` (or inline the lambda).
- `platformModule` (android/ios): add `ConnectivityObserver` actual binding.
- New `single { SyncCoordinator(get(), get(), get()) }` (in `databaseModule` or a new `syncModule`).
- `useCasesModule`: add the four new use cases; keep `CreateSubjectUseCase`.

### Success Criteria

#### Automated Verification:
- [x] Common compilation passes: `./scripts/compile.sh`
- [x] Android assembles: `./gradlew :composeApp:assembleDebug --no-daemon` (+ iOS `compileKotlinIosSimulatorArm64`)

#### Manual Verification:
- None yet (UI in Phase 5/6).

---

## Phase 5: Create-subject → local-first

### Overview
Rewrite `CreateSubjectViewModel.submit()` to enqueue locally and return instantly, then fire a sync
request (non-blocking). The form clears with a "saved, will sync" confirmation. No network wait, no
network error surfaced on this screen (errors live on the Uploads screen now).

### Changes Required

#### 1. `CreateSubjectViewModel`
- Inject `CreateSubjectUseCase` (now enqueue) **and** `SyncCoordinator`.
- `submit()`: validate (unchanged) → `createSubject(newSubject)` (enqueue, near-instant) → on success
  reset form + `successMessage = "Guardado. Se sincronizará automáticamente."` → call
  `syncCoordinator.requestSync()` (fire-and-forget; no UI dependency on its result).
- Remove `submitError` network handling / `toUserMessage()` SubjectError mapping (no longer reachable
  from here). Keep field validation errors.
- `isSubmitting` becomes a brief local-write guard (optional; the write is fast).

#### 2. `CreateSubjectState`
- Keep `successMessage`; `submitError` can be dropped (or repurposed for the rare local-write
  failure). Keep `canSubmit`, field errors.

#### 3. Optional: success affordance to Uploads
Add a "Ver subidas" action on the success banner that navigates to the Uploads tab (wired in Phase 6).

### Success Criteria

#### Automated Verification:
- [x] Common compilation passes: `./scripts/compile.sh`
- [x] Android assembles: `./gradlew :composeApp:assembleDebug --no-daemon`

#### Manual Verification:
- [ ] Tap "Guardar" with the device in airplane mode → form clears + confirmation, no error, no spinner hang.
- [ ] Validation errors still block submit as before.

---

## Phase 6: Uploads status screen

### Overview
A new feature screen (6-file MVVM bundle via the `feature-screen-generator` skill) that observes the
local DB Flow and lists every subject upload grouped/badged by `SyncState`, with **Retry** (FAILED)
and **Delete** (PENDING/FAILED/MERGED) actions. Added as a third tab in `AuthedShell`.

### Changes Required

#### 1. Feature bundle — `presentation/feature/subject/uploads/`
- `SubjectUploadsAction`: `Retry(localId)`, `Delete(localId)`, `BackClick` (and `Refresh` if a
  pull-to-refresh / manual "Sincronizar ahora" button is added → calls `SyncSubjectsUseCase` via
  coordinator).
- `SubjectUploadsState`: `uploads: List<SubjectUploadUi>` (domain + display badge + relative time),
  derived empty-state flag.
- `SubjectUploadsViewModel`: collects `ObserveSubjectUploadsUseCase()` into state via
  `stateIn(viewModelScope, WhileSubscribed(5_000), ...)`; `onAction` calls retry/delete use cases +
  `syncCoordinator.requestSync()`.
- `SubjectUploadsScreen`: list of rows; each row shows name + DNI, a colored state badge
  (`PENDING`=muted, `SYNCING`=spinner, `SYNCED`=primary, `MERGED`=secondary "ya existía",
  `FAILED`=destructive + `errorMessage`), and a trailing action menu (Retry/Delete) per allowed state.
  Use KomoUI `styles` + existing components; empty state when no uploads.
- `SubjectUploadsDestination` + `SubjectUploadsNavigation`: `Back` only (it's a tab root).

#### 2. Bottom nav + route
- `core/design_system/components/navbar/AlBottomNavTab`: add `Uploads` tab (needs a MingCute icon —
  use the `add-mingcute-icon` skill, e.g. `ic_mingcute_upload_line`, then rebuild).
- `Route.kt`: add `@SerialName("subject_uploads") data object SubjectUploads : Route()`.
- `AuthedShell.kt`: add the tab branch + a `composable<Route.SubjectUploads> { SubjectUploadsScreenRoot(...) }`
  in the inner `NavHost`, mirroring Home/Profile.
- `viewModelsModule`: `viewModelOf(::SubjectUploadsViewModel)`.

#### 3. Reachability
Keep the existing Home "Cargar sujeto" entry to create. The Uploads tab makes state observable. The
create-success banner's optional "Ver subidas" switches to the Uploads tab.

### Success Criteria

#### Automated Verification:
- [x] Common compilation passes: `./scripts/compile.sh`
- [x] Android assembles: `./gradlew :composeApp:assembleDebug --no-daemon`

#### Manual Verification:
- [ ] Create offline → appears as `PENDING` in Uploads instantly.
- [ ] Go online → row transitions `SYNCING` → `SYNCED` without manual action.
- [ ] Duplicate DNI → row becomes `MERGED` ("ya existía") showing the canonical id.
- [ ] Forced 400/403 → `FAILED` with a Spanish reason; **Retry** re-queues, **Delete** removes it.
- [ ] Empty state shows when there are no uploads.

---

## Phase 7: Lifecycle/connectivity triggers + tests

### Overview
Wire the sync triggers (foreground, connectivity, periodic-while-foregrounded) and add focused unit
tests for the highest-risk logic: the state-transition engine and the API outcome mapping.

### Changes Required

#### 1. Start the coordinator + foreground/periodic triggers
- Resolve `SyncCoordinator` once at app start and call `start()` (collects connectivity). Options:
  inject into `AppViewModel` and call in `init` (consistent with the existing auth-bootstrap pattern),
  **or** a small `SyncTriggers()` composable mounted in `App.kt`/`AuthedShell` using
  `LifecycleStartEffect { coordinator.requestSync() }` for foreground and a `LifecycleResumeEffect`
  with a `while (isActive) { delay(15.min); coordinator.requestSync() }` loop for periodic. Recommended:
  `start()` from `AppViewModel.init`; `LifecycleStartEffect`/periodic from a composable in `AuthedShell`
  (sync only matters when authed).
- Trigger a sync on successful enqueue (already done in Phase 5 VM).

#### 2. Tests — `commonTest`
- `SubjectUploadOutcomeTest` (Ktor `MockEngine`): assert 201→Created, 200→Created, 409+canonical→
  Duplicate, 409-without-extension→Retryable, 400→InvalidData, 401→Unauthorized, 403→Forbidden,
  connection failure→Retryable.
- `SubjectSyncEngineTest`: with a **fake** `SubjectLocalStore` + fake `SubjectApi`, assert each
  outcome maps to the correct state transition and `SyncSummary` counts; assert idempotent replay
  (200) lands `SYNCED`; assert 401/network keep rows `PENDING` and **bump `attemptCount`**; assert a row
  at `attemptCount == MAX_SYNC_ATTEMPTS - 1` hitting `Retryable` **dead-letters to `FAILED`**; assert
  `isEligibleAt` gates a freshly-bumped row out and lets it back in after the backoff window;
  assert `resetStuckSyncing()` flips a `SYNCING` row back to `PENDING` (crash recovery).
- `CreateSubjectViewModelTest` (Turbine): enqueue success clears form + sets confirmation; validation
  blocks enqueue.
- (Optional) `androidUnitTest` `SubjectLocalStoreTest` with `JdbcSqliteDriver(IN_MEMORY)`: insert →
  `observeAll` emits; markSynced/markFailed/delete behave; `selectSyncable` filters correctly.

### Success Criteria

#### Automated Verification:
- [x] Common compilation passes: `./scripts/compile.sh`
- [x] Unit tests pass: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:testDebugUnitTest --no-daemon` (or `allTests`)
- [x] Android assembles: `./gradlew :composeApp:assembleDebug --no-daemon` (+ iOS `compileKotlinIosSimulatorArm64`)

#### Manual Verification:
- [ ] Background the app while a row is `PENDING`, restore → it syncs on foreground.
- [ ] Toggle airplane mode off → pending rows sync within seconds (connectivity trigger).
- [ ] Leave the app foregrounded with a stuck-pending row (e.g. backend down) → periodic tick retries.
- [ ] Kill + relaunch with pending rows → rows persist and resume.
- [ ] iOS + Android both exhibit the above.

---

## Testing Strategy

### Unit (automated)
- Outcome mapping (Ktor `MockEngine`) and sync state-transition engine (fakes) — Phase 7. These cover
  the three contract changes and the dedup story without a network or real DB.
- ViewModel behavior via Turbine.
- DB queries via in-memory `JdbcSqliteDriver` in `androidUnitTest`.

### Manual (cross-platform, iOS + Android)
The Phase 5–7 manual criteria together exercise: offline create, auto-sync on connectivity/foreground,
idempotent replay (200), duplicate (409/MERGED), terminal failures (400/403), retry/delete, and
persistence across app restarts.

## Performance Considerations

- UI observes the DB via `asFlow().mapToList(Dispatchers.IO)` — recomposition is bounded by queue
  size (manager uploads are low-volume). No pagination needed initially; add a `LIMIT` if a manager
  accumulates thousands of un-pruned `SYNCED` rows.
- Sync is single-flight (`Mutex.tryLock`, overlapping triggers dropped) and sequential per row — fine
  for the expected volume; batch/parallel is a later optimization if needed. (extra's chat scheduler
  fans out sends in parallel per row; subjects POST one-at-a-time, so sequential is correct here.)
- **Pruning is shipped** (not deferred): the coordinator's hourly cleanup loop calls
  `pruneTerminal(7.days)` to drop old `SYNCED`/`MERGED` rows, mirroring extra's telemetry hourly
  `cleanup(maxEventAgeMs, maxRetries)`. Keeps the queue bounded without a manual sweep.
- All DB I/O on `Dispatchers.IO`; sync on `AppScope` (`Dispatchers.Default`) — off the main thread.

## Migration Notes

- **Fresh table, no migration.** SQLDelight is net-new; first launch creates `PendingSubject`. Future
  schema changes need `.sqm` migration files + `schemaOutputDirectory` (note for later, not now).
- **Backward-compatible wire change.** Sending `id` is safe against the old backend (it ignored unknown
  fields per the task's back-compat note); handling 200/409-enriched is additive. No server coordination
  needed to ship the client.
- **Reversible.** Revert by restoring `SubjectApi`/`SubjectRepositoryImpl`/`CreateSubjectViewModel` to
  their current online-only form and removing `data/db`, `data/subject/{local,sync}`, the Uploads
  feature, the SQLDelight plugin/deps, and the new DI bindings.

## Risks

- **PII at rest unencrypted.** The queue stores DNI/name/birth date in a plain SQLite file. Acceptable
  for v1 (device-local, app-sandboxed) but flag for an encryption follow-up (SQLCipher / per-platform).
- **iOS `NWPathMonitor` / Android callback correctness** in the hand-rolled observer — **de-risked** by
  the design change above: the observer is now only an *optimization* (instant resync on reconnect), not
  the backbone. Even if it never fires, the periodic foreground tick + exponential backoff guarantee
  progress. Evidence: the `../../extra` marketplace ships **no connectivity observer at all** and syncs
  reliably on poll + backoff alone. If the hand-rolled observer proves flaky we can simply drop it (not
  block the feature) or fall back to `jordond/connectivity`. Covered by the manual airplane-mode tests.
- **Periodic foreground polling** is a coarse backstop, not true background sync; long-offline sessions
  only sync once the app is foregrounded. True OS background sync is the named follow-up milestone.

## References

- Task contract (source of truth — supersedes the in-repo spec): `id` request field, `200`-as-success,
  enriched `409` with `canonical_subject` (described in the originating request; intended doc
  `docs/mobile/subjects-offline-sync.md` §3–4 does not yet exist in-repo).
- Current online-only implementation: `presentation/feature/subject/create_subject/*`,
  `data/subject/*`, `domain/{model,usecase,repository}/subject/*`.
- Prior feature plan (excluded offline): `thoughts/plans/create_subject_screen.md` (esp. §52).
- `expect/actual` persistence precedent: `data/auth/storage/TokenStore*.kt`, `data/device/DeviceIdStore*.kt`.
- DI surface: `di/{ClientsModule,RepositoriesModule,UseCasesModule,ViewModelsModule,InitKoin,Module}.kt`,
  `di/Modules.{android,ios}.kt`.
- HTTP client topology + authed-client (no `expectSuccess`): `data/network/HttpClientFactory.kt`.
- Authed shell / bottom nav (Uploads tab host): `app/AuthedShell.kt`.
- **Battle-tested outbox precedent — `/Users/juanduzac/dev/extra`** (sister CMP marketplace). Patterns
  adopted above: telemetry `BatchProcessor` (app-scoped flusher: `start()` guard, `resetStuckEvents()`
  on start, `flushMutex.tryLock()`, hourly `cleanup`), `EventQueueImpl` / `TelemetryEvent.sq`
  (`resetSendingToPending`, `selectPendingBatch ... retry_count < :maxRetries`, GC queries); chat
  `MessageScheduler.isEligibleAt` / `MessageRequest.nextRetryEligibleAtMs` (`MAX=5`,
  `minOf(30_000, 1_000 shl failedCount) + createdAt%1000` backoff+jitter), `MessageTimeoutController`
  (stuck-`sent` recovery), `ChatRepositoryImpl.sendMessageRequest` (mark-in-flight-before-the-call),
  two-DB SQLDelight setup (`build.gradle.kts` distinct `srcDirs`+`packageName`). **Notable divergences:**
  extra has **no connectivity observer** (poll+backoff only) and passes raw `now: Long` (we inject
  `now: () -> Long`); extra deletes permanent failures (we keep `FAILED` dead-letters for PII rows).
- Tooling (mid-2026, web-verified): SQLDelight 2.3.2 (`app.cash.sqldelight`); `kotlin.uuid.Uuid`
  (stdlib, Kotlin ≥2.0.20); connectivity via hand-rolled `expect/actual` (alt:
  `dev.jordond.connectivity:connectivity-device:2.4.1`); CMP lifecycle
  `LifecycleStartEffect`/`LifecycleResumeEffect`.
</content>
</invoke>
