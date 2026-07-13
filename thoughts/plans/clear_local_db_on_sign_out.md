# Clear Local Database on Sign-Out Implementation Plan

## Overview

On explicit sign-out, wipe **all** local SQLDelight tables (`PendingSubject`,
`PendingMeasurement`, `CachedEstablishment`) so that when a different user logs in on the
same device they never inherit the previous user's offline data. Today only the
establishment cache is cleared; the two offline-first upload queues survive across users.

## Current State Analysis

- Sign-out is triggered from the Profile tab → `ProfileViewModel.performSignOut()` →
  `SignOutUseCase` → `AuthRepository.signOut()`
  (`data/auth/AuthRepositoryImpl.kt:51-61`). `signOut()` does best-effort server token
  invalidation, clears the secure `TokenStore`, clears Ktor's bearer cache, then flips the
  global session to `SessionStatus.NotAuthenticated(isSignOut = true)`.
- `AppViewModel` (`app/AppViewModel.kt:36-40`) collects **any** `NotAuthenticated` and calls
  `ClearEstablishmentCacheUseCase` — clearing **only** `CachedEstablishment`.
- The SQLDelight DB (`AppDatabase`, singleton, fixed file `ingenieria_alimentacion.db`, **not**
  user-scoped) holds three tables. `CachedEstablishment.sq` already has `deleteAll`
  (`CachedEstablishment.sq:22-23`). `PendingSubject.sq` and `PendingMeasurement.sq` have **no**
  full-table delete — only `deleteById` and `pruneTerminalOlderThan` (terminal-rows GC).
- Forced logout (token refresh failure) also lands on `NotAuthenticated`, but with
  `isSignOut = false` (`HttpClientFactory.kt:67-86`). The refresh path does **not** distinguish a
  genuine 401/invalid-grant from a transient network error — a brief connectivity loss currently
  logs the user out. **This is why we must NOT wipe on forced logout** (a field worker who briefly
  loses signal would lose un-synced offline measurements). This plan leaves the refresh path
  untouched and gates the wipe strictly on explicit sign-out.

### Key Discoveries:
- `databaseModule` already exposes `pendingSubjectQueries`, `pendingMeasurementQueries`,
  `cachedEstablishmentQueries`, and `AppDatabase` as Koin singletons (`di/DatabaseModule.kt:12-15`).
- `AppViewModel` is bound via `viewModelOf(::AppViewModel)` (`di/ViewModelsModule.kt:27`), so adding a
  constructor dependency only needs the new use case bound in `useCasesModule` — no `ViewModelsModule` edit.
- LocalStores default DB I/O to `Dispatchers.Default` (`DefaultEstablishmentLocalStore.kt:18`); the new
  cleaner follows the same convention.
- `SessionStatus.NotAuthenticated` carries `isSignOut: Boolean` — the exact flag we branch on.
- Adding a labeled query to a `.sq` file does **not** change the schema (no `CREATE`/`ALTER`/`DROP`), so
  `verifyMigrations.set(true)` (`build.gradle.kts:21`) requires **no** new `.sqm` migration and the
  committed `3.db` snapshot stays valid.

## Desired End State

Tapping "Cerrar sesión" and confirming wipes `PendingSubject`, `PendingMeasurement`, and
`CachedEstablishment` in a single transaction. A subsequent login by a different user shows an
empty history / empty pending queues / empty establishment picker. A forced logout
(refresh failure) behaves exactly as today: clears only the establishment cache, preserving
pending uploads.

## What We're NOT Doing

- **No** wipe on forced/refresh-failure logout (`isSignOut = false`) — keeps offline work safe.
- **No** changes to the token-refresh logic in `HttpClientFactory` (network-vs-401 distinction is a
  separate concern, explicitly deferred).
- **No** login-time "different user" guard / last-user tracking.
- **No** per-user database scoping or DB-file renaming.
- **No** changes to `TokenStore` / `DeviceIdStore` (already correctly cleared / intentionally kept).

## Implementation Approach

Add full-table `deleteAll` queries to the two pending-upload `.sq` files, then introduce a single
cross-table `DatabaseCleaner` abstraction (domain interface + SQLDelight impl) that wipes all three
tables atomically. Expose it through a `ClearLocalDataUseCase`, and have `AppViewModel` call it
**only** when `NotAuthenticated.isSignOut == true`, keeping the existing establishment-only clear for
the forced-logout branch.

