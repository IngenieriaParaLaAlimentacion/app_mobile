# Measurement Screen Offline Reads — Implementation Plan

## Overview

The measurement screen's two reads are live HTTP with no offline fallback, which breaks the full
offline chain (create subject offline → measure offline → both sync later):

- **Establishment picker** (`MeasurementViewModel.loadEstablishments` → `GetMyEstablishmentsUseCase`
  → `ProfileRepositoryImpl.getMyEstablishments` → `ProfileApi.getMyEstablishments`) is remote-only.
  Offline it fails, `selectedEstablishment` stays `null`, and submit is hard-blocked
  (`MeasurementViewModel.kt:138-179`).
- **Subject picker** (`SelectSubjectViewModel` → `GetSubjectsUseCase` → `SubjectQueryRepositoryImpl`
  → `SubjectApi.list`) is remote-only and reads a *different* store than where offline subjects live
  (`PendingSubject`). A subject created offline is invisible in the picker; offline the call errors
  to an empty list.

This plan implements the offline strategy already designed and locked in the grilling session
(`thoughts/grilling/adr-001..004` + `offline-reads-synthesis.md`):

- **Establishments** → SQLDelight read-through reference cache (cache raw, filter at read, warm at
  authed-shell entry, clear on logout).
- **Subjects** → union read (remote ∪ local `PendingSubject`, deduped by id, hiding MERGED/FAILED)
  **plus** measurement resolve-at-read gating (the subject↔measurement sync dependency).

## Current State Analysis

- **Schema version is 2.** `composeApp/src/commonMain/sqldelight/databases/` holds only `2.db`;
  migrations dir holds only `1.sqm` (v1→v2). `verifyMigrations.set(true)`
  (`composeApp/build.gradle.kts:12-24`) and a Gradle hook making every Kotlin compile depend on
  `verifyCommonMainAppDatabaseMigration` (`build.gradle.kts:140-144`) fail the build on any `.sq`
  schema change without a matching `.sqm`.
- **DI** (`di/`): `databaseModule` provides `AppDatabase` + `pendingSubjectQueries` +
  `pendingMeasurementQueries` (`DatabaseModule.kt:12-14`); `repositoriesModule` wires every
  repo/store (`RepositoriesModule.kt:48-72`); `SyncCoordinator` drives
  `listOf(SubjectRepository, MeasurementRepository)` in that order (`DatabaseModule.kt:16-18`).
- **Backend contract (confirmed):** both `SubjectRequest` and `MeasurementRequest` accept a
  client-supplied `id: UUID` → server PK (idempotent replay → 200). `measurement.subject_id` is a
  non-nullable FK; a missing subject → **404, not deferred**. DNI collision on subject create → 409
  with the canonical subject. So sync must be ordered (subject lands before its measurement), and
  the only id reconciliation is the 409/DNI-merge case.

### Key Discoveries

- `ProfileRepositoryImpl.getMyEstablishments()` (`data/profile/ProfileRepositoryImpl.kt:28-38`) is
  pure-network; non-managers get 403 → mapped to `emptyList()`. `MeasurementViewModel` already
  filters to `Approved` (`MeasurementViewModel.kt:113`), so the repo can cache the **raw** list.
- `SubjectQueryRepositoryImpl` (`data/subject/SubjectQueryRepositoryImpl.kt`) only wraps
  `SubjectApi`; `searchSubjects` is a one-shot paginated `Result<SubjectPage>` (not a Flow).
- `SubjectUpload` → `SubjectSummary` maps field-for-field; only `id` needs a bridge:
  `id = canonicalId ?: localId` (`SubjectUpload.kt`, `SubjectSummary.kt`). `localId` == server PK in
  the happy path, so dedup by `id` is exact.
- The subject picker renders title/subtitle only — **no per-row status field** exists
  (`SelectSubjectScreen.kt:137-148`). (v1 ships no badge, per decision.)
- `PendingSubject.sq` already exposes `selectById` → resolving the subject at measurement-sync time
  needs **no measurement-schema migration**; do it in the repository layer.
- `toPendingUpload` writes `subject.id` into `PendingMeasurement.subjectId`
  (`MeasurementUploadMapper.kt:37-56`); `toRequest()` copies it verbatim into `subject_id`
  (`MeasurementUploadMapper.kt:9-16`). That single line is the resolve interception point.
