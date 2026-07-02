# Server-Backed Activity Feed (History + Home) Implementation Plan

## Overview

Integrate `GET /api/v1/users/me/activity` into `HistoryHubScreen` (Sujetos/Mediciones segments) and
Home's recent-activity list, so both surfaces show a merged, paginated, date-descending ledger —
local sync-queue rows unioned with server-truth history — instead of today's 100%-local view that
silently loses everything past `SyncCoordinator`'s 7-day prune.

This plan implements the decisions locked in the grilling session at `thoughts/grilling/` (ADR-001
through ADR-011, `activity-feed-synthesis.md`, `activity-feed-glossary.md`). Read those first for
the *why*; this document is the *how*.

## Current State Analysis

- `HomeViewModel`/`HomeStateMapper` (`presentation/feature/home/`) and
  `SubjectUploadsViewModel`/`MeasurementUploadsViewModel`
  (`presentation/feature/subject/uploads/`, `presentation/feature/measurement/uploads/`) all derive
  their lists purely from `SubjectRepository.observeUploads()` / `MeasurementRepository.observeUploads()`
  — a reactive `Flow` over the local `PendingSubject`/`PendingMeasurement` SQLDelight tables, no
  pagination, no network call.
- `SyncCoordinator.pruneTerminal(olderThan = 7.days)` (`data/subject/sync/SyncCoordinator.kt:38`)
  deletes SYNCED/MERGED/FAILED rows older than 7 days on an hourly loop — the root cause this plan
  addresses.
- The only existing pagination pattern is `SelectSubjectViewModel` / `GetSubjectsUseCase` /
  `SubjectQueryRepositoryImpl` (subject picker) — manual page/totalPages state + `LoadMore` action,
  union-reads the local queue into page 1, "remote wins" on id collision. This plan reuses the same
  shape but with the opposite dedup direction (ADR-009: **local wins**, not remote).
- The only existing local-only cache table is `CachedEstablishment` (`data/profile/local/`) — a
  small, bounded reference-data cache using full delete-then-reinsert (`replaceAll`). This plan's
  new table is a **different shape**: capped rolling window, not bounded-domain wholesale overwrite.
- SQLDelight schema is at **version 3** (`PendingSubject`, `PendingMeasurement`, `CachedEstablishment`;
  migrations `1.sqm`, `2.sqm`; snapshots `2.db`, `3.db`). A new table requires `3.sqm` + a regenerated
  `4.db`.
- `SqlDelightDatabaseCleaner.clearAll()` (`data/db/SqlDelightDatabaseCleaner.kt`) hardcodes each
  table's `deleteAll()` call inside one transaction — a new table must be added there explicitly or
  it silently survives sign-out.
- `SubjectUpload` (`domain/model/subject/SubjectUpload.kt`) is "the full creation payload + sync
  state" — `birthDate`/`isRural`/`isIndigenous` are written once at `enqueue()` time
  (`DefaultSubjectLocalStore.kt:44-46`) and read again only by the create-subject form; **no display
  path reads them** (confirmed via full-codebase grep). `/me/activity`'s subject-item payload
  (`{id, dni, name, last_name, sex}`) doesn't carry them either — reusing `SubjectUpload` for a
  remote-sourced row is not possible without inventing nullable creation-payload fields.
- There is no existing discriminated-union JSON parsing precedent — confirmed via search. The
  closest analog (`SubjectUploadOutcome`) branches on HTTP status, not a JSON field.
- `AlertDto` already exists (`data/measurement/dto/MeasurementResponse.kt:7-11`, `@SerialName
  ("alert_type")`) and already maps to the domain `ClinicalAlert` via `AlertDto.toDomain()`
  (`data/measurement/mapper/MeasurementNetworkMapper.kt:19`) — reuse it directly for the new
  endpoint's `alerts` field rather than duplicating it.

### Key Discoveries

- `SyncCoordinator.kt:38` — the 7-day prune is the actual motivator, more than "offline support."
- `HomeStateMapper.kt:54-56` — Home's `recent` list already includes every sync state, not just
  in-flight ones, which is why Home also needs the merge (ADR-005), not just History.
- `SubjectQueryRepositoryImpl.kt` (full file) — the exact union-read shape to replicate for paging,
  with the dedup direction flipped per ADR-009.
- `AuthedShell.kt:116-136` (`SyncTriggers`) — exact insertion point for a new warm-up
  `LaunchedEffect`, mirroring the existing `refreshMyEstablishments` one-shot warm-up.
- `di/DatabaseModule.kt`, `data/db/SqlDelightDatabaseCleaner.kt` — exact wiring/wipe-list pattern a
  new table must join.
- `data/measurement/dto/MeasurementResponse.kt` / `MeasurementNetworkMapper.kt` — reuse `AlertDto`
  and its `toDomain()` mapper as-is.

## Desired End State

- `HistoryHubScreen`'s Sujetos and Mediciones tabs each show a paginated, date-descending merged
  ledger: local queue rows (any sync state) unioned with `/me/activity` history, deduped by id
  (local wins), with Retry/Delete rendered only for local-sourced rows.
- Home's top-4 recent list reads the same merged local source (queue ∪ capped cache) reactively,
  with no network call of its own; a background prefetch at `AuthedShell` entry keeps it warm.
- A new capped (~200 items per type) `CachedActivityItem` table persists fetched pages so both
  screens degrade gracefully offline past the 7-day queue-prune window.
- MERGED subject rows are never pruned by the hourly sweep.

**Verification (manual, see Testing Strategy):** register a subject, wait past a simulated 7-day
prune (or manually invoke prune with a test clock), confirm it still appears in History, sourced
from the cache; go offline after having opened History once online, confirm scrolling to a
previously-seen page still renders; trigger a DNI collision (409/MERGED), confirm the row is still
visible in History indefinitely.

## What We're NOT Doing

- Not touching `SubjectUpload`/`MeasurementUpload`/`NewSubject`/`NewMeasurement`, `enqueue()`, or
  `syncPending()` — the create/sync-queue path is untouched.