---

## Phase 1: Add full-table delete queries

### Overview
Give the two pending-upload tables a `deleteAll` query (the establishment table already has one).

### Changes Required:

#### 1. PendingSubject queries
**File**: `composeApp/src/commonMain/sqldelight/ar/com/ingenieriaalimentacion/data/db/PendingSubject.sq`
**Changes**: Append a `deleteAll` query.

```sql
deleteAll:
DELETE FROM PendingSubject;
```

#### 2. PendingMeasurement queries
**File**: `composeApp/src/commonMain/sqldelight/ar/com/ingenieriaalimentacion/data/db/PendingMeasurement.sq`
**Changes**: Append a `deleteAll` query.

```sql
deleteAll:
DELETE FROM PendingMeasurement;
```

### Success Criteria:

#### Automated Verification:
- [x] SQLDelight interfaces regenerate without error: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew generateCommonMainAppDatabaseInterface --no-daemon`
- [x] Migration verification still passes (no schema change): `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew verifySqlDelightMigration --no-daemon`

#### Manual Verification:
- [x] Generated `PendingSubjectQueries` / `PendingMeasurementQueries` now expose `deleteAll()`.

---

## Phase 2: DatabaseCleaner abstraction + use case

### Overview
Introduce a domain `DatabaseCleaner` interface, a SQLDelight implementation that wipes all three
tables in one transaction, and a `ClearLocalDataUseCase` that wraps it.

### Changes Required:

#### 1. Domain interface
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/repository/DatabaseCleaner.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.domain.repository

/** Clears all locally persisted data. Implemented in the data layer over the SQLDelight database. */
interface DatabaseCleaner {
    suspend fun clearAll()
}
```

#### 2. SQLDelight implementation
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/db/SqlDelightDatabaseCleaner.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.data.db