- `MeasurementApi.create` maps 404 → `NotFound` → instant `markFailed`
  (`MeasurementRepositoryImpl.kt:55-58`). Gating must keep a measurement from being *attempted*
  while its subject isn't server-side, or it dead-letters on a guaranteed 404.
- Auth-success funnels: sign-in (`AuthRepositoryImpl.kt:45`), storage bootstrap
  (`AuthRepositoryImpl.kt:33`), token refresh (`HttpClientFactory.kt:77`). `AuthedShell`'s
  `SyncTriggers` (`AuthedShell.kt:104-116`, `LifecycleStartEffect` + `koinInject`) is entered on all
  three — the uniform place to warm the cache.

## Desired End State

Offline, with a warm establishment cache and a just-created offline subject:

1. The establishment picker shows the last cached approved establishments; auto-select works; submit
   is **not** blocked.
2. The subject picker shows the offline-created subject (PENDING) alongside any cached/remote
   subjects.
3. A measurement created offline against that subject is queued and, once connectivity returns,
   syncs **after** its subject (resolving the subject id), with no spurious 404 dead-letter.

Verify: airplane-mode walkthrough (subject → measurement → reconnect → both SYNCED), plus the
409-merge and revoked-establishment dead-letter paths.

## What We're NOT Doing

- No subject sync-state badge in the picker (v1) — offline subjects render like any other row.
- No true stale-while-revalidate Flow refactor of `MeasurementViewModel` — cache-first read + a
  separate prefetch is the v1 shape (SWR is a future enhancement).
- No per-user keying of the establishment cache — single-user-at-a-time table, **cleared on
  logout** (achieves the same no-cross-account-leak guarantee with less surface).
- No change to the write outboxes (`PendingSubject`/`PendingMeasurement` enqueue paths) — they are
  already solid.
- No new measurement-schema migration (subject resolution lives in the repo, not a JOIN query).
- No pre-validation of a cached establishment's approval before submit — the measurement engine
  already dead-letters a revoked establishment safely (`Forbidden`/`NotFound` → `markFailed`).

## Implementation Approach

Three phases, bottom-up, each compiling. Phase 1 (establishments) is fully independent and ships
first — it alone unblocks offline submit. Phases 2 and 3 are the subject chain and **must land
together**: making an offline subject selectable (P2) is only safe once measurement gating (P3)
prevents the 404 dead-letter.

---

## Phase 1: Establishment read-through reference cache

### Overview
Add a `CachedEstablishment` SQLDelight table (migration v2→v3), an `EstablishmentLocalStore`, make
`ProfileRepository.getMyEstablishments()` cache-first, warm the cache at authed-shell entry, and
clear it on logout.

### Changes Required

#### 1. New SQLDelight table + migration
**File**: `composeApp/src/commonMain/sqldelight/ar/com/ingenieriaalimentacion/data/db/CachedEstablishment.sq` (new)
```sql
CREATE TABLE CachedEstablishment (
    id               TEXT NOT NULL PRIMARY KEY,
    name             TEXT NOT NULL,
    formattedAddress TEXT NOT NULL,
    latitude         REAL NOT NULL,
    longitude        REAL NOT NULL,
    placeId          TEXT NOT NULL,
    city             TEXT NOT NULL,
    province         TEXT NOT NULL,
    status           TEXT NOT NULL,   -- 'Approved'|'Pending'|'Rejected'|'Unknown'
    updatedAt        INTEGER NOT NULL
);

selectAll:
SELECT * FROM CachedEstablishment;

insert:
INSERT OR REPLACE INTO CachedEstablishment(id, name, formattedAddress, latitude, longitude,
    placeId, city, province, status, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

deleteAll:
DELETE FROM CachedEstablishment;
```

**File**: `composeApp/src/commonMain/sqldelight/ar/com/ingenieriaalimentacion/data/db/2.sqm` (new — migrates v2→v3)
```sql
CREATE TABLE IF NOT EXISTS CachedEstablishment (
    id               TEXT NOT NULL PRIMARY KEY,
    name             TEXT NOT NULL,
    formattedAddress TEXT NOT NULL,
    latitude         REAL NOT NULL,
    longitude        REAL NOT NULL,
    placeId          TEXT NOT NULL,
    city             TEXT NOT NULL,
    province         TEXT NOT NULL,
    status           TEXT NOT NULL,
    updatedAt        INTEGER NOT NULL
);
```
Then regenerate the snapshot (`3.db`):
`export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:generateCommonMainAppDatabaseSchema --no-daemon`
Commit `2.sqm` **and** the new `databases/3.db`.