- Not introducing Paging3 or cursor-based pagination — reusing the existing manual load-more
  pattern; offset-pagination drift (no cursor in the API) is an accepted risk (ADR-010).
- Not renaming `SubjectUploadsViewModel`/`MeasurementUploadsViewModel`/`*ContentRoot` — deferred
  per the synthesis doc's residual-risk note; only their internals change in this plan.
- Not requesting a backend change for MERGED-subject visibility — handled entirely client-side via
  exempting it from the local prune (ADR-007).
- Not adding pull-to-refresh UI — page-1 refresh stays automatic (on screen open / periodic sync
  trigger), matching today's behavior.
- Not unifying History's two segments into one cross-type feed — they stay independently paginated
  by `type`; only Home unifies both kinds, same as today (presentation-layer merge only).

## Implementation Approach

Bottom-up, compiling standalone at each phase: wire layer (DTOs/API) → storage (capped cache table)
→ a one-line correctness fix (prune exemption) → the merge repositories (keystone) → the two
consuming screens. Every new piece reuses an existing convention rather than inventing one:
`SubjectApi`'s status-branching, `SubjectQueryRepositoryImpl`'s union-read, `SelectSubjectViewModel`'s
load-more, `DefaultEstablishmentLocalStore`'s local-store shape, `AlertDto`'s wire mapping.

---

## Phase 1: DTOs + API client

### Overview

Add the wire layer for `GET /api/v1/users/me/activity`, plus the new display-only domain models
(`SubjectHistoryEntry`/`MeasurementHistoryEntry`) that resolve the birthDate/isRural/isIndigenous
gap. No schema, no UI — fully unit-testable in isolation.

### Changes Required

#### 1. New DTOs

**File**: `data/activity/dto/ActivityResponse.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.data.activity.dto

import ar.com.ingenieriaalimentacion.data.measurement.dto.AlertDto
import ar.com.ingenieriaalimentacion.data.subject.dto.SexDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ActivitySubjectResponse(
    val id: String,
    val dni: Int,
    val name: String,
    @SerialName("last_name") val lastName: String,
    val sex: SexDto,
)

/** The subject nested inside a measurement item — richer than [ActivitySubjectResponse]. */
@Serializable
data class ActivityMeasurementSubjectResponse(
    val id: String,
    val dni: Int,
    val sex: SexDto,
    val name: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("birth_date") val birthDate: String,
)

@Serializable
data class ActivityEstablishmentResponse(
    val id: String,
    val name: String,
    val city: String,
    val province: String,
)

@Serializable
data class ActivityMeasurementResponse(
    val id: String,
    @SerialName("measured_at") val measuredAt: String,
    @SerialName("weight_kg") val weightKg: Double,
    @SerialName("height_cm") val heightCm: Double,
    val bmi: Double? = null,
    val subject: ActivityMeasurementSubjectResponse,
    val establishment: ActivityEstablishmentResponse,
    val alerts: List<AlertDto> = emptyList(),
    // `responsible` intentionally omitted — always the caller for /me/activity, never displayed.
)

/** Discriminated union on [type]: exactly one of [subject]/[measurement] is non-null. */
@Serializable
data class ActivityItemResponse(
    val type: String,
    @SerialName("occurred_at") val occurredAt: String,
    val subject: ActivitySubjectResponse? = null,
    val measurement: ActivityMeasurementResponse? = null,
)

@Serializable
data class PaginatedActivityResponse(
    val items: List<ActivityItemResponse>,
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    @SerialName("total_pages") val totalPages: Int,
)
```

#### 2. New Api class

**File**: `data/activity/ActivityApi.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.data.activity

import ar.com.ingenieriaalimentacion.data.activity.dto.PaginatedActivityResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess

/**
 * `/me/activity` — this user's own subjects+measurements, merged & paginated. Mirrors
 * [ar.com.ingenieriaalimentacion.data.subject.SubjectApi.list]'s status-check convention.
 */
class ActivityApi(private val client: HttpClient) {
    suspend fun list(page: Int, pageSize: Int, type: String? = null): PaginatedActivityResponse {
        val response = client.get("api/v1/users/me/activity") {
            parameter("page", page)
            parameter("page_size", pageSize)
            type?.let { parameter("type", it) }
        }
        if (!response.status.isSuccess()) error("activity list failed: ${response.status.value}")
        return response.body()
    }
}
```

#### 3. New domain models

**File**: `domain/model/subject/SubjectHistoryEntry.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.domain.model.subject

/**
 * One row in the subject ledger (History / Home) — either a still-queued [SubjectUpload] or a
 * reconciled `/me/activity` item. Deliberately smaller than [SubjectUpload]: a remote item never
 * carries birthDate/isRural/isIndigenous (not part of the activity payload, and never displayed
 * anywhere), so this type only carries what's ever rendered.
 */
data class SubjectHistoryEntry(
    val id: String,
    val dni: Int,
    val sex: Sex,
    val name: String,
    val lastName: String,
    val syncState: SyncState,     // always Synced for a remote-sourced entry
    val canonicalId: String?,     // MERGED local rows only
    val errorMessage: String?,    // FAILED local rows only
    val isLocal: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class SubjectHistoryPage(
    val items: List<SubjectHistoryEntry>,
    val page: Int,
    val totalPages: Int,
)
```

**File**: `domain/model/measurement/MeasurementHistoryEntry.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.domain.model.measurement

import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState

data class MeasurementHistoryEntry(
    val id: String,
    val subjectName: String,
    val subjectLastName: String,
    val subjectDni: Int,
    val establishmentName: String,
    val weightKg: Double,
    val heightCm: Double,
    val measuredAt: String,
    val syncState: SyncState,     // always Synced for a remote-sourced entry
    val errorMessage: String?,    // FAILED local rows only
    val bmi: Double?,
    val alerts: List<ClinicalAlert>,
    val isLocal: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class MeasurementHistoryPage(
    val items: List<MeasurementHistoryEntry>,
    val page: Int,
    val totalPages: Int,
)
```