import ar.com.ingenieriaalimentacion.domain.repository.DatabaseCleaner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Wipes every local table in a single transaction. Used on explicit sign-out. */
class SqlDelightDatabaseCleaner(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : DatabaseCleaner {
    override suspend fun clearAll(): Unit = withContext(dispatcher) {
        database.transaction {
            database.pendingSubjectQueries.deleteAll()
            database.pendingMeasurementQueries.deleteAll()
            database.cachedEstablishmentQueries.deleteAll()
        }
    }
}
```

#### 3. Use case
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/usecase/auth/ClearLocalDataUseCase.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.domain.usecase.auth

import ar.com.ingenieriaalimentacion.domain.repository.DatabaseCleaner

/** Wipes all local data on explicit sign-out so a new user never sees the previous user's data. */
class ClearLocalDataUseCase(private val cleaner: DatabaseCleaner) {
    suspend operator fun invoke() = cleaner.clearAll()
}
```

#### 4. DI wiring
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/DatabaseModule.kt`
**Changes**: Bind the cleaner (add `DatabaseCleaner` + `SqlDelightDatabaseCleaner` imports).

```kotlin
single<DatabaseCleaner> { SqlDelightDatabaseCleaner(database = get()) }
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/UseCasesModule.kt`
**Changes**: Add the use case factory (add import).

```kotlin
factory { ClearLocalDataUseCase(get()) }
```

### Success Criteria:

#### Automated Verification:
- [x] Kotlin compiles: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:compileCommonMainKotlinMetadata --no-daemon`

#### Manual Verification:
- [ ] Koin graph resolves at app start (no missing-binding crash).

---

## Phase 3: Branch the logout teardown in AppViewModel

### Overview
On `NotAuthenticated`, wipe everything for explicit sign-out; keep establishment-only clear for
forced logout.

### Changes Required:

#### 1. AppViewModel
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/AppViewModel.kt`
**Changes**: Inject `ClearLocalDataUseCase`; branch on `isSignOut`.

```kotlin
class AppViewModel(
    initializeAuth: InitializeAuthUseCase,
    observeSessionStatus: ObserveSessionStatusUseCase,
    syncCoordinator: SyncCoordinator,
    clearEstablishmentCache: ClearEstablishmentCacheUseCase,
    clearLocalData: ClearLocalDataUseCase,
) : ViewModel() {
    // ...
    init {
        viewModelScope.launch { initializeAuth() }
        syncCoordinator.start()
        // Explicit sign-out wipes ALL local data so the next user starts clean; a forced
        // (refresh-failure) logout only drops the establishment cache, preserving un-synced uploads.
        viewModelScope.launch {
            sessionStatus
                .filterIsInstance<SessionStatus.NotAuthenticated>()
                .collect { status ->
                    if (status.isSignOut) clearLocalData() else clearEstablishmentCache()
                }
        }
    }
}
```

### Success Criteria:

#### Automated Verification:
- [x] Kotlin compiles: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:compileCommonMainKotlinMetadata --no-daemon`

#### Manual Verification:
- [ ] Sign out → log in as a **different** user → Historia/uploads lists and establishment picker are empty.
- [ ] Create offline subjects/measurements while airplane-mode → sign out → they are gone after re-login.
- [ ] Forced logout simulation (let the token expire / refresh fail) → pending uploads still present after re-login as the **same** user.

---

## Phase 4: Tests

### Overview
Cover the new use case, the cleaner, and the AppViewModel branch per the project's testing rules
(kotlin.test + in-memory `JdbcSqliteDriver` + Turbine).

### Changes Required:

#### 1. Cleaner integration test
**File**: `composeApp/src/commonTest/kotlin/.../data/db/SqlDelightDatabaseCleanerTest.kt` (new)
Use `JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)` + `AppDatabase.Schema.create(driver)`, insert one
row into each of the three tables, call `clearAll()`, assert all three `selectAll()` are empty.

#### 2. Use case test
**File**: `composeApp/src/commonTest/kotlin/.../domain/usecase/auth/ClearLocalDataUseCaseTest.kt` (new)
Fake `DatabaseCleaner`; assert `invoke()` calls `clearAll()` once.

#### 3. AppViewModel branch test
**File**: `composeApp/src/commonTest/kotlin/.../app/AppViewModelTest.kt` (new or extend)
Drive a fake session-status flow; assert `NotAuthenticated(isSignOut = true)` invokes the
clear-local-data path and `isSignOut = false` invokes only the establishment clear. (Use fakes for
both use cases.)

### Success Criteria:

#### Automated Verification:
- [x] Tests pass: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:jvmTest --no-daemon` (or the project's commonTest task)

#### Manual Verification:
- [ ] Coverage for the new use case + cleaner meets the 80% bar.

---

## Testing Strategy

### Manual Testing Steps:
1. Log in as user A, create subjects + measurements (some offline), confirm they appear.
2. Sign out via Profile → "Cerrar sesión" → confirm.
3. Log in as user B → verify empty history, empty pending uploads, empty establishment picker.
4. Repeat but trigger a forced logout (token refresh failure) instead of sign-out → confirm pending
   uploads survive after re-login as the same user.

## Performance Considerations

Single transaction over three small tables on a background dispatcher — negligible. Runs once per
sign-out.

## Migration Notes

No schema change → no `.sqm` migration, no bump of the committed `3.db` snapshot. Existing installs
keep their data until the next explicit sign-out.

## Edge Cases / Notes

- **In-flight sync at sign-out:** the bearer token is already cleared, so any racing
  `SyncCoordinator` request 401s; a late `markSynced`/`markFailed` UPDATE targets a deleted row by id
  → harmless no-op. No coordinator changes needed.
- **`resetPassword`** (`AuthRepositoryImpl.kt:63-70`) calls `signOut()` with the explicit-sign-out
  status, so it will also wipe local data — desired (forces a fully clean re-login).

## References

- Sign-out flow: `data/auth/AuthRepositoryImpl.kt:51-61`, `app/AppViewModel.kt:36-40`
- DB + DI: `di/DatabaseModule.kt:12-15`, `di/UseCasesModule.kt`, `di/ViewModelsModule.kt:27`
- Existing full-clear pattern: `CachedEstablishment.sq:22-23`, `DefaultEstablishmentLocalStore.kt:47-49`
- Forced-logout path (left untouched): `data/network/HttpClientFactory.kt:67-86`