#### 2. EstablishmentLocalStore (interface + SQLDelight impl)
**File**: `data/profile/local/EstablishmentLocalStore.kt` (new) — mirror `SubjectLocalStore` style.
```kotlin
interface EstablishmentLocalStore {
    suspend fun getAll(): List<EstablishmentWithStatus>
    suspend fun replaceAll(items: List<EstablishmentWithStatus>)   // transactional overwrite
    suspend fun clear()
}
```
**File**: `data/profile/local/DefaultEstablishmentLocalStore.kt` (new) — wraps the generated
`CachedEstablishmentQueries`, injected `now: () -> Long`, `dispatcher` defaulted (mirror
`DefaultSubjectLocalStore.kt:20-24`). `replaceAll` runs `queries.transaction { deleteAll(); items.forEach { insert(...) } }`.
Map `status` via `EstablishmentStatus.name` ↔ `enumValueOf`/`Unknown` fallback.

#### 3. Cache-first repository
**File**: `data/profile/ProfileRepositoryImpl.kt`
- Add ctor param `private val local: EstablishmentLocalStore`.
- `getMyEstablishments()` becomes cache-first: return cached when non-empty; otherwise network →
  `local.replaceAll(fresh)` → return fresh (preserve the existing 403→`emptyList()` handling at
  `:31-37`, and on a network failure with a non-empty cache, fall back to cache).
- Add `suspend fun refreshMyEstablishments(): Result<Unit>` — bypass cache, fetch, `replaceAll`,
  best-effort (swallow offline failure). Used by the prefetch.
- Add `suspend fun clearEstablishmentCache()` → `local.clear()` (or expose via the interface).

**File**: `domain/repository/ProfileRepository.kt` — add `refreshMyEstablishments()` and
`clearEstablishmentCache()` to the interface.

#### 4. Prefetch at authed-shell entry
**File**: `presentation/.../AuthedShell.kt` (the `SyncTriggers` composable, `:104-116`)
- On first `LifecycleStartEffect`, if `connectivity.isOnline`, launch
  `profileRepository.refreshMyEstablishments()` (via a new `RefreshMyEstablishmentsUseCase` or
  `koinInject`-ed repo), guarded by a remembered one-shot flag.

#### 5. Clear cache on logout
**File**: `app/AppViewModel.kt` — observe `sessionStatus`; on transition to
`SessionStatus.NotAuthenticated`, call `clearEstablishmentCache()` (inject the use case/repo). This
catches both explicit sign-out and refresh-failure logout uniformly.

#### 6. DI wiring
**File**: `di/DatabaseModule.kt` — add `single { get<AppDatabase>().cachedEstablishmentQueries }`
(after `:14`).
**File**: `di/RepositoriesModule.kt` — add `single<EstablishmentLocalStore> { DefaultEstablishmentLocalStore(queries = get(), now = { Clock.System.now().toEpochMilliseconds() }) }`;
change the `ProfileRepository` binding (`:72`) to `ProfileRepositoryImpl(api = get(), local = get())`.
**File**: `di/UseCasesModule.kt` — add `factory { RefreshMyEstablishmentsUseCase(get()) }` (+ a clear
use case if used).

### Success Criteria

#### Automated Verification:
- [x] Schema regenerated and migration verified: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:verifyCommonMainAppDatabaseMigration --no-daemon`
- [x] commonMain compiles: `./scripts/compile.sh`
- [x] Android build: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:assembleDebug --no-daemon`
- [x] `databases/3.db` and `2.sqm` exist and are committed.

#### Manual Verification:
- [ ] Online: open measurement screen → establishments load and cache is populated.
- [ ] Toggle airplane mode → reopen measurement screen → establishment picker still shows the last
      cached approved establishments; auto-select works; submit is not blocked.
- [ ] Cold cache + offline (fresh login already offline) → picker empty, profileError shown, Retry
      available (acceptable documented edge).