#### 4. Mappers

**File**: `data/activity/mapper/ActivityMapper.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.data.activity.mapper

import ar.com.ingenieriaalimentacion.data.activity.dto.ActivityItemResponse
import ar.com.ingenieriaalimentacion.data.measurement.mapper.toDomain as alertToDomain
import ar.com.ingenieriaalimentacion.data.subject.mapper.toDomain as sexDtoToDomain
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import kotlinx.datetime.Instant

/** Sealed result of parsing one wire item, so callers can filter/branch without re-checking `type`. */
internal sealed interface ParsedActivityItem {
    data class SubjectEntry(val entry: SubjectHistoryEntry) : ParsedActivityItem
    data class MeasurementEntry(val entry: MeasurementHistoryEntry) : ParsedActivityItem
}

/** Validates the type/nesting invariant and maps to the matching entry. Throws on a malformed item. */
internal fun ActivityItemResponse.toParsedEntry(): ParsedActivityItem {
    val occurredAtMillis = Instant.parse(occurredAt).toEpochMilliseconds()
    return when (type) {
        "subject" -> {
            val s = checkNotNull(subject) { "activity item type=subject with no subject payload" }
            ParsedActivityItem.SubjectEntry(
                SubjectHistoryEntry(
                    id = s.id,
                    dni = s.dni,
                    sex = s.sex.sexDtoToDomain(),
                    name = s.name,
                    lastName = s.lastName,
                    syncState = SyncState.Synced,
                    canonicalId = null,
                    errorMessage = null,
                    isLocal = false,
                    createdAt = occurredAtMillis,
                    updatedAt = occurredAtMillis,
                ),
            )
        }
        "measurement" -> {
            val m = checkNotNull(measurement) { "activity item type=measurement with no measurement payload" }
            ParsedActivityItem.MeasurementEntry(
                MeasurementHistoryEntry(
                    id = m.id,
                    subjectName = m.subject.name,
                    subjectLastName = m.subject.lastName,
                    subjectDni = m.subject.dni,
                    establishmentName = m.establishment.name,
                    weightKg = m.weightKg,
                    heightCm = m.heightCm,
                    measuredAt = m.measuredAt,
                    syncState = SyncState.Synced,
                    errorMessage = null,
                    bmi = m.bmi,
                    alerts = m.alerts.map { it.alertToDomain() },
                    isLocal = false,
                    createdAt = occurredAtMillis,
                    updatedAt = occurredAtMillis,
                ),
            )
        }
        else -> error("unknown activity item type: $type")
    }
}
```

Also add, alongside the existing per-entity mappers:

**File**: `data/subject/mapper/SubjectMapper.kt` (edit — add function)
```kotlin
internal fun SubjectUpload.toHistoryEntry(): SubjectHistoryEntry = SubjectHistoryEntry(
    id = localId, dni = dni, sex = sex, name = name, lastName = lastName,
    syncState = syncState, canonicalId = canonicalId, errorMessage = errorMessage,
    isLocal = true, createdAt = createdAt, updatedAt = updatedAt,
)
```

**File**: `data/measurement/mapper/MeasurementUploadMapper.kt` (edit — add function)
```kotlin
internal fun MeasurementUpload.toHistoryEntry(): MeasurementHistoryEntry = MeasurementHistoryEntry(
    id = localId, subjectName = subjectName, subjectLastName = subjectLastName, subjectDni = subjectDni,
    establishmentName = establishmentName, weightKg = weightKg, heightCm = heightCm, measuredAt = measuredAt,
    syncState = syncState, errorMessage = errorMessage, bmi = bmi, alerts = alerts,
    isLocal = true, createdAt = createdAt, updatedAt = updatedAt,
)
```

#### 5. DI wiring

**File**: `di/ClientsModule.kt` (edit)
```kotlin
single { ActivityApi(client = get()) }   // alongside `single { SubjectApi(client = get()) }`
```

### Success Criteria

#### Automated Verification
- [x] Compiles: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:compileCommonMainKotlinMetadata --no-daemon`
- [x] Unit test `ActivityMapperTest`: decodes the endpoint's documented example JSON (both item
      shapes) via `Json.decodeFromString<PaginatedActivityResponse>` then `.toParsedEntry()`,
      asserting the right sealed variant and field mapping (including `alerts` via the reused
      `AlertDto`).
- [x] Unit test `ActivityMapperTest`: a `type="subject"` item with a non-null `measurement` (and
      null `subject`) throws on `toParsedEntry()` (the `checkNotNull` invariant).
- [x] Unit test `ActivityApiTest` (Ktor `MockEngine`): asserts `page`/`page_size` always sent,
      `type` only sent when non-null.

#### Manual Verification
- N/A — no UI in this phase.

---

## Phase 2: `CachedActivityItem` table (capped, per type)

### Overview

New SQLDelight table storing fetched activity pages, capped to the most recent ~200 rows **per
type** (subjects and measurements capped independently, since History's two segments paginate
independently). Schema bump v3 → v4.

### Changes Required

#### 1. New `.sq` file

**File**: `composeApp/src/commonMain/sqldelight/ar/com/ingenieriaalimentacion/data/db/CachedActivityItem.sq` (new)

```sql
CREATE TABLE CachedActivityItem (
    id               TEXT    NOT NULL PRIMARY KEY,  -- subject id or measurement id
    type             TEXT    NOT NULL,              -- 'SUBJECT' | 'MEASUREMENT'
    occurredAtMillis INTEGER NOT NULL,
    payloadJson      TEXT    NOT NULL
);

CREATE INDEX CachedActivityItem_type_occurred ON CachedActivityItem(type, occurredAtMillis DESC);

selectByType:
SELECT * FROM CachedActivityItem WHERE type = :type ORDER BY occurredAtMillis DESC;

selectPage:
SELECT * FROM CachedActivityItem WHERE type = :type
ORDER BY occurredAtMillis DESC LIMIT :limit OFFSET :offset;

upsert:
INSERT OR REPLACE INTO CachedActivityItem(id, type, occurredAtMillis, payloadJson)
VALUES (?, ?, ?, ?);

evictBeyondCap:
DELETE FROM CachedActivityItem
WHERE type = :type AND id NOT IN (
    SELECT id FROM CachedActivityItem WHERE type = :type ORDER BY occurredAtMillis DESC LIMIT :cap
);

deleteAll:
DELETE FROM CachedActivityItem;
```

#### 2. Migration + schema snapshot

**File**: `composeApp/src/commonMain/sqldelight/ar/com/ingenieriaalimentacion/data/db/3.sqm` (new)
```sql
-- v3 -> v4: adds CachedActivityItem, a capped rolling cache for /me/activity history.
CREATE TABLE IF NOT EXISTS CachedActivityItem (
    id               TEXT    NOT NULL PRIMARY KEY,
    type             TEXT    NOT NULL,
    occurredAtMillis INTEGER NOT NULL,
    payloadJson      TEXT    NOT NULL
);
CREATE INDEX IF NOT EXISTS CachedActivityItem_type_occurred ON CachedActivityItem(type, occurredAtMillis DESC);
```
Then regenerate: `./gradlew :composeApp:generateCommonMainAppDatabaseSchema` → produces `4.db`.
**Commit `3.sqm` and `4.db` together.**

#### 3. Local store

**File**: `data/activity/local/ActivityLocalStore.kt` (new)
```kotlin
package ar.com.ingenieriaalimentacion.data.activity.local

import kotlinx.coroutines.flow.Flow

enum class ActivityItemType { SUBJECT, MEASUREMENT }

data class CachedActivityRow(val id: String, val occurredAtMillis: Long, val payloadJson: String)

interface ActivityLocalStore {
    fun observe(type: ActivityItemType): Flow<List<CachedActivityRow>>
    suspend fun getPage(type: ActivityItemType, limit: Int, offset: Int): List<CachedActivityRow>
    suspend fun upsertPage(type: ActivityItemType, rows: List<CachedActivityRow>, cap: Int = DEFAULT_CAP)
    suspend fun clear()