- [ ] Sign out → sign in as a different user → no previous user's establishments appear.

---

## Phase 2: Subject picker union read

### Overview
`SubjectQueryRepositoryImpl` unions remote `/subjects` results with local `PendingSubject` rows
(PENDING/SYNCING/SYNCED only), deduped by id, so an offline-created subject is selectable.

### Changes Required

#### 1. SubjectUpload → SubjectSummary mapper
**File**: `data/subject/mapper/SubjectUploadMapper.kt` — add:
```kotlin
internal fun SubjectUpload.toSummary(): SubjectSummary = SubjectSummary(
    id = canonicalId ?: localId,   // happy path: localId == server PK
    dni = dni, name = name, lastName = lastName, sex = sex,
)
```

#### 2. One-shot local read
**File**: `data/subject/local/SubjectLocalStore.kt` — add `suspend fun all(): List<SubjectUpload>`
(one-shot; `DefaultSubjectLocalStore` implements via `queries.selectAll().executeAsList().map { it.toDomain() }`).

#### 3. Union in the query repository
**File**: `data/subject/SubjectQueryRepositoryImpl.kt`
- Add ctor param `private val local: SubjectLocalStore`.
- In `searchSubjects`: fetch remote page as today. **On page 1 only**, read `local.all()`, keep
  states `Pending/Syncing/Synced`, map via `toSummary()`, filter by `query` client-side
  (name/lastName contains, or dni match) when `query` is non-blank, then prepend to the remote items
  and **dedup by `id`** (remote wins on collision). On pages > 1, return remote items unchanged.
- On remote failure (offline): if page 1, return a `SubjectPage` built from local rows only
  (`page = 1, totalPages = 1`) instead of failing; else propagate failure.

**File**: `di/RepositoriesModule.kt:60` — change to
`single<SubjectQueryRepository> { SubjectQueryRepositoryImpl(api = get(), local = get()) }`
(`SubjectLocalStore` is already registered at `:48`).

### Success Criteria

#### Automated Verification:
- [x] commonMain compiles: `./scripts/compile.sh`
- [x] Unit test: `SubjectQueryRepositoryImpl` unions + dedups + hides MERGED/FAILED + filters by
      query (fake `SubjectLocalStore` + fake `SubjectApi`).

#### Manual Verification:
- [ ] Create a subject offline → open the subject picker → the new subject appears (offline).
- [ ] Reconnect, let the subject sync, reopen the picker → it appears exactly once (no duplicate).
- [ ] A MERGED subject (force a DNI conflict) and a FAILED subject do not appear in the picker.

---

## Phase 3: Measurement resolve-at-read gating  (land with Phase 2)

### Overview
Before POSTing a queued measurement, resolve/gate against its subject's `PendingSubject` row in the
**repository** (no schema change): hold while the subject is PENDING/SYNCING, dead-letter if the
subject is FAILED, release-and-resolve (`canonicalId ?: localId`) when SYNCED/MERGED, and pass
through unchanged when no local subject row exists (pre-existing server subject).

### Changes Required

#### 1. Subject lookup by id
**File**: `data/subject/local/SubjectLocalStore.kt` — add `suspend fun find(localId: String): SubjectUpload?`
(`DefaultSubjectLocalStore` implements via the existing `PendingSubject.sq` `selectById`:
`queries.selectById(localId).executeAsOneOrNull()?.toDomain()`).

#### 2. Resolve/gate in measurement sync
**File**: `data/measurement/MeasurementRepositoryImpl.kt`
- Add ctor param `private val subjects: SubjectLocalStore`.
- In `syncPending()`, for each eligible upload, **before** `markSyncing`/`api.create`, resolve:
```kotlin
val subject = subjects.find(upload.subjectId)
val resolvedSubjectId = when {
    subject == null -> upload.subjectId                       // pre-existing server subject → pass through
    subject.syncState == SyncState.Synced ||
        subject.syncState == SyncState.Merged -> subject.canonicalId ?: upload.subjectId
    subject.syncState == SyncState.Failed -> {
        local.markFailed(upload.localId, "El sujeto no se pudo sincronizar.")
        failed++; return@forEach                              // dead-letter the measurement (decision)
    }
    else -> return@forEach                                    // Pending/Syncing → HOLD (not attempted, not counted)
}
local.markSyncing(upload.localId)
when (val outcome = api.create(upload.copy(subjectId = resolvedSubjectId).toRequest())) { ... }
```
  (`upload.copy(subjectId = …).toRequest()` feeds the resolved id into the POST without rewriting
  the DB row; `toRequest` stays unchanged.)

**File**: `di/RepositoriesModule.kt:64-71` — add `subjects = get()` to the
`MeasurementRepositoryImpl` constructor (`SubjectLocalStore` already registered at `:48`).

### Success Criteria

#### Automated Verification:
- [x] commonMain compiles: `./scripts/compile.sh`
- [x] Unit tests on `MeasurementRepositoryImpl.syncPending` with a fake `SubjectLocalStore`:
  - [x] no subject row → POSTs `subjectId` unchanged
  - [x] subject SYNCED → POSTs `localId`; subject MERGED → POSTs `canonicalId`
  - [x] subject PENDING/SYNCING → measurement held (not attempted, stays PENDING, attemptCount unchanged)
  - [x] subject FAILED → measurement `markFailed` with the Spanish reason
- [x] Android build: `./gradlew :composeApp:assembleDebug --no-daemon`

#### Manual Verification:
- [ ] Full offline chain: airplane mode → create subject → create measurement against it →
      reconnect → subject syncs first, then measurement syncs (both SYNCED), no spurious FAILED.
- [ ] 409 path: create an offline subject whose DNI already exists server-side → subject MERGED →
      its queued measurement POSTs with the canonical id and succeeds.
- [ ] Subject dead-letters (force 5 failures or a 400) → its dependent measurement dead-letters with
      "El sujeto no se pudo sincronizar."

---

## Testing Strategy

### Manual Testing Steps (end-to-end, airplane mode)
1. Online login → confirm establishment cache warms (Phase 1).
2. Airplane mode → create a subject → confirm it appears in the subject picker (Phase 2).
3. Still offline → create a measurement against it → submit succeeds (queued).
4. Reconnect → confirm subject SYNCED then measurement SYNCED in the Uploads screens (Phase 3).
5. Edge: DNI-conflict subject → MERGED → measurement still syncs (canonical id).
6. Edge: revoked establishment → measurement dead-letters with the access Spanish reason.

### Unit Tests
- `SubjectQueryRepositoryImpl` union/dedup/state-filter/query-filter/offline-fallback.
- `MeasurementRepositoryImpl.syncPending` resolve/hold/dead-letter/pass-through matrix.
- `DefaultEstablishmentLocalStore` replaceAll/getAll/clear (if a SQLDelight in-memory driver test
  harness exists; otherwise cover the repo cache-first logic with a fake store).

## Performance Considerations

- Establishment cache: tiny list, single `selectAll`; negligible.
- Subject union: one extra `selectAll` of `PendingSubject` on page-1 searches only; the queue is
  small (local pending rows). No N+1 — single read.
- Measurement gating: one `selectById` per syncable measurement per pass (local SQLite, small queue).

## Migration Notes

- Append-only migration per `sqldelight-migration-workflow` memory: add `2.sqm`, regenerate `3.db`,
  commit both; never lower `Schema.version`. The build-time guard
  (`verifyCommonMainAppDatabaseMigration`) will fail the build if the migration is missing.
- No data backfill — `CachedEstablishment` starts empty and warms on first online entry.

## References

- Grilling decisions: `thoughts/grilling/adr-001..004`, `thoughts/grilling/offline-reads-synthesis.md`,
  `thoughts/grilling/offline-reads-glossary.md`
- Prior offline work: `thoughts/plans/subjects_offline_first_sync.md`
- Memory: `extra-outbox-reference`, `sqldelight-migration-workflow`, `offline-reads-design-decisions`
- Key code: `ProfileRepositoryImpl.kt:28`, `SubjectQueryRepositoryImpl.kt`,
  `MeasurementRepositoryImpl.kt:42-72`, `MeasurementUploadMapper.kt:9-16`,
  `DatabaseModule.kt:12-19`, `RepositoriesModule.kt:48-72`, `AuthedShell.kt:104-116`,
  `build.gradle.kts:12-24,140-144`