    companion object { const val DEFAULT_CAP = 200 }
}
```

**File**: `data/activity/local/DefaultActivityLocalStore.kt` (new) — follows `DefaultSubjectLocalStore`'s
shape (`asFlow().mapToList(dispatcher)` for the reactive read, `withContext(dispatcher)` for writes,
one `queries.transaction { }` per `upsertPage` covering the upserts + the eviction so a crash
mid-batch can't leave the cap violated).

```kotlin
class DefaultActivityLocalStore(
    private val queries: CachedActivityItemQueries,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ActivityLocalStore {

    override fun observe(type: ActivityItemType): Flow<List<CachedActivityRow>> =
        queries.selectByType(type.name).asFlow().mapToList(dispatcher).map { it.map { row -> row.toDomain() } }

    override suspend fun getPage(type: ActivityItemType, limit: Int, offset: Int) = withContext(dispatcher) {
        queries.selectPage(type.name, limit.toLong(), offset.toLong()).executeAsList().map { it.toDomain() }
    }

    override suspend fun upsertPage(type: ActivityItemType, rows: List<CachedActivityRow>, cap: Int) =
        withContext(dispatcher) {
            queries.transaction {
                rows.forEach { queries.upsert(it.id, type.name, it.occurredAtMillis, it.payloadJson) }
                queries.evictBeyondCap(type.name, cap.toLong())
            }
        }

    override suspend fun clear(): Unit = withContext(dispatcher) { queries.deleteAll() }
}

private fun CachedActivityItem.toDomain() = CachedActivityRow(id, occurredAtMillis, payloadJson)
```

#### 4. Sign-out wipe (do this in the SAME phase, not later — easy to forget)

**File**: `data/db/SqlDelightDatabaseCleaner.kt` (edit)
```kotlin
database.transaction {
    database.pendingSubjectQueries.deleteAll()
    database.pendingMeasurementQueries.deleteAll()
    database.cachedEstablishmentQueries.deleteAll()
    database.cachedActivityItemQueries.deleteAll()   // new
}
```

#### 5. DI wiring

**File**: `di/DatabaseModule.kt` (edit)
```kotlin
single { get<AppDatabase>().cachedActivityItemQueries }
```

**File**: `di/RepositoriesModule.kt` (edit)
```kotlin
single<ActivityLocalStore> { DefaultActivityLocalStore(queries = get()) }
```

### Success Criteria

#### Automated Verification
- [x] `verifyMigrations` passes as part of: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./scripts/compile.sh`
- [x] `4.db` and `3.sqm` are both present and committed alongside the `.sq` change.
- [x] Unit test `DefaultActivityLocalStoreTest` (JDBC in-memory driver, per the kotlin testing rules):
      upserting 210 rows of one type leaves exactly 200, the 10 oldest (by `occurredAtMillis`) gone;
      the other type's rows are untouched by that eviction.
- [x] Unit test: `SqlDelightDatabaseCleanerTest` (or extend the existing one) — asserts
      `cachedActivityItemQueries` rows are gone after `clearAll()`.

#### Manual Verification
- N/A — no UI/repository wiring yet.

---

## Phase 3: MERGED-row prune exemption

### Overview

One-line query change — no schema/migration impact (this changes a named query's `WHERE` clause,
not a `CREATE TABLE`/`INDEX`, so `verifyMigrations` does not require a new `.sqm`).

### Changes Required

**File**: `composeApp/src/commonMain/sqldelight/ar/com/ingenieriaalimentacion/data/db/PendingSubject.sq` (edit)

Before:
```sql
pruneTerminalOlderThan:
DELETE FROM PendingSubject WHERE syncState IN ('SYNCED', 'MERGED') AND updatedAt < :cutoff;
```
After:
```sql
pruneTerminalOlderThan:
DELETE FROM PendingSubject WHERE syncState = 'SYNCED' AND updatedAt < :cutoff;
```

(`PendingMeasurement.sq`'s equivalent query already only targets `SYNCED` — no change needed there.)

### Success Criteria

#### Automated Verification
- [x] Compiles: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:compileCommonMainKotlinMetadata --no-daemon`
- [x] Existing/extended `DefaultSubjectLocalStoreTest` (or `SubjectRepositoryImplTest`): a MERGED row
      with `updatedAt` far in the past survives `pruneTerminalOlderThan`; a SYNCED row with the same
      age does not.

#### Manual Verification
- N/A.

---

## Phase 4: History repositories (keystone)

### Overview

The union-read/dedup/pagination logic, per ADR-004/006/009/010's shape. Two type-facing
repositories share the `ActivityApi` + `ActivityLocalStore` plumbing built in Phase 1–2.

### Changes Required

#### 1. Repository interfaces

**File**: `domain/repository/SubjectHistoryRepository.kt` (new)
```kotlin
package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryPage
import kotlinx.coroutines.flow.Flow

interface SubjectHistoryRepository {
    /** Reactive local-only merge (queue ∪ cache), no network — used by Home and as History's floor. */
    fun observeLocalMerged(): Flow<List<SubjectHistoryEntry>>

    /** Network fetch, cache upsert, then merge per ADR-009 (local wins on collision). */
    suspend fun loadPage(page: Int, pageSize: Int): Result<SubjectHistoryPage>
}
```

**File**: `domain/repository/MeasurementHistoryRepository.kt` (new) — identical shape, `MeasurementHistoryEntry`/`Page`.

#### 2. Repository implementations

**File**: `data/subject/SubjectHistoryRepositoryImpl.kt` (new)
```kotlin
package ar.com.ingenieriaalimentacion.data.subject

import ar.com.ingenieriaalimentacion.data.activity.ActivityApi
import ar.com.ingenieriaalimentacion.data.activity.local.ActivityItemType
import ar.com.ingenieriaalimentacion.data.activity.local.ActivityLocalStore
import ar.com.ingenieriaalimentacion.data.activity.mapper.ParsedActivityItem
import ar.com.ingenieriaalimentacion.data.activity.mapper.toParsedEntry
import ar.com.ingenieriaalimentacion.data.subject.local.SubjectLocalStore
import ar.com.ingenieriaalimentacion.data.subject.mapper.toHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryPage
import ar.com.ingenieriaalimentacion.domain.repository.SubjectHistoryRepository
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json

/**
 * Union-reads the local subject queue with `/me/activity` history. Mirrors
 * [SubjectQueryRepositoryImpl]'s shape, with the dedup direction flipped (ADR-009: local wins,
 * since queue rows are already fully denormalized and provably the same row as their server copy).
 */
class SubjectHistoryRepositoryImpl(
    private val api: ActivityApi,
    private val cache: ActivityLocalStore,
    private val queue: SubjectLocalStore,
    private val json: Json,
) : SubjectHistoryRepository {

    override fun observeLocalMerged(): Flow<List<SubjectHistoryEntry>> =
        combine(queue.observeAll(), cache.observe(ActivityItemType.SUBJECT)) { queued, cached ->
            merge(queued.map { it.toHistoryEntry() }, cached.map { it.toEntry() })
        }

    override suspend fun loadPage(page: Int, pageSize: Int): Result<SubjectHistoryPage> {
        val remote = runCatching {
            api.list(page = page, pageSize = pageSize, type = "subject").items
                .mapNotNull { (it.toParsedEntry() as? ParsedActivityItem.SubjectEntry)?.entry }
        }
        return remote.fold(
            onSuccess = { items ->
                cache.upsertPage(ActivityItemType.SUBJECT, items.map { it.toCachedRow() })
                if (page != 1) return@fold Result.success(SubjectHistoryPage(items, page, PLACEHOLDER_TOTAL_PAGES))
                val queued = queue.all().map { it.toHistoryEntry() }
                Result.success(SubjectHistoryPage(merge(queued, items), page, PLACEHOLDER_TOTAL_PAGES))
            },
            onFailure = { error ->
                // Offline: serve queue ∪ cache as a single self-contained page (page 1) or the
                // requested cached page (page > 1) instead of failing.
                val fallback = if (page == 1) {
                    merge(queue.all().map { it.toHistoryEntry() }, cachedPage(1, pageSize))
                } else {
                    cachedPage(page, pageSize)
                }
                if (fallback.isNotEmpty()) Result.success(SubjectHistoryPage(fallback, page, page))
                else Result.failure(error)
            },
        )
    }

    /** Local wins on id collision (ADR-009): queue rows first, remote/cache fills the rest. */
    private fun merge(local: List<SubjectHistoryEntry>, remote: List<SubjectHistoryEntry>): List<SubjectHistoryEntry> {
        val localIds = local.mapTo(mutableSetOf()) { it.id }
        return (local + remote.filterNot { it.id in localIds }).sortedByDescending { it.updatedAt }
    }

    private suspend fun cachedPage(page: Int, pageSize: Int): List<SubjectHistoryEntry> =
        cache.getPage(ActivityItemType.SUBJECT, limit = pageSize, offset = (page - 1) * pageSize)
            .map { it.toEntry() }

    // toCachedRow()/toEntry() de/serialize SubjectHistoryEntry <-> CachedActivityRow.payloadJson via [json].
}
```

`MeasurementHistoryRepositoryImpl` mirrors this exactly, substituting `MeasurementLocalStore`/
`MeasurementHistoryEntry`/`type = "measurement"`.

**Note on `totalPages`:** the real value comes from the endpoint's `total_pages`, which the code
above needs to thread through from `PaginatedActivityResponse` (elided as `PLACEHOLDER_TOTAL_PAGES`
above for brevity — the actual implementation reads `response.totalPages`, matching
`SubjectPage`/`PaginatedSubjectsResponse`'s existing convention).

#### 3. Use cases

**File**: `domain/usecase/subject/ObserveSubjectUploadsUseCase.kt` (edit — repoint)
```kotlin
class ObserveSubjectUploadsUseCase(private val repository: SubjectHistoryRepository) {
    operator fun invoke(): Flow<List<SubjectHistoryEntry>> = repository.observeLocalMerged()
}
```
(Same treatment for `ObserveMeasurementsUseCase` → `MeasurementHistoryRepository`.)

**File**: `domain/usecase/subject/GetSubjectHistoryPageUseCase.kt` (new)
```kotlin
class GetSubjectHistoryPageUseCase(private val repository: SubjectHistoryRepository) {
    suspend operator fun invoke(page: Int, pageSize: Int = DEFAULT_PAGE_SIZE): Result<SubjectHistoryPage> =
        repository.loadPage(page, pageSize)

    companion object { const val DEFAULT_PAGE_SIZE = 20 }
}
```
(Same for `GetMeasurementHistoryPageUseCase`.)

#### 4. DI wiring

**File**: `di/RepositoriesModule.kt` (edit)
```kotlin
single<SubjectHistoryRepository> {
    SubjectHistoryRepositoryImpl(api = get(), cache = get(), queue = get(), json = get(named("auth")))
}
single<MeasurementHistoryRepository> {
    MeasurementHistoryRepositoryImpl(api = get(), cache = get(), queue = get(), json = get(named("auth")))
}
```
(Reuses the existing `named("auth")` `Json` singleton from `RepositoriesModule.kt:34-36` —
`ignoreUnknownKeys = true` is exactly what a forward-compatible payload cache needs.)

**File**: `di/UseCasesModule.kt` (edit)
```kotlin
factory { ObserveSubjectUploadsUseCase(get()) }       // now takes SubjectHistoryRepository
factory { GetSubjectHistoryPageUseCase(get()) }
factory { ObserveMeasurementsUseCase(get()) }          // now takes MeasurementHistoryRepository
factory { GetMeasurementHistoryPageUseCase(get()) }
```

### Success Criteria

#### Automated Verification
- [x] Compiles: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:compileCommonMainKotlinMetadata --no-daemon`
- [x] Unit test `SubjectHistoryRepositoryImplTest` (fakes for `ActivityApi`/`ActivityLocalStore`/
      `SubjectLocalStore`, per the "fakes over mocks" testing rule):
      - id present in both queue and remote page 1 → the queue's copy wins (ADR-009).
      - a FAILED-only local row (no remote counterpart) still appears on page 1.
      - page 2 request never includes local queue rows, even if the queue has stale entries.
      - network failure on page 1 with a warm queue+cache → falls back to a merged local page
        instead of erroring.
      - network failure on page 2 → falls back to `cache.getPage(2, ...)`.
- [x] Same test matrix for `MeasurementHistoryRepositoryImplTest`.

#### Manual Verification
- N/A — screens not wired yet.

---

## Phase 5: `HistoryHubScreen` wiring (pagination + isLocal gating)

### Overview

Add manual load-more pagination (mirroring `SelectSubjectViewModel`) to both Uploads ViewModels,
switch their data source to the Phase 4 use cases, and gate Retry/Delete on `isLocal`.

### Changes Required

#### 1. State — add pagination fields

**File**: `presentation/feature/subject/uploads/SubjectUploadsState.kt` (edit)
```kotlin
data class SubjectUploadsState(
    val uploads: List<SubjectUploadUi> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val totalPages: Int = 1,
) {
    val isEmpty: Boolean get() = !isLoading && uploads.isEmpty()
    val canLoadMore: Boolean get() = page < totalPages && !isLoading && !isLoadingMore
}
```
(Same shape for `MeasurementUploadsState.kt`.)

#### 2. Action — add `LoadMore`

**File**: `presentation/feature/subject/uploads/SubjectUploadsAction.kt` (edit)
```kotlin
sealed interface SubjectUploadsAction {
    data class Retry(val localId: String) : SubjectUploadsAction
    data class Delete(val localId: String) : SubjectUploadsAction
    data object LoadMore : SubjectUploadsAction
}
```
(Same for `MeasurementUploadsAction.kt`.)

#### 3. ViewModel — reactive local floor + paginated refresh, mirroring `SelectSubjectViewModel`

**File**: `presentation/feature/subject/uploads/SubjectUploadsViewModel.kt` (edit)
```kotlin
class SubjectUploadsViewModel(
    observeUploads: ObserveSubjectUploadsUseCase,       // now SubjectHistoryEntry-based
    private val getPage: GetSubjectHistoryPageUseCase,
    private val retryUpload: RetrySubjectUploadUseCase,
    private val deleteUpload: DeleteSubjectUploadUseCase,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(SubjectUploadsState())
    val state = _state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), _state.value)

    init {
        // Reactive floor: local queue + whatever's cached, so the screen never shows blank while
        // the network call below is in flight, and stays populated fully offline.
        viewModelScope.launch {
            observeUploads().collect { entries ->
                _state.update { it.copy(uploads = entries.map { e -> e.toUi() }, isLoading = false) }
            }
        }
        // Page-1 network refresh on open — same trigger point as today's requestSync().
        viewModelScope.launch { refreshPage1() }
        syncCoordinator.requestSync()
    }

    fun onAction(action: SubjectUploadsAction) {
        when (action) {
            is SubjectUploadsAction.Retry -> retry(action.localId)
            is SubjectUploadsAction.Delete -> delete(action.localId)
            SubjectUploadsAction.LoadMore -> loadMore()
        }
    }

    private suspend fun refreshPage1() {
        getPage(page = 1).onSuccess { p ->
            _state.update { it.copy(page = p.page, totalPages = p.totalPages) }
        }
        // Failure is silent here: the reactive `observeUploads()` collector above already keeps
        // the screen populated from the local floor; page/totalPages simply don't advance.
    }

    private fun loadMore() {
        val current = _state.value
        if (!current.canLoadMore) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            getPage(page = current.page + 1).fold(
                onSuccess = { p ->
                    // Appends past the reactive floor's rows; dedup by id defends against the
                    // reactive collector re-emitting while a LoadMore is in flight.
                    _state.update { s ->
                        val seen = s.uploads.mapTo(mutableSetOf()) { it.localId }
                        s.copy(
                            uploads = s.uploads + p.items.map { it.toUi() }.filterNot { it.localId in seen },
                            page = p.page,
                            totalPages = p.totalPages,
                            isLoadingMore = false,
                        )
                    }
                },
                onFailure = { _state.update { it.copy(isLoadingMore = false) } },
            )
        }
    }

    private fun retry(localId: String) { viewModelScope.launch { retryUpload(localId); syncCoordinator.requestSync() } }
    private fun delete(localId: String) { viewModelScope.launch { deleteUpload(localId) } }
}
```
(Same shape for `MeasurementUploadsViewModel.kt`.)

#### 4. UI mapper — thread `isLocal` into `canRetry`/`canDelete`

**File**: `presentation/feature/subject/uploads/SubjectUploadUi.kt` (edit — change receiver type)
```kotlin
internal fun SubjectHistoryEntry.toUi(): SubjectUploadUi {
    val (label, tone) = when (syncState) { /* unchanged */ }
    val detail = buildString { /* unchanged, reads dni/errorMessage/canonicalId — all present */ }
    return SubjectUploadUi(
        localId = id,
        title = "$name $lastName",
        subtitle = detail,
        badgeLabel = label,
        badgeTone = tone,
        showSpinner = syncState == SyncState.Syncing,
        canRetry = isLocal && syncState == SyncState.Failed,
        canDelete = isLocal && (
            syncState == SyncState.Pending || syncState == SyncState.Failed || syncState == SyncState.Merged
        ),
    )
}
```
(Same treatment for `MeasurementUploadUi.kt`'s `MeasurementHistoryEntry.toUi()`, ANDing `isLocal`
into both `canRetry` and `canDelete`.)

No changes to `SubjectUploadsScreen.kt`/`MeasurementUploadsContent.kt` — they only read
`upload.canRetry`/`upload.canDelete`, already gated correctly by the mapper change above. Add a
"load more" trigger (bottom-reached callback on the existing `LazyColumn`, dispatching
`SubjectUploadsAction.LoadMore`/`MeasurementUploadsAction.LoadMore`) plus a small loading
indicator row when `isLoadingMore`.

### Success Criteria

#### Automated Verification
- [x] Compiles: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:compileCommonMainKotlinMetadata --no-daemon`
- [x] Unit test `SubjectUploadUiTest` (extend existing): a `SubjectHistoryEntry(isLocal = false,
      syncState = Synced)` maps to `canRetry = false, canDelete = false` regardless of `syncState`.
- [x] Unit test `SubjectUploadsViewModelTest` (Turbine, per kotlin testing rules): `LoadMore` appends
      without duplicating ids already present from the reactive floor.
- [x] Full build: `./gradlew :composeApp:assembleDebug --no-daemon`

#### Manual Verification
- [ ] Open History → Sujetos with the device online: shows local queue rows plus older history not
      in the queue, newest first.
- [ ] Scroll to the bottom → next page loads, a loading indicator shows briefly, no duplicate rows.
- [ ] A remote-sourced (older, not-in-queue) row shows no trash icon / no "Reintentar" button.
- [ ] A PENDING/FAILED local row still shows its action(s) as before.
- [ ] Turn off network, reopen History → still shows previously-seen pages; scrolling past the
      cached window (beyond ~200 items) simply stops loading more, no crash/error state.
- [ ] Repeat all of the above for the Mediciones segment.

*(Manual verification requires a running device/emulator against a live backend — not exercised in
this session; automated coverage above validates the underlying logic.)*

---

## Phase 6: Home wiring

### Overview

Home's `HomeViewModel` already consumes `ObserveSubjectUploadsUseCase`/`ObserveMeasurementsUseCase`
— after Phase 4's repoint, it receives merged entries automatically. This phase is mostly a
mechanical type-signature update in the pure mapper, plus the new warm-up prefetch.

### Changes Required

#### 1. Mapper signature update

**File**: `presentation/feature/home/HomeStateMapper.kt` (edit)
```kotlin
internal fun deriveState(
    subjects: List<SubjectHistoryEntry>,       // was List<SubjectUpload>
    measurements: List<MeasurementHistoryEntry>, // was List<MeasurementUpload>
    online: Boolean,
    now: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): HomeState {
    // body unchanged: every field read (syncState, createdAt, updatedAt, dni, name, lastName,
    // subjectName, weightKg, heightCm, bmi, alerts) exists identically on the new entry types.
    ...
}

private fun SubjectHistoryEntry.toRecentUi(): RecentActivityUi = /* unchanged body, receiver type only */
private fun MeasurementHistoryEntry.toRecentUi(): RecentActivityUi = /* unchanged body, receiver type only */
```
No behavior change beyond what the merged source now provides: a remote-sourced entry always has
`syncState = Synced`, so it correctly contributes to `AllSynced`/`lastSyncedAt` and never to
`pendingCount`/`syncingCount`/`failedCount` — exactly ADR-005's intended effect, no special-casing
needed in the mapper body itself.

#### 2. Warm-up prefetch at `AuthedShell` entry

**File**: `domain/usecase/activity/WarmActivityCacheUseCase.kt` (new)
```kotlin
package ar.com.ingenieriaalimentacion.domain.usecase.activity

import ar.com.ingenieriaalimentacion.domain.repository.MeasurementHistoryRepository
import ar.com.ingenieriaalimentacion.domain.repository.SubjectHistoryRepository

/** Best-effort prefetch of page 1 for both entity types, so Home has fresh data even before the
 *  user opens History this session. Mirrors [ar.com.ingenieriaalimentacion.domain.usecase.profile.RefreshMyEstablishmentsUseCase]. */
class WarmActivityCacheUseCase(
    private val subjects: SubjectHistoryRepository,
    private val measurements: MeasurementHistoryRepository,
) {
    suspend operator fun invoke() {
        subjects.loadPage(page = 1, pageSize = PAGE_SIZE)
        measurements.loadPage(page = 1, pageSize = PAGE_SIZE)
    }
    private companion object { const val PAGE_SIZE = 20 }
}
```

**File**: `app/AuthedShell.kt` (edit — add a fourth effect inside `SyncTriggers`)
```kotlin
@Composable
private fun SyncTriggers(
    coordinator: SyncCoordinator = koinInject(),
    connectivity: ConnectivityObserver = koinInject(),
    refreshMyEstablishments: RefreshMyEstablishmentsUseCase = koinInject(),
    warmActivityCache: WarmActivityCacheUseCase = koinInject(),   // new
) {
    LifecycleStartEffect(Unit) { coordinator.requestSync(); onStopOrDispose { } }
    LaunchedEffect(Unit) { if (connectivity.isOnline()) refreshMyEstablishments() }
    LaunchedEffect(Unit) { if (connectivity.isOnline()) warmActivityCache() }   // new
    LaunchedEffect(Unit) { while (isActive) { delay(15.minutes); coordinator.requestSync() } }
}
```

#### 3. DI wiring

**File**: `di/UseCasesModule.kt` (edit)
```kotlin
factory { WarmActivityCacheUseCase(get(), get()) }
```

### Success Criteria

#### Automated Verification
- [x] Compiles: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:compileCommonMainKotlinMetadata --no-daemon`
- [x] Existing `HomeStateMapperTest` suite passes after the receiver-type change (update fixture
      construction from `SubjectUpload(...)`/`MeasurementUpload(...)` to
      `SubjectHistoryEntry(...)`/`MeasurementHistoryEntry(...)`, `isLocal = true` for queue fixtures).
- [x] New `HomeStateMapperTest` case: a `SubjectHistoryEntry(isLocal = false, syncState = Synced)`
      older than 7 days (i.e. would be pruned if it were a queue row) still appears in `recent`.
- [x] Full build: `./gradlew :composeApp:assembleDebug --no-daemon`

#### Manual Verification
- [ ] Fresh sign-in on a clean device, online: Home's "Actividad reciente" shows past history
      (from `/me/activity`), not an empty state, even though the local queue is empty.
- [ ] Do something on this device, then simulate a 7-day-old local row (or just trust Phase 5's
      manual verification) — Home still reflects it via the cache after the queue prunes it.
- [ ] Register a subject that triggers a 409/MERGED — the row remains visible in Home/History
      indefinitely (not just 7 days).
- [ ] Sign out, sign back in as a different (or the same) user — no stale activity from the
      previous session appears (cache wiped by `SqlDelightDatabaseCleaner`).

*(Manual verification requires a running device/emulator against a live backend — not exercised in
this session; automated coverage above validates the underlying logic.)*

---

## Testing Strategy

### Unit tests (per phase, listed above)
- `ActivityMapperTest`, `ActivityApiTest` (Phase 1)
- `DefaultActivityLocalStoreTest`, extended `SqlDelightDatabaseCleanerTest` (Phase 2)
- Extended `DefaultSubjectLocalStoreTest`/`SubjectRepositoryImplTest` for prune exemption (Phase 3)
- `SubjectHistoryRepositoryImplTest`, `MeasurementHistoryRepositoryImplTest` (Phase 4) — the highest-
  value tests in this plan; they encode ADR-006/007/009's dedup and gating rules directly.
- `SubjectUploadUiTest`/`MeasurementUploadUiTest`, `SubjectUploadsViewModelTest`/
  `MeasurementUploadsViewModelTest` (Phase 5)
- Extended `HomeStateMapperTest` (Phase 6)

### Manual Testing Steps (end-to-end, after all phases)
1. Fresh install, sign in online → Home and History both populate from `/me/activity` immediately
   (via the Phase 6 warm-up), even with an empty local queue.
2. Create a subject and a measurement offline → both appear instantly in Home/History as PENDING,
   with Retry/Delete visible; go online, confirm they transition to Sincronizado and remain
   actionable (still local, per ADR-006 — SYNCED local rows keep their delete action).
3. Force a DNI collision (register a subject with a DNI that already exists on the server) →
   confirm the MERGED row is visible with "Ya existía"; confirm it's still visible after the local
   7-day-prune window would otherwise have removed it (ADR-007).
4. Scroll History past the first page online, confirm smooth load-more with no duplicate rows; go
   offline, reopen the screen, confirm previously-loaded pages still render from cache; scroll past
   the ~200-item cap, confirm it simply stops (no crash, no error banner).
5. Sign out explicitly → sign in as a different test user → confirm zero activity leakage from the
   previous account (both the queue and the new `CachedActivityItem` table are wiped).

## Performance Considerations

- `CachedActivityItem_type_occurred` index keeps `selectPage`/`evictBeyondCap` O(log n) rather than
  full scans, even as the cap is tuned upward later.
- The cap (200/type) bounds local storage growth regardless of account age — no unbounded SQLite
  growth over a multi-year install.
- `observeLocalMerged()`'s `combine()` re-runs the merge on every queue OR cache emission; the queue
  is small (7-day window) and the cache is capped, so this is cheap even on low-end Android devices
  targeted by this app.

## Migration Notes

- Schema v3 → v4: `3.sqm` + regenerated `4.db`, committed together, per the
  sqldelight-migration-workflow memory. No data migration needed for existing rows — the new table
  starts empty and fills in on first successful `/me/activity` fetch.
- No backend coordination needed — the endpoint is documented as already shipped.

## References

- Grilling session: `thoughts/grilling/activity-feed-synthesis.md`, `activity-feed-glossary.md`,
  `adr-005` through `adr-011` (this plan implements those decisions verbatim).
- Prior grilling session (offline reads): `thoughts/grilling/adr-001` through `adr-004` (the union-
  read and resolve-at-read patterns this plan reuses).
- Pattern precedents: `data/subject/SubjectQueryRepositoryImpl.kt`,
  `presentation/feature/subject/select_subject/SelectSubjectViewModel.kt`,
  `data/profile/local/DefaultEstablishmentLocalStore.kt`, `data/measurement/dto/MeasurementResponse.kt`.
