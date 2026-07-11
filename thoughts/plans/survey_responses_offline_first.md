# Survey Responses (Offline-First) Implementation Plan

## Overview

Add a new, fully offline-first "survey responses" feature for field workers: browse
surveys they're eligible to answer, fill out a dynamically-rendered question form,
submit offline-first, and later view/edit their own submissions (including from a
different device). This is greenfield — no survey code exists in the app today — built
on top of the backend endpoints shipped in PR #64, per the mobile integration guide
(`.context/attachments/iuel9i/pasted_text_2026-07-11_14-34-57.txt`).

## Current State Analysis

- **No survey code exists** anywhere in `composeApp/src` — domain, data, and
  presentation layers all start from zero for this feature.
- The app already has a proven offline-first "outbox" pattern used by `subjects` and
  `measurements`: a per-entity SQLDelight pending-queue table, a `SyncEngine`
  implementation per entity, and one shared `SyncCoordinator`
  (`data/subject/sync/SyncCoordinator.kt`) that drives every registered engine off
  connectivity changes, app-foreground, a 15-minute timer, and manual triggers. This is
  directly reusable.
- Every existing pending-queue table (`PendingSubject`, `PendingMeasurement`) uses a
  single UUID as both the SQLDelight primary key and the wire idempotency `id` — there
  is no precedent for an edit operation with its **own**, separate idempotency key
  distinct from the entity's id. This plan introduces that as new, purpose-built schema.
- The existing "activity feed" (`/me/activity`) pipeline (`data/activity/*`,
  `presentation/feature/home/HomeState*`) is hardcoded to exactly two item types
  (`subject`, `measurement`) at every layer — DTO, mapper, local-store enum, `HomeState`.
  There is no polymorphic extension point. Per user decision, this plan does **not**
  extend that pipeline; it builds a dedicated, separate "My survey responses" flow that
  happens to call the same `/me/activity?type=survey_response` URL with its own
  independent DTOs/mapper.
- Auth role is a raw `String` (`AuthUser.role`, `Profile.role`) with no `UserRole` enum
  and no existing role-gating UI pattern anywhere in the app (only a display-label
  helper, `roleLabel()`). This plan introduces the app's first role-gated UI affordance.
- The design system (`core/design_system/components/`) has `AlInput` (text/numeric),
  `AlSelectField` (tap-to-pick, e.g. establishment), `AlSegmentedControl`, and
  `AlSelectableCard` (a card wrapping a `Checkbox`) — but no reusable single-choice
  "chip group" or multi-choice "checkbox group" component. `CreateSubjectScreen`
  currently hand-rolls a one-off two-option selector (`SexSelector`/`SexOption`) rather
  than using a shared component. This plan generalizes that pattern into two new,
  reusable design-system components.
- `EstablishmentWithStatus` + `ProfileRepository.getMyEstablishments()`
  (cache-first, filtered to `Approved` at the call site — see `MeasurementViewModel
  .loadEstablishments()`) is the existing establishment-eligibility read path, and
  `GetCurrentEstablishmentUseCase`/`SetCurrentEstablishmentUseCase` supply the Home
  "current establishment" default. Both are reused as-is for survey submission.
- Ktor's shared `Auth` plugin (`HttpClientFactory.createAuthedClient`) already gives any
  new API built on the default authed `HttpClient` transparent 401 → refresh → retry —
  no new plumbing needed.
- Current SQLDelight schema is at **version 5** (`1.sqm`..`4.sqm`, snapshot `5.db`); the
  next migration is `5.sqm` (v5 → v6).

### Key Discoveries

- `domain/sync/SyncEngine.kt:11-20` — the interface every new queue implements
  (`syncPending`, `resetStuckSyncing`, `pruneTerminal`).
- `data/subject/sync/SyncCoordinator.kt:20-53` — single coordinator, single-flight via
  `Mutex.tryLock()`, hourly GC (`pruneTerminal(7.days)`), crash recovery on `start()`.
- `domain/model/subject/SubjectUpload.kt:34-38` — the exact backoff formula
  (`isEligibleAt`) to port verbatim: `minOf(30_000L, 1_000L shl attemptCount) +
  (createdAt % 1_000L)`, `MAX_SYNC_ATTEMPTS = 5`.
- `data/measurement/MeasurementRepositoryImpl.kt:50-71` — the FK-gate pattern (a
  measurement checks its subject's local `SyncState` before POSTing) that this plan
  ports for gating a survey-response **edit** on its parent response's sync state.
- `data/subject/SubjectApi.kt:23-47` — the status-code-branching convention (no
  `expectSuccess`, explicit `when (response.status.value)`) every new `Api` class
  follows.
- `data/activity/ActivityApi.kt:15-23` already accepts a `type` query param — reused
  as-is for `type=survey_response`, via a **new**, independent DTO/parsing path (not the
  existing `ActivityItemResponse`/`ActivityMapper`, which are hardcoded to
  subject/measurement and would silently drop a `survey_response` payload since
  `ignoreUnknownKeys = true` is set on the shared `Json` — `HttpClientFactory.json()`).
- `composeApp/build.gradle.kts:141-145` — `verifyMigrations` fails the build on any
  `.sq` change without a matching `.sqm`; ceremony is documented in memory
  (`sqldelight-migration-workflow`).
- `data/db/SqlDelightDatabaseCleaner.kt:13-21` — every new table must be added to the
  `clearAll()` transaction (logout wipe).

## Desired End State

A field worker can, fully offline after an initial online sync of reference data:
1. Browse surveys they're eligible to answer (generic + targeted at their approved
   establishments).
2. Fill out a survey's question form (single/multiple choice, numeric, text, date),
   pick an establishment (defaulting to Home's "current establishment"), and submit —
   the response is saved locally as `PENDING` and syncs automatically once connectivity
   returns, idempotently.
3. See a "My survey responses" list (their own submissions, synced + still-pending),
   open one to see its full answers (fetched live via `GET /survey-responses/{id}` when
   online, falling back to the local copy when offline and it's this device's own
   recent submission), and edit it — a full-replace edit queued with its own,
   independent idempotency key, syncing only after the original submission has synced.

Verification: manual walkthrough of the flow online, then in airplane mode, confirming
sync-on-reconnect; `field_worker`/`admin`/`superadmin` see the survey entry points on
Home, `analyst` does not; unit tests for both new sync engines' state-machine
transitions (permanent-fail vs. retry-then-dead-letter) and for the edit-coalescing
logic.

## What We're NOT Doing

- Not extending the existing Home activity feed (`ActivityItemResponse`/
  `ActivityMapper`/`HomeState`) to a third item type — the new "My survey responses"
  screen is a separate, dedicated flow (per user decision).
- Not persisting an in-progress, not-yet-submitted survey draft across app kill — only
  `ViewModel`-scoped state (survives rotation/backgrounding, lost on process death). Per
  user decision: "the survey is quick to respond," no draft persistence needed.
- Not building a calendar/date-picker UI component. `date`-type questions reuse this
  app's existing typed-date-with-mask idiom (`AlInput` + `DateVisualTransformation`,
  the same pattern `CreateSubjectScreen` uses for birth date) rather than introducing a
  new UI paradigm.
- Not client-side pre-filtering the establishment picker to a targeted survey's exact
  audience — the doc's own error table treats "establishment outside a TARGETED
  survey's audience" as a normal 400 to surface, same as any other validation error;
  the picker always offers the same approved-establishments list used by measurements.
- Not adding a `409`/`Duplicate`/`Merged` sync state for survey responses — the backend
  doc is explicit there's no per-survey uniqueness constraint, so every submission
  resolves to `Created` or an error; no dedup/conflict path like subjects' DNI merge.
- Not building a generic `UserRole` enum refactor across the whole app — only the
  minimal helper needed to gate the new survey entry points, since that's the extent of
  today's actual need and no other role-gating exists to consolidate with yet.

## Implementation Approach

Follow the existing offline-first template exactly where it fits (sync engine,
backoff, DI wiring, cache-then-fetch reads), and introduce new schema only where the
guide's edit semantics genuinely diverge from anything in the codebase (a second
idempotency key). Build data model → data layer → design-system primitives →
fill-a-survey UI → view/edit UI → navigation, so each phase is independently
compilable and testable before the next starts.

---

## Phase 1: Domain Layer

### Overview
Domain models, repository interfaces, and the role-gating helper — no I/O, no
Compose, no SQLDelight. Establishes the vocabulary every later phase builds on.

### Changes Required

#### 1. Survey models
**File**: `domain/model/survey/Survey.kt` (new)
```kotlin
package ar.com.ingenieriaalimentacion.domain.model.survey

enum class SurveyScope { Generic, Targeted }

/** Summary shape from GET /surveys/available — no questions. */
data class Survey(
    val id: String,
    val name: String,
    val scope: SurveyScope,
    val isActive: Boolean,
    val questionCount: Int,
    val createdAt: String,
)

enum class SurveyResponseType { SingleChoice, MultipleChoice, Numeric, Text, Date }

data class SurveyOption(val id: String, val text: String)

data class SurveyQuestion(
    val id: String,
    val text: String,
    val responseType: SurveyResponseType,
    val isRequired: Boolean,
    val options: List<SurveyOption>,
)

/** Full shape from GET /surveys/{id} — what the response form renders against. */
data class SurveyDetail(
    val id: String,
    val name: String,
    val scope: SurveyScope,
    val isActive: Boolean,
    val questions: List<SurveyQuestion>,
)
```

#### 2. Answer value model
**File**: `domain/model/survey/SurveyAnswerValue.kt` (new)
```kotlin
package ar.com.ingenieriaalimentacion.domain.model.survey

/** One answer, typed per the owning question's [SurveyResponseType]. Keyed by question id
 *  in [ar.com.ingenieriaalimentacion.domain.model.survey.NewSurveyResponse.answers]. */
sealed interface SurveyAnswerValue {
    data class SingleChoice(val optionId: String) : SurveyAnswerValue
    data class MultipleChoice(val optionIds: List<String>) : SurveyAnswerValue
    data class Numeric(val value: Double) : SurveyAnswerValue
    data class Text(val value: String) : SurveyAnswerValue
    /** ISO-8601 date string, e.g. "2026-07-10". */
    data class DateAnswer(val isoDate: String) : SurveyAnswerValue
}
```

#### 3. Response upload/edit + sync-state models
**File**: `domain/model/survey/SurveyResponseUpload.kt` (new)
```kotlin
package ar.com.ingenieriaalimentacion.domain.model.survey

import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState

/** Payload to enqueue a brand-new response (mirrors [ar.com.ingenieriaalimentacion.domain.model.subject.NewSubject]). */
data class NewSurveyResponse(
    val surveyId: String,
    val surveyName: String,
    val establishmentId: String,
    val establishmentName: String,
    val answers: Map<String, SurveyAnswerValue>,
    /** Epoch millis when the relevador actually finished the interview (client clock). */
    val eventAtMs: Long,
)

/** A locally-queued response plus its sync metadata. [localId] doubles as the SQLDelight PK
 *  and the wire idempotency `id` — same single-id design as [ar.com.ingenieriaalimentacion.domain.model.subject.SubjectUpload]. */
data class SurveyResponseUpload(
    val localId: String,
    val surveyId: String,
    val surveyName: String,
    val establishmentId: String,
    val establishmentName: String,
    val answersJson: String,
    val eventAt: String,
    val syncState: SyncState,
    val canonicalId: String?,
    val errorMessage: String?,
    val attemptCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

fun SurveyResponseUpload.isEligibleAt(nowMs: Long): Boolean {
    if (attemptCount == 0) return true
    val backoff = minOf(30_000L, 1_000L shl attemptCount) + (createdAt % 1_000L)
    return nowMs >= updatedAt + backoff
}
```

**File**: `domain/model/survey/SurveyResponseEditUpload.kt` (new)
```kotlin
package ar.com.ingenieriaalimentacion.domain.model.survey

import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState

/**
 * A locally-queued edit *attempt*. [editId] is a UUID that is a SEPARATE idempotency key
 * from [responseId] — the response being edited. Per ADR-0004: a fresh [editId] is minted
 * only the first time an edit is enqueued for a given [responseId]; while that edit attempt
 * is still PENDING (never transmitted), later local edits overwrite it in place rather than
 * minting a new id (see [SurveyResponseEditRepositoryImpl.enqueueEdit] in Phase 3) — reusing
 * an id that already reached the server would silently no-op, but overwriting a row that was
 * never sent carries no such risk.
 */
data class SurveyResponseEditUpload(
    val editId: String,
    val responseId: String,
    val surveyName: String,
    val establishmentName: String,
    val answersJson: String,
    val eventAt: String,
    val syncState: SyncState,
    val errorMessage: String?,
    val attemptCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

fun SurveyResponseEditUpload.isEligibleAt(nowMs: Long): Boolean {
    if (attemptCount == 0) return true
    val backoff = minOf(30_000L, 1_000L shl attemptCount) + (createdAt % 1_000L)
    return nowMs >= updatedAt + backoff
}
```

#### 4. History/detail models (for the "My survey responses" flow)
**File**: `domain/model/survey/SurveyResponseHistory.kt` (new)
```kotlin
package ar.com.ingenieriaalimentacion.domain.model.survey

import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState

/** One row in the "My survey responses" list — union of remote (/me/activity) + local pending. */
data class SurveyResponseSummary(
    val id: String,
    val surveyName: String,
    val establishmentName: String,
    val eventAt: String,
    val syncState: SyncState,
    val isLocal: Boolean,
)

/** Full answers, from GET /survey-responses/{id} or (offline fallback) the local queue. */
data class SurveyResponseDetail(
    val id: String,
    val surveyId: String,
    val establishmentId: String,
    val answers: Map<String, SurveyAnswerValue>,
    val eventAtMs: Long,
)
```

#### 5. Repository interfaces
**File**: `domain/repository/SurveyRepository.kt` (new)
```kotlin
interface SurveyRepository {
    suspend fun getAvailable(page: Int, pageSize: Int): Result<PagedSurveys>
    /** Cache-first: warm CachedSurvey row if present, else fetch + cache. */
    suspend fun getDetail(surveyId: String): Result<SurveyDetail>
}
```
**File**: `domain/repository/SurveyResponseRepository.kt` (new) — extends `SyncEngine`,
mirrors `SubjectRepository`:
```kotlin
interface SurveyResponseRepository : SyncEngine {
    suspend fun enqueue(newResponse: NewSurveyResponse): Result<Unit>
    fun observeUploads(): Flow<List<SurveyResponseUpload>>
    suspend fun retry(localId: String): Result<Unit>
}
```
**File**: `domain/repository/SurveyResponseEditRepository.kt` (new) — also extends
`SyncEngine`, the second, independent queue:
```kotlin
interface SurveyResponseEditRepository : SyncEngine {
    suspend fun enqueueEdit(
        responseId: String,
        surveyName: String,
        establishmentName: String,
        answers: Map<String, SurveyAnswerValue>,
        eventAtMs: Long,
    ): Result<Unit>
    suspend fun retry(editId: String): Result<Unit>
}
```
**File**: `domain/repository/SurveyResponseHistoryRepository.kt` (new)
```kotlin
interface SurveyResponseHistoryRepository {
    /** Remote list (unioned with local pending in the impl), page 1 only needs the union. */
    suspend fun getPage(page: Int, pageSize: Int): Result<List<SurveyResponseSummary>>
    /** Always prefers a live fetch; falls back to a local copy only when offline AND it's
     *  this device's own not-yet-pruned submission. */
    suspend fun getDetail(responseId: String): Result<SurveyResponseDetail>
}
```

#### 6. Role-gating helper
**File**: `domain/model/auth/SurveyRolePermissions.kt` (new) — the app's first
role-gated UI check.
```kotlin
package ar.com.ingenieriaalimentacion.domain.model.auth

private val SURVEY_RESPONSE_ROLES = setOf("field_worker", "admin", "superadmin")

/** `analyst` cannot submit or edit survey responses — the only role excluded. */
fun canRespondToSurveys(role: String): Boolean = role in SURVEY_RESPONSE_ROLES
```

### Success Criteria

#### Automated Verification:
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./scripts/compile.sh` passes.

#### Manual Verification:
- N/A (no UI yet).

---

## Phase 2: SQLDelight Schema + Migration

### Overview
Three new tables: a read-through cache for survey definitions, and the two pending
queues (create, edit). Follows the `.sqm` + schema-snapshot ceremony exactly (memory:
`sqldelight-migration-workflow`).

### Changes Required

#### 1. `CachedSurvey.sq`
**File**: `composeApp/src/commonMain/sqldelight/ar/com/ingenieriaalimentacion/data/db/CachedSurvey.sq` (new)
```sql
-- Read-through cache: list rows come from GET /surveys/available (detailJson NULL until
-- first detail fetch); detailJson holds the full question/option set from GET /surveys/{id}
-- so an in-progress or fully offline response can still render + validate against it.
CREATE TABLE CachedSurvey (
    id            TEXT NOT NULL PRIMARY KEY,
    name          TEXT NOT NULL,
    scope         TEXT NOT NULL,   -- 'GENERIC' | 'TARGETED'
    isActive      INTEGER NOT NULL,
    questionCount INTEGER NOT NULL,
    detailJson    TEXT,            -- full SurveyDetail JSON; NULL until GET /surveys/{id}
    updatedAt     INTEGER NOT NULL
);

selectAll:
SELECT * FROM CachedSurvey ORDER BY name ASC;

selectById:
SELECT * FROM CachedSurvey WHERE id = ?;

upsertSummary:                    -- from the /available list; preserves any existing detailJson
INSERT INTO CachedSurvey(id, name, scope, isActive, questionCount, detailJson, updatedAt)
VALUES (?, ?, ?, ?, ?, NULL, ?)
ON CONFLICT(id) DO UPDATE SET
    name = excluded.name, scope = excluded.scope, isActive = excluded.isActive,
    questionCount = excluded.questionCount, updatedAt = excluded.updatedAt;

upsertDetail:                     -- from GET /surveys/{id}
INSERT INTO CachedSurvey(id, name, scope, isActive, questionCount, detailJson, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(id) DO UPDATE SET
    name = excluded.name, scope = excluded.scope, isActive = excluded.isActive,
    questionCount = excluded.questionCount, detailJson = excluded.detailJson,
    updatedAt = excluded.updatedAt;

deleteAll:
DELETE FROM CachedSurvey;
```

#### 2. `PendingSurveyResponse.sq`
**File**: `composeApp/src/commonMain/sqldelight/ar/com/ingenieriaalimentacion/data/db/PendingSurveyResponse.sq` (new)
```sql
-- Same single-id design as PendingSubject/PendingMeasurement: localId is both the SQLDelight
-- PK and the wire idempotency `id`. surveyName/establishmentName are denormalized (like
-- PendingMeasurement's subject*/establishment* columns) so the "My survey responses" list
-- renders fully offline with no cross-table join. No MERGED state: no per-survey uniqueness
-- constraint exists server-side, so there is no conflict/dedup case to model.
CREATE TABLE PendingSurveyResponse (
    localId           TEXT    NOT NULL PRIMARY KEY,
    surveyId          TEXT    NOT NULL,
    surveyName        TEXT    NOT NULL,
    establishmentId   TEXT    NOT NULL,
    establishmentName TEXT    NOT NULL,
    answersJson       TEXT    NOT NULL,   -- Map<String, JsonElement>, wire-ready
    eventAt           TEXT    NOT NULL,   -- ISO-8601 instant, client clock
    syncState         TEXT    NOT NULL,   -- PENDING|SYNCING|SYNCED|FAILED
    canonicalId       TEXT,               -- server id once synced (== localId, always)
    errorMessage      TEXT,
    attemptCount      INTEGER NOT NULL DEFAULT 0,
    createdAt         INTEGER NOT NULL,
    updatedAt         INTEGER NOT NULL
);

CREATE INDEX PendingSurveyResponse_state ON PendingSurveyResponse(syncState);

insert:
INSERT INTO PendingSurveyResponse(localId, surveyId, surveyName, establishmentId,
    establishmentName, answersJson, eventAt, syncState, canonicalId, errorMessage,
    attemptCount, createdAt, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

selectAll:
SELECT * FROM PendingSurveyResponse ORDER BY createdAt DESC;

selectSyncable:
SELECT * FROM PendingSurveyResponse WHERE syncState = 'PENDING' ORDER BY createdAt ASC;

selectById:
SELECT * FROM PendingSurveyResponse WHERE localId = ?;

bumpRetry:
UPDATE PendingSurveyResponse SET syncState = 'PENDING', attemptCount = attemptCount + 1,
    errorMessage = ?, updatedAt = ? WHERE localId = ?;

markSyncing:
UPDATE PendingSurveyResponse SET syncState = 'SYNCING', updatedAt = ? WHERE localId = ?;

resetSyncingToPending:
UPDATE PendingSurveyResponse SET syncState = 'PENDING', updatedAt = ? WHERE syncState = 'SYNCING';

pruneTerminalOlderThan:            -- FAILED only — SYNCED rows are exempt (kept as the local
DELETE FROM PendingSurveyResponse  -- source of truth for offline edit pre-fill; see Phase 3).
WHERE syncState = 'FAILED' AND updatedAt < :cutoff;

markSynced:
UPDATE PendingSurveyResponse SET syncState = 'SYNCED', canonicalId = ?, errorMessage = NULL,
    updatedAt = ? WHERE localId = ?;

markFailed:
UPDATE PendingSurveyResponse SET syncState = 'FAILED', errorMessage = ?,
    attemptCount = attemptCount + 1, updatedAt = ? WHERE localId = ?;

resetForRetry:
UPDATE PendingSurveyResponse SET syncState = 'PENDING', errorMessage = NULL, attemptCount = 0,
    updatedAt = ? WHERE localId = ?;

deleteAll:
DELETE FROM PendingSurveyResponse;
```

#### 3. `PendingSurveyResponseEdit.sq`
**File**: `composeApp/src/commonMain/sqldelight/ar/com/ingenieriaalimentacion/data/db/PendingSurveyResponseEdit.sq` (new)
```sql
-- editId is the wire idempotency `id` for ONE edit attempt — deliberately NOT the same
-- value as responseId (ADR-0004). responseId is a plain string, not a SQL foreign key: the
-- parent response may live in PendingSurveyResponse (this device's own submission) or may
-- never have a local row at all (an edit of a response fetched from another device via
-- GET /survey-responses/{id}) — both are valid and gated at the app level (Phase 3), not
-- the schema level.
CREATE TABLE PendingSurveyResponseEdit (
    editId             TEXT    NOT NULL PRIMARY KEY,
    responseId         TEXT    NOT NULL,
    surveyName         TEXT    NOT NULL,
    establishmentName  TEXT    NOT NULL,
    answersJson        TEXT    NOT NULL,
    eventAt            TEXT    NOT NULL,
    syncState          TEXT    NOT NULL,  -- PENDING|SYNCING|SYNCED|FAILED
    errorMessage       TEXT,
    attemptCount       INTEGER NOT NULL DEFAULT 0,
    createdAt          INTEGER NOT NULL,
    updatedAt          INTEGER NOT NULL
);

CREATE INDEX PendingSurveyResponseEdit_state ON PendingSurveyResponseEdit(syncState);
CREATE INDEX PendingSurveyResponseEdit_responseId ON PendingSurveyResponseEdit(responseId);

insert:
INSERT INTO PendingSurveyResponseEdit(editId, responseId, surveyName, establishmentName,
    answersJson, eventAt, syncState, errorMessage, attemptCount, createdAt, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

selectAll:
SELECT * FROM PendingSurveyResponseEdit ORDER BY createdAt DESC;

selectSyncable:
SELECT * FROM PendingSurveyResponseEdit WHERE syncState = 'PENDING' ORDER BY createdAt ASC;

selectPendingForResponse:          -- the "coalesce into the still-unsent edit" lookup (Phase 3)
SELECT * FROM PendingSurveyResponseEdit WHERE responseId = ? AND syncState = 'PENDING' LIMIT 1;

updatePending:                     -- overwrite an unsent edit in place, no new idempotency key
UPDATE PendingSurveyResponseEdit SET answersJson = ?, eventAt = ?, updatedAt = ?
WHERE editId = ?;

markSyncing:
UPDATE PendingSurveyResponseEdit SET syncState = 'SYNCING', updatedAt = ? WHERE editId = ?;

resetSyncingToPending:
UPDATE PendingSurveyResponseEdit SET syncState = 'PENDING', updatedAt = ? WHERE syncState = 'SYNCING';

bumpRetry:
UPDATE PendingSurveyResponseEdit SET syncState = 'PENDING', attemptCount = attemptCount + 1,
    errorMessage = ?, updatedAt = ? WHERE editId = ?;

pruneTerminalOlderThan:
DELETE FROM PendingSurveyResponseEdit
WHERE (syncState = 'SYNCED' OR syncState = 'FAILED') AND updatedAt < :cutoff;

markSynced:
UPDATE PendingSurveyResponseEdit SET syncState = 'SYNCED', errorMessage = NULL, updatedAt = ?
WHERE editId = ?;

markFailed:
UPDATE PendingSurveyResponseEdit SET syncState = 'FAILED', errorMessage = ?,
    attemptCount = attemptCount + 1, updatedAt = ? WHERE editId = ?;

resetForRetry:
UPDATE PendingSurveyResponseEdit SET syncState = 'PENDING', errorMessage = NULL, attemptCount = 0,
    updatedAt = ? WHERE editId = ?;

deleteAll:
DELETE FROM PendingSurveyResponseEdit;
```

#### 4. Migration
**File**: `composeApp/src/commonMain/sqldelight/ar/com/ingenieriaalimentacion/data/db/5.sqm` (new)
```sql
-- v5 -> v6: adds CachedSurvey, PendingSurveyResponse, PendingSurveyResponseEdit for the
-- survey-responses feature.
CREATE TABLE IF NOT EXISTS CachedSurvey (
    id            TEXT NOT NULL PRIMARY KEY,
    name          TEXT NOT NULL,
    scope         TEXT NOT NULL,
    isActive      INTEGER NOT NULL,
    questionCount INTEGER NOT NULL,
    detailJson    TEXT,
    updatedAt     INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS PendingSurveyResponse (
    localId           TEXT    NOT NULL PRIMARY KEY,
    surveyId          TEXT    NOT NULL,
    surveyName        TEXT    NOT NULL,
    establishmentId   TEXT    NOT NULL,
    establishmentName TEXT    NOT NULL,
    answersJson       TEXT    NOT NULL,
    eventAt           TEXT    NOT NULL,
    syncState         TEXT    NOT NULL,
    canonicalId       TEXT,
    errorMessage      TEXT,
    attemptCount      INTEGER NOT NULL DEFAULT 0,
    createdAt         INTEGER NOT NULL,
    updatedAt         INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS PendingSurveyResponse_state ON PendingSurveyResponse(syncState);

CREATE TABLE IF NOT EXISTS PendingSurveyResponseEdit (
    editId             TEXT    NOT NULL PRIMARY KEY,
    responseId         TEXT    NOT NULL,
    surveyName         TEXT    NOT NULL,
    establishmentName  TEXT    NOT NULL,
    answersJson        TEXT    NOT NULL,
    eventAt            TEXT    NOT NULL,
    syncState          TEXT    NOT NULL,
    errorMessage       TEXT,
    attemptCount       INTEGER NOT NULL DEFAULT 0,
    createdAt          INTEGER NOT NULL,
    updatedAt          INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS PendingSurveyResponseEdit_state ON PendingSurveyResponseEdit(syncState);
CREATE INDEX IF NOT EXISTS PendingSurveyResponseEdit_responseId ON PendingSurveyResponseEdit(responseId);
```

#### 5. Regenerate schema snapshot
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :composeApp:generateCommonMainAppDatabaseSchema
```
Commit the new `6.db` under `src/commonMain/sqldelight/databases/` alongside `5.sqm`.

#### 6. Wire into logout cleanup
**File**: `data/db/SqlDelightDatabaseCleaner.kt`
```kotlin
database.transaction {
    database.pendingSubjectQueries.deleteAll()
    database.pendingMeasurementQueries.deleteAll()
    database.cachedEstablishmentQueries.deleteAll()
    database.cachedActivityItemQueries.deleteAll()
    database.currentEstablishmentQueries.deleteAll()
    database.cachedSurveyQueries.deleteAll()               // new
    database.pendingSurveyResponseQueries.deleteAll()       // new
    database.pendingSurveyResponseEditQueries.deleteAll()   // new
}
```

### Success Criteria

#### Automated Verification:
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:generateCommonMainAppDatabaseSchema --no-daemon` succeeds and produces `6.db`.
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./scripts/compile.sh` passes (runs `verifyCommonMainAppDatabaseMigration`).
- [x] `SqlDelightDatabaseCleanerTest` (existing, extend it) asserts the three new tables are empty after `clearAll()`. (Written; could not execute — no Android SDK in this sandbox, see note below.)

#### Manual Verification:
- [ ] Fresh install on a device/emulator creates the DB at v6 with no crash.
- [ ] A device already on v5 (pre-existing install) upgrades to v6 via `5.sqm` without data loss or a downgrade crash.

**Deviation from plan**: `CachedSurvey.sq`'s `upsertSummary`/`upsertDetail` originally used
`INSERT ... ON CONFLICT(id) DO UPDATE SET ...`, which this project's SQLDelight dialect
(sqlite_3_18, the existing default — no `dialect {}` block overrides it) does not parse
(UPSERT syntax requires SQLite 3.24+). Replaced with a single `insertOrReplace` query
(`INSERT OR REPLACE`, the same convention already used by `CachedActivityItem`/
`CachedEstablishment`); the "preserve existing detailJson on a list refresh" behavior moves
into `SurveyLocalStore` (Phase 3): read the existing row's `detailJson` first, then re-pass
it into `insertOrReplace`.

---

## Phase 3: Data Layer

### Overview
DTOs, two `Api` classes, mappers, local stores, and the two `SyncEngine`
implementations (create-queue, edit-queue), wired into the existing `SyncCoordinator`
and Koin.

### Changes Required

#### 1. DTOs
**File**: `data/survey/dto/SurveyDto.kt` (new)
```kotlin
@Serializable
data class SurveyListItemResponse(
    val id: String,
    val name: String,
    val scope: String,               // "generic" | "targeted"
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("created_by_id") val createdById: String,
    @SerialName("question_count") val questionCount: Int,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class PaginatedSurveysResponse(
    val items: List<SurveyListItemResponse>,
    val total: Int, val page: Int,
    @SerialName("page_size") val pageSize: Int,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
data class SurveyOptionResponse(val id: String, val text: String)

@Serializable
data class SurveyQuestionResponse(
    val id: String,
    val text: String,
    @SerialName("response_type") val responseType: String,  // single_choice|multiple_choice|numeric|text|date
    @SerialName("is_required") val isRequired: Boolean,
    val options: List<SurveyOptionResponse> = emptyList(),
)

@Serializable
data class SurveyDetailResponse(
    val id: String,
    val name: String,
    val scope: String,
    @SerialName("is_active") val isActive: Boolean,
    val questions: List<SurveyQuestionResponse>,
)
```
**File**: `data/survey/dto/SurveyResponseDto.kt` (new)
```kotlin
@Serializable
data class SurveyResponseRequest(
    val id: String,
    @SerialName("survey_id") val surveyId: String,
    @SerialName("establishment_id") val establishmentId: String,
    val answers: Map<String, JsonElement>,
    @SerialName("event_at") val eventAt: String,
)

@Serializable
data class SurveyResponseEditRequest(
    val id: String,
    val answers: Map<String, JsonElement>,
    @SerialName("event_at") val eventAt: String,
)

@Serializable
data class SurveyResponseResponse(
    val id: String,
    @SerialName("survey_id") val surveyId: String,
    @SerialName("establishment_id") val establishmentId: String,
    @SerialName("submitted_by_id") val submittedById: String,
    val answers: Map<String, JsonElement>,
    @SerialName("event_at") val eventAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)
```
**File**: `data/survey/dto/SurveyResponseActivityDto.kt` (new — deliberately independent
of `data/activity/dto/ActivityResponse.kt`, per the "don't touch the hardcoded feed"
decision)
```kotlin
@Serializable
data class SurveyResponseActivitySurveyResponse(val id: String, val name: String)

@Serializable
data class SurveyResponseActivityEstablishmentResponse(
    val id: String, val name: String, val city: String,
)

@Serializable
data class SurveyResponseActivitySummaryResponse(
    val id: String,
    @SerialName("event_at") val eventAt: String,
    val survey: SurveyResponseActivitySurveyResponse,
    val establishment: SurveyResponseActivityEstablishmentResponse,
)

@Serializable
data class SurveyResponseActivityItemResponse(
    val type: String,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("survey_response") val surveyResponse: SurveyResponseActivitySummaryResponse? = null,
)

@Serializable
data class PaginatedSurveyResponseActivityResponse(
    val items: List<SurveyResponseActivityItemResponse>,
    val total: Int, val page: Int,
    @SerialName("page_size") val pageSize: Int,
    @SerialName("total_pages") val totalPages: Int,
)
```

#### 2. `SurveyApi`
**File**: `data/survey/SurveyApi.kt` (new) — same status-branching convention as
`SubjectApi`.
```kotlin
class SurveyApi(private val client: HttpClient) {
    suspend fun available(page: Int, pageSize: Int): PaginatedSurveysResponse {
        val response = client.get("api/v1/surveys/available") {
            parameter("page", page); parameter("page_size", pageSize)
        }
        if (!response.status.isSuccess()) error("surveys available failed: ${response.status.value}")
        return response.body()
    }

    suspend fun detail(surveyId: String): SurveyDetailOutcome = runCatching {
        val response = client.get("api/v1/surveys/$surveyId")
        when (response.status.value) {
            200 -> SurveyDetailOutcome.Found(response.body())
            403 -> SurveyDetailOutcome.Forbidden
            404 -> SurveyDetailOutcome.NotFound
            else -> SurveyDetailOutcome.Retryable(null)
        }
    }.getOrElse { t -> if (t is CancellationException) throw t; SurveyDetailOutcome.Retryable(t) }
}

sealed interface SurveyDetailOutcome {
    data class Found(val response: SurveyDetailResponse) : SurveyDetailOutcome
    data object Forbidden : SurveyDetailOutcome
    data object NotFound : SurveyDetailOutcome
    data class Retryable(val cause: Throwable?) : SurveyDetailOutcome
}
```

#### 3. `SurveyResponseApi`
**File**: `data/survey/SurveyResponseApi.kt` (new)
```kotlin
class SurveyResponseApi(private val client: HttpClient) {
    suspend fun create(body: SurveyResponseRequest): SurveyResponseUploadOutcome =
        runCatching {
            val response = client.post("api/v1/survey-responses") {
                contentType(ContentType.Application.Json); setBody(body)
            }
            when (response.status.value) {
                200, 201 -> SurveyResponseUploadOutcome.Created(response.body())
                400, 422 -> SurveyResponseUploadOutcome.InvalidData
                401 -> SurveyResponseUploadOutcome.Unauthorized
                403 -> SurveyResponseUploadOutcome.Forbidden
                404 -> SurveyResponseUploadOutcome.NotFound
                else -> SurveyResponseUploadOutcome.Retryable(null)
            }
        }.getOrElse { t -> if (t is CancellationException) throw t; SurveyResponseUploadOutcome.Retryable(t) }

    suspend fun edit(responseId: String, body: SurveyResponseEditRequest): SurveyResponseEditOutcome =
        runCatching {
            val response = client.put("api/v1/survey-responses/$responseId") {
                contentType(ContentType.Application.Json); setBody(body)
            }
            when (response.status.value) {
                200 -> SurveyResponseEditOutcome.Applied(response.body())
                400 -> SurveyResponseEditOutcome.InvalidData
                401 -> SurveyResponseEditOutcome.Unauthorized
                403 -> SurveyResponseEditOutcome.Forbidden
                404 -> SurveyResponseEditOutcome.NotFound
                else -> SurveyResponseEditOutcome.Retryable(null)
            }
        }.getOrElse { t -> if (t is CancellationException) throw t; SurveyResponseEditOutcome.Retryable(t) }

    suspend fun detail(responseId: String): SurveyResponseDetailOutcome = runCatching {
        val response = client.get("api/v1/survey-responses/$responseId")
        when (response.status.value) {
            200 -> SurveyResponseDetailOutcome.Found(response.body())
            403 -> SurveyResponseDetailOutcome.Forbidden
            404 -> SurveyResponseDetailOutcome.NotFound
            else -> SurveyResponseDetailOutcome.Retryable(null)
        }
    }.getOrElse { t -> if (t is CancellationException) throw t; SurveyResponseDetailOutcome.Retryable(t) }
}

sealed interface SurveyResponseUploadOutcome {
    data class Created(val response: SurveyResponseResponse) : SurveyResponseUploadOutcome
    data object InvalidData : SurveyResponseUploadOutcome
    data object Forbidden : SurveyResponseUploadOutcome
    data object NotFound : SurveyResponseUploadOutcome
    data object Unauthorized : SurveyResponseUploadOutcome
    data class Retryable(val cause: Throwable?) : SurveyResponseUploadOutcome
}

sealed interface SurveyResponseEditOutcome {
    data class Applied(val response: SurveyResponseResponse) : SurveyResponseEditOutcome
    data object InvalidData : SurveyResponseEditOutcome
    data object Forbidden : SurveyResponseEditOutcome
    data object NotFound : SurveyResponseEditOutcome
    data object Unauthorized : SurveyResponseEditOutcome
    data class Retryable(val cause: Throwable?) : SurveyResponseEditOutcome
}

sealed interface SurveyResponseDetailOutcome {
    data class Found(val response: SurveyResponseResponse) : SurveyResponseDetailOutcome
    data object Forbidden : SurveyResponseDetailOutcome
    data object NotFound : SurveyResponseDetailOutcome
    data class Retryable(val cause: Throwable?) : SurveyResponseDetailOutcome
}
```
Add `type=survey_response` reads via a **new** minimal method on a dedicated
`SurveyResponseActivityApi` (`data/survey/SurveyResponseActivityApi.kt`), calling the
same `api/v1/users/me/activity` URL as `ActivityApi.list` but deserializing into
`PaginatedSurveyResponseActivityResponse` — kept separate from `ActivityApi` so neither
file needs to know about the other's item type (mirrors `ActivityApi.kt:15-23`
structurally, does not modify it).

#### 4. Mappers
**File**: `data/survey/mapper/SurveyAnswerMapper.kt` (new) — the answer ↔ wire-JSON
bridge (both directions used by enqueue, and by edit-prefill/decode):
```kotlin
fun Map<String, SurveyAnswerValue>.toAnswersJson(): Map<String, JsonElement> = mapValues { (_, v) ->
    when (v) {
        is SurveyAnswerValue.SingleChoice -> JsonPrimitive(v.optionId)
        is SurveyAnswerValue.MultipleChoice -> JsonArray(v.optionIds.map { JsonPrimitive(it) })
        is SurveyAnswerValue.Numeric -> JsonPrimitive(v.value)
        is SurveyAnswerValue.Text -> JsonPrimitive(v.value)
        is SurveyAnswerValue.DateAnswer -> JsonPrimitive(v.isoDate)
    }
}

/** Answers alone are ambiguous (a bare JSON string could be text/date/single-choice) — must
 *  be interpreted against the owning survey's question list. Unknown/missing questions are
 *  skipped rather than throwing, so a stale cached survey definition degrades gracefully. */
fun Map<String, JsonElement>.toAnswerValues(questions: List<SurveyQuestion>): Map<String, SurveyAnswerValue> {
    val byId = questions.associateBy { it.id }
    return mapNotNull { (questionId, element) ->
        val type = byId[questionId]?.responseType ?: return@mapNotNull null
        val value = when (type) {
            SurveyResponseType.SingleChoice -> SurveyAnswerValue.SingleChoice(element.jsonPrimitive.content)
            SurveyResponseType.MultipleChoice ->
                SurveyAnswerValue.MultipleChoice(element.jsonArray.map { it.jsonPrimitive.content })
            SurveyResponseType.Numeric -> SurveyAnswerValue.Numeric(element.jsonPrimitive.double)
            SurveyResponseType.Text -> SurveyAnswerValue.Text(element.jsonPrimitive.content)
            SurveyResponseType.Date -> SurveyAnswerValue.DateAnswer(element.jsonPrimitive.content)
        }
        questionId to value
    }.toMap()
}
```
Plus straightforward `SurveyListItemResponse.toDomain()`, `SurveyDetailResponse
.toDomain()`, `SurveyResponseResponse.toDomain()` DTO↔domain mappers
(`data/survey/mapper/SurveyMapper.kt`), following the existing
`data/subject/mapper/SubjectMapper.kt` convention (small top-level extension
functions, `response_type` string ↔ `SurveyResponseType` enum via a `when`, erroring
on an unknown value like `ActivityMapper.toParsedEntry()` does).

#### 5. Local stores
**File**: `data/survey/local/SurveyLocalStore.kt` (+ `DefaultSurveyLocalStore.kt`) —
cache-first reads over `CachedSurveyQueries`, mirrors `DefaultEstablishmentLocalStore`.

**File**: `data/survey/local/SurveyResponseLocalStore.kt` (+
`DefaultSurveyResponseLocalStore.kt`) — mirrors `DefaultSubjectLocalStore` exactly
(`insert`, `observeAll`, `syncable`, `find(localId)`, `markSyncing`, `markSynced`,
`bumpRetry`, `markFailed`, `resetForRetry`, `resetStuckSyncing`,
`pruneTerminalOlderThan`), operating on `PendingSurveyResponseQueries`. `find(localId)`
is the new addition the edit engine's FK-gate needs (subjects/measurements don't need
an analogous by-id lookup on their own table today, but the pattern —
`SubjectLocalStore` has no `find`, `MeasurementLocalStore` reads via the *subject's*
`find` — is the same shape as this store reading its own rows for the edit engine).

**File**: `data/survey/local/SurveyResponseEditLocalStore.kt` (+
`DefaultSurveyResponseEditLocalStore.kt`) — same shape, plus the coalescing methods:
```kotlin
interface SurveyResponseEditLocalStore {
    suspend fun findPendingForResponse(responseId: String): SurveyResponseEditUpload?
    suspend fun updatePending(editId: String, answersJson: String, eventAt: String, now: Long)
    suspend fun insert(edit: SurveyResponseEditUpload)
    fun syncable(): List<SurveyResponseEditUpload>          // suspend + Flow variants as needed
    // markSyncing / markSynced / bumpRetry / markFailed / resetForRetry / resetStuckSyncing /
    // pruneTerminalOlderThan — same shape as SurveyResponseLocalStore.
}
```

#### 6. Repository implementations (the two `SyncEngine`s)

**File**: `data/survey/SurveyResponseRepositoryImpl.kt` (new) — a direct structural
port of `SubjectRepositoryImpl` (`data/subject/SubjectRepositoryImpl.kt:22-94`), with
the outcome `when` simplified (no `Duplicate`/`Merged` branch — no per-survey
uniqueness) and a `NotFound` branch added (survey/establishment id gone stale, mirrors
`MeasurementRepositoryImpl`'s `NotFound` handling):
```kotlin
class SurveyResponseRepositoryImpl(
    private val api: SurveyResponseApi,
    private val local: SurveyResponseLocalStore,
    private val idGenerator: IdGenerator,
    private val json: Json,
    private val now: () -> Long,
) : SurveyResponseRepository {

    override suspend fun enqueue(newResponse: NewSurveyResponse): Result<Unit> = runCatching {
        val nowMs = now()
        val eventAt = Instant.fromEpochMilliseconds(newResponse.eventAtMs).toString()
        val answersJson = json.encodeToString(
            MapSerializer(String.serializer(), JsonElement.serializer()),
            newResponse.answers.toAnswersJson(),
        )
        local.insert(
            SurveyResponseUpload(
                localId = idGenerator.newId(),
                surveyId = newResponse.surveyId,
                surveyName = newResponse.surveyName,
                establishmentId = newResponse.establishmentId,
                establishmentName = newResponse.establishmentName,
                answersJson = answersJson,
                eventAt = eventAt,
                syncState = SyncState.Pending,
                canonicalId = null,
                errorMessage = null,
                attemptCount = 0,
                createdAt = nowMs,
                updatedAt = nowMs,
            ),
        )
    }

    override fun observeUploads() = local.observeAll()

    override suspend fun syncPending(): SyncSummary {
        var synced = 0; var failed = 0; var pending = 0
        local.syncable().filter { it.isEligibleAt(now()) }.forEach { upload ->
            local.markSyncing(upload.localId)
            when (val outcome = api.create(upload.toRequest(json))) {
                is SurveyResponseUploadOutcome.Created -> {
                    local.markSynced(upload.localId, outcome.response.id); synced++
                }
                SurveyResponseUploadOutcome.InvalidData -> {
                    local.markFailed(upload.localId, "Las respuestas no son válidas."); failed++
                }
                SurveyResponseUploadOutcome.Forbidden -> {
                    local.markFailed(upload.localId, "No tenés permisos para responder esta encuesta."); failed++
                }
                SurveyResponseUploadOutcome.NotFound -> {
                    local.markFailed(upload.localId, "La encuesta o el establecimiento no existen."); failed++
                }
                SurveyResponseUploadOutcome.Unauthorized ->
                    if (retryOrDeadLetter(upload, "Sesión expirada. Reintentando…")) pending++ else failed++
                is SurveyResponseUploadOutcome.Retryable ->
                    if (retryOrDeadLetter(upload, "Sin conexión. Reintentando…")) pending++ else failed++
            }
        }
        return SyncSummary(synced = synced, merged = 0, failed = failed, stillPending = pending)
    }

    private suspend fun retryOrDeadLetter(upload: SurveyResponseUpload, msg: String): Boolean =
        if (upload.attemptCount + 1 >= MAX_SYNC_ATTEMPTS) {
            local.markFailed(upload.localId, "No se pudo sincronizar tras varios intentos."); false
        } else { local.bumpRetry(upload.localId, msg); true }

    override suspend fun retry(localId: String) = runCatching { local.resetForRetry(localId) }
    override suspend fun resetStuckSyncing() = local.resetStuckSyncing()
    override suspend fun pruneTerminal(olderThan: Duration) =
        local.pruneTerminalOlderThan(now() - olderThan.inWholeMilliseconds)
}
```
Note `pruneTerminalOlderThan` only ever deletes `FAILED` rows for this table (per the
`.sq` query above) — `SYNCED` rows are exempt, deliberately, so they remain the local
edit-prefill fallback. (Confirmed with the user — Q1.)

**File**: `data/survey/SurveyResponseEditRepositoryImpl.kt` (new) — the FK-gated engine,
structurally ported from `MeasurementRepositoryImpl`'s subject-gate
(`data/measurement/MeasurementRepositoryImpl.kt:50-71`), gating on the parent
response's `SyncState` instead of a subject's:
```kotlin
class SurveyResponseEditRepositoryImpl(
    private val api: SurveyResponseApi,
    private val local: SurveyResponseEditLocalStore,
    private val responses: SurveyResponseLocalStore,
    private val idGenerator: IdGenerator,
    private val json: Json,
    private val now: () -> Long,
) : SurveyResponseEditRepository {

    override suspend fun enqueueEdit(
        responseId: String, surveyName: String, establishmentName: String,
        answers: Map<String, SurveyAnswerValue>, eventAtMs: Long,
    ): Result<Unit> = runCatching {
        val nowMs = now()
        val eventAt = Instant.fromEpochMilliseconds(eventAtMs).toString()
        val answersJson = json.encodeToString(
            MapSerializer(String.serializer(), JsonElement.serializer()),
            answers.toAnswersJson(),
        )
        val existing = local.findPendingForResponse(responseId)
        if (existing != null) {
            // Not-yet-transmitted edit for this response: overwrite in place. Safe — this id
            // was never sent, so there's no already-applied server-side attempt to collide with.
            local.updatePending(existing.editId, answersJson, eventAt, nowMs)
        } else {
            local.insert(
                SurveyResponseEditUpload(
                    editId = idGenerator.newId(),
                    responseId = responseId,
                    surveyName = surveyName,
                    establishmentName = establishmentName,
                    answersJson = answersJson,
                    eventAt = eventAt,
                    syncState = SyncState.Pending,
                    errorMessage = null,
                    attemptCount = 0,
                    createdAt = nowMs,
                    updatedAt = nowMs,
                ),
            )
        }
    }

    override suspend fun syncPending(): SyncSummary {
        var synced = 0; var failed = 0; var pending = 0
        local.syncable().filter { it.isEligibleAt(now()) }.forEach { edit ->
            // Gate on the parent response's own queue state (no schema JOIN, same pattern as
            // MeasurementRepositoryImpl's subject gate). No local row → this device never
            // created it (fetched from the server, e.g. cross-device edit) → assume synced,
            // attempt directly.
            val parent = responses.find(edit.responseId)
            when {
                parent == null || parent.syncState == SyncState.Synced -> Unit
                parent.syncState == SyncState.Failed -> {
                    local.markFailed(edit.editId, "La respuesta original no se pudo sincronizar.")
                    failed++
                    return@forEach
                }
                else -> { pending++; return@forEach }
            }
            local.markSyncing(edit.editId)
            when (val outcome = api.edit(edit.responseId, edit.toRequest(json))) {
                is SurveyResponseEditOutcome.Applied -> { local.markSynced(edit.editId); synced++ }
                SurveyResponseEditOutcome.InvalidData -> {
                    local.markFailed(edit.editId, "Las respuestas no son válidas."); failed++
                }
                SurveyResponseEditOutcome.Forbidden -> {
                    local.markFailed(edit.editId, "No tenés permisos para editar esta respuesta."); failed++
                }
                SurveyResponseEditOutcome.NotFound -> {
                    local.markFailed(edit.editId, "La respuesta original no existe en el servidor."); failed++
                }
                SurveyResponseEditOutcome.Unauthorized ->
                    if (retryOrDeadLetter(edit, "Sesión expirada. Reintentando…")) pending++ else failed++
                is SurveyResponseEditOutcome.Retryable ->
                    if (retryOrDeadLetter(edit, "Sin conexión. Reintentando…")) pending++ else failed++
            }
        }
        return SyncSummary(synced = synced, merged = 0, failed = failed, stillPending = pending)
    }

    private suspend fun retryOrDeadLetter(edit: SurveyResponseEditUpload, msg: String): Boolean =
        if (edit.attemptCount + 1 >= MAX_SYNC_ATTEMPTS) {
            local.markFailed(edit.editId, "No se pudo sincronizar tras varios intentos."); false
        } else { local.bumpRetry(edit.editId, msg); true }

    override suspend fun retry(editId: String) = runCatching { local.resetForRetry(editId) }
    override suspend fun resetStuckSyncing() = local.resetStuckSyncing()
    override suspend fun pruneTerminal(olderThan: Duration) =
        local.pruneTerminalOlderThan(now() - olderThan.inWholeMilliseconds)
}
```

**File**: `data/survey/SurveyResponseHistoryRepositoryImpl.kt` (new) — implements
`SurveyResponseHistoryRepository`:
- `getPage`: fetch via the new `SurveyResponseActivityApi` (type filter, page 1 only —
  matching `SubjectQueryRepositoryImpl.searchSubjects()`'s page-1-only union rule);
  union with `local.observeAll()` snapshot (both `PendingSurveyResponse` PENDING/
  SYNCING/SYNCED rows), prepend local-only entries not present in the remote id set. On
  remote failure (offline), fall back to serving the local queue as a synthetic page —
  same shape as `SubjectQueryRepositoryImpl.kt:39-45`.
- `getDetail`: always attempts `SurveyResponseApi.detail(responseId)` first (freshest —
  last-write-wins by `event_at` means a cached copy can go silently stale, per the
  guide's §4 warning). On network failure, falls back to `local.find(responseId)` (this
  device's own `PendingSurveyResponse`/`SYNCED` row) if present; otherwise surfaces the
  error (no known local copy — the guide's own documented gap for a response neither
  submitted from nor previously viewed on this device while offline).
- `SurveyRepositoryImpl` (`data/survey/SurveyRepositoryImpl.kt`): `getAvailable` is a
  thin live call (`SurveyApi.available` + `SurveyLocalStore.upsertSummaries`, cache
  written but not read-through — availability is time-sensitive, always prefer live);
  `getDetail` is cache-first (`SurveyLocalStore.findDetail(surveyId)` → return if
  present, else `SurveyApi.detail` → cache → return), so a survey opened once while
  online is answerable fully offline afterward.

#### 7. Koin DI
**File**: `di/DatabaseModule.kt`
```kotlin
single { get<AppDatabase>().cachedSurveyQueries }
single { get<AppDatabase>().pendingSurveyResponseQueries }
single { get<AppDatabase>().pendingSurveyResponseEditQueries }
single<List<SyncEngine>> {
    listOf(
        get<SubjectRepository>(),
        get<MeasurementRepository>(),
        get<SurveyResponseRepository>(),       // new
        get<SurveyResponseEditRepository>(),   // new
    )
}
```
**File**: `di/RepositoriesModule.kt` — add `SurveyLocalStore`, `SurveyResponseLocalStore`,
`SurveyResponseEditLocalStore` singles (same `now = { Clock.System.now()
.toEpochMilliseconds() }` injection convention), `SurveyRepository`,
`SurveyResponseRepository`, `SurveyResponseEditRepository` (injecting
`json = get(named("auth"))`, matching `SubjectHistoryRepositoryImpl`'s wiring), and
`SurveyResponseHistoryRepository`.

### Success Criteria

#### Automated Verification:
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./scripts/compile.sh` passes.
- [x] New unit tests (mirroring `commonTest/.../data/subject/SubjectSyncEngineTest.kt`
      and `data/measurement/MeasurementSyncEngineTest.kt`) for
      `SurveyResponseRepositoryImpl`: 201/200 → `Synced`; 400/422/403/404 → immediate
      `Failed`; 401/5xx → `Pending` with incrementing `attemptCount` up to
      `MAX_SYNC_ATTEMPTS`, then `Failed`. (Written in `SurveyResponseSyncEngineTest.kt`;
      compiles clean — could not execute, see note below.)
- [x] New unit tests for `SurveyResponseEditRepositoryImpl`: edit held `Pending` while
      parent response is `Pending`/`Syncing`; parent `Failed` → edit immediately
      `Failed`; parent `Synced` (or no local parent row at all) → edit attempted;
      same-error-code branches as above. (Written in `SurveyResponseEditSyncEngineTest.kt`.)
- [x] New unit test: `enqueueEdit` called twice for the same `responseId` while the
      first edit is still `Pending` reuses the same `editId` (coalesces); called again
      after the first has reached `Synced`/`Syncing` mints a new `editId`. (Same file.)
- [x] `FakeSurveyResponseLocalStore`/`FakeSurveyResponseEditLocalStore` fakes added
      under `commonTest/.../fake/` (mirroring `FakeSubjectLocalStore.kt`).

**Could not execute tests in this sandbox**: no Android SDK (`compileDebugUnitTestKotlinAndroid`
fails at dependency-resolution before compiling — `ANDROID_HOME`/`local.properties` missing) and
no JVM test target exists in this KMP module (only `androidUnitTest`/iOS targets). As a proxy,
`compileTestKotlinIosSimulatorArm64` was run: it type-checks the entire `commonTest` source set
cleanly for all new survey files (zero new errors); the only failures are pre-existing, unrelated
test names in `ActivityApiTest.kt`/`SubjectQueryRepositoryImplTest.kt`/`HomeStateMapperTest.kt`/
`MeasurementUploadUiTest.kt`/`SubjectUploadUiTest.kt` that contain commas in their backtick names
(a Kotlin/Native symbol-mangling limitation on that target, not something this phase introduced).
Run the actual suite with Android Studio or a machine with the Android SDK configured.

#### Manual Verification:
- N/A (no UI yet — covered end-to-end in Phase 5/6).

---

## Phase 4: Design-System Components

### Overview
Two new reusable input components, generalized from `CreateSubjectScreen`'s
hand-rolled `SexSelector`/`SexOption` pattern. `date`-type questions reuse the existing
`AlInput` + `DateVisualTransformation` idiom — no new component needed there.

### Changes Required

#### 1. Single-choice group
**File**: `core/design_system/components/input/AlChoiceGroup.kt` (new) — generalizes
`SexOption`'s clip/background/border/clickable visual treatment into a reusable,
label-driven, N-option chip row/wrap (not hardcoded to two options).
```kotlin
@Composable
fun AlChoiceGroup(
    label: String,
    options: List<AlChoiceOption>,
    selectedId: String?,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
)

data class AlChoiceOption(val id: String, val label: String)
```
Layout: `FlowRow` (options wrap onto multiple lines for longer survey option text,
unlike `SexSelector`'s fixed two-`weight(1f)` row) with the same selected/unselected
`styles.primary`/`styles.background` + border treatment as `SexOption`.

#### 2. Multiple-choice group
**File**: `core/design_system/components/input/AlCheckboxGroup.kt` (new) — a vertical
list of `AlSelectableCard`-style rows, each with a leading `Checkbox` (reusing
`AlSelectableCard`'s existing `Checkbox` usage rather than a new checkbox primitive).
```kotlin
@Composable
fun AlCheckboxGroup(
    label: String,
    options: List<AlChoiceOption>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
)
```

Both follow `AlInput`'s label/error/`enabled` prop shape so they drop into the same
form-field vertical rhythm (`Arrangement.spacedBy(16.dp)` columns) used throughout
`CreateSubjectScreen`/`MeasurementScreen`.

### Success Criteria

#### Automated Verification:
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./scripts/compile.sh` passes.

#### Manual Verification:
- [ ] Both components render correctly at 1x/2x/3x font scale (long option text wraps,
      doesn't clip) on an Android emulator.
- [ ] Disabled state (`enabled = false`) visibly non-interactive, matches `AlInput`'s
      disabled treatment.

**Deviation from plan**: `AlCheckboxGroup` directly composes [AlSelectableCard] per option
(title = option label, selected = membership in `selectedIds`) rather than hand-building a new
row with a leading `Checkbox` — this *is* "reusing `AlSelectableCard`'s existing `Checkbox`
usage," just via composition instead of copy-pasting its internals.

---

## Phase 5: Fill-a-Survey Flow

### Overview
Two new screens: browse available surveys, and the dynamic response form. Shares a
new `SurveyQuestionForm` composable with Phase 6's edit screen.

### Changes Required

#### 1. Shared dynamic-question renderer
**File**: `presentation/feature/survey/common/SurveyQuestionForm.kt` (new) — takes
`List<SurveyQuestion>` + a `Map<String, SurveyAnswerValue>` + `onAnswerChanged`, renders
one field per question by `responseType`:
- `SingleChoice` → `AlChoiceGroup`
- `MultipleChoice` → `AlCheckboxGroup`
- `Numeric` → `AlInput` with `KeyboardType.Decimal` (reusing `MeasurementViewModel
  .sanitizeDecimal`-style comma/dot normalization, hoisted to a shared
  `core/validation/` or `domain/util/` extension so both features use one
  implementation)
- `Text` → `AlInput`, `singleLine = false` for longer free text
- `Date` → `AlInput` + `DateVisualTransformation` (DD/MM/AAAA mask, converted to
  ISO `yyyy-MM-dd` when building the answer)

Required-field validation: blur-aware per CLAUDE.md's `TextFieldState`/`Rules`
pattern for `Text`/`Numeric`/`Date` questions; `SingleChoice`/`MultipleChoice` show
their error only after `Submit` is pressed (no natural "blur" for a choice control,
same as `CreateSubjectScreen`'s `sexError`, which is submit-triggered only).

#### 2. Browse surveys screen
**Files**: `presentation/feature/survey/survey_list/{SurveyListAction,
SurveyListDestination, SurveyListNavigation, SurveyListScreen, SurveyListState,
SurveyListViewModel}.kt` (new, six-file pattern — scaffold via the
`feature-screen-generator` skill, then fill in). Paginated list (`AlListRow` per
survey, `question_count` as subtitle), tap → `SurveyResponseForm(surveyId)`.

#### 3. Response form screen
**Files**: `presentation/feature/survey/survey_response_form/{SurveyResponseFormAction,
..Destination, ..Navigation, ..Screen, ..State, ..ViewModel}.kt` (new).
- `init`: `getDetail(surveyId)` (cache-first, per Phase 3) to populate questions; loads
  approved establishments + current-establishment default exactly like
  `MeasurementViewModel.loadEstablishments()` (`getCurrentEstablishment(approved) ?:
  approved.singleOrNull() ?: state.selectedEstablishment`), including the same
  `ModalBottomSheet` establishment picker UI as `MeasurementScreen.kt:122-143`.
- `Submit`: force-show every required-question error (mirrors
  `CreateSubjectViewModel.submit()`'s `showError(validator)` sweep); if all required
  answered, build `NewSurveyResponse(eventAtMs = now)` and call
  `SurveyResponseRepository.enqueue(...)` (via a thin `SubmitSurveyResponseUseCase`,
  matching `CreateMeasurementUseCase`'s shape), then `syncCoordinator.requestSync()`
  fire-and-forget, and show a success state (clearing answers so the field worker can
  start another interview — mirrors `MeasurementViewModel.submit()`'s
  clear-and-keep-establishment behavior, except here answers reset for a fresh survey
  attempt while establishment stays selected, matching "N interviews per visit").

### Success Criteria

#### Automated Verification:
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./scripts/compile.sh` passes.
- [x] `SurveyResponseFormViewModelTest` (new, mirrors `CreateSubjectViewModelTest`):
      required-question validation blocks submit; a valid form calls `enqueue` with
      correctly-typed `answers`. (Compiles clean on the `iosSimulatorArm64` test
      target proxy — see Phase 3's note on why tests can't execute in this sandbox.)

#### Manual Verification:
- [ ] Online: browse surveys, open one, fill every question type, submit — response
      appears synced (verify via a temporary log or the Phase 6 history screen).
- [ ] Airplane mode: same flow — response saved locally as `PENDING`; re-enable
      connectivity, confirm it syncs within the 15-minute foreground tick or via
      manual sync.
- [ ] A survey opened once while online is still fully answerable in airplane mode
      afterward (cache-first `getDetail` verified).
- [ ] `analyst`-role account does not see the entry point to this flow (verified once
      Phase 7 wires the Home gating).

**Deviations from plan**: (1) hoisted `sanitizeDecimal` out of `MeasurementViewModel` into
`domain/util/DecimalInput.kt` (`sanitizeDecimalInput`) as the plan directed, and switched
`MeasurementViewModel` to the shared function. (2) Added `Route.SurveyList` /
`Route.SurveyResponseForm` (plus the three Phase 6 routes, added early to avoid a forward
reference) in this phase rather than waiting for Phase 7 — each six-file screen's
`*Navigation.kt` needs its `Route` entry to exist to compile; Phase 7 still owns wiring these
into `AppNavHost` and the Home entry points. (3) Added a `SurveyAnswerFieldState.kt` file
(`presentation/feature/survey/common/`) alongside `SurveyQuestionForm.kt` — the plan described
the form's behavior but not this per-question UI-buffer/validator model explicitly; it's the
data side of "blur-aware validation for Text/Numeric/Date, submit-only for choice questions."

---

## Phase 6: View & Edit Flow

### Overview
"My survey responses" list (own dedicated screen, not the Home activity feed) +
detail/edit.

### Changes Required

#### 1. History list screen
**Files**: `presentation/feature/survey/survey_response_history/{...}.kt` (new,
six-file pattern). Calls `SurveyResponseHistoryRepository.getPage` (Phase 3's
union-with-local-pending read); each row shows survey name, establishment, `event_at`,
and a sync-state indicator (`Pending`/`Syncing` spinner, `Synced` checkmark, `Failed`
with a Retry action — reusing whatever indicator treatment the existing Uploads
screens use for subjects/measurements). Tap → `SurveyResponseDetail(responseId)`.

#### 2. Detail screen (read-only + edit entry point)
**Files**: `presentation/feature/survey/survey_response_detail/{...}.kt` (new).
`init` calls `SurveyResponseHistoryRepository.getDetail(responseId)` (live-first, per
Phase 3) plus `SurveyRepository.getDetail(surveyId)` to resolve question text/labels
for rendering the answers read-only (via `toAnswerValues` from Phase 3's mapper).
"Editar" button → `SurveyResponseEdit(responseId)`, gated by
`canRespondToSurveys(profile.role)` **and** "is the original submitter, or
admin/superadmin" (the detail response's `submitted_by_id` vs. the current user's
id — mirrors the guide's §3/§4 restriction; if neither, the button is hidden rather
than shown-then-403'd).

#### 3. Edit screen
**Files**: `presentation/feature/survey/survey_response_edit/{...}.kt` (new). Reuses
`SurveyQuestionForm` (Phase 5) pre-filled with the detail screen's already-fetched
answers. `Submit` calls `SurveyResponseEditRepository.enqueueEdit(...)` (via a thin
`EditSurveyResponseUseCase`) with a **new client-clock `event_at`** (when this edit was
actually made — distinct from the original response's `event_at`), then
`syncCoordinator.requestSync()`, then pops back with a result flag
(`SURVEY_RESPONSE_EDITED_RESULT`, added to the shared `Results.kt`-style constants file
per CLAUDE.md's "navigate-back-with-result" pattern) so the detail screen re-fetches
and shows the update — noting per the guide that the re-fetched state may not equal
what was just sent if a later-`event_at` edit from elsewhere already won; that's
expected, not a bug, and is not specially surfaced (no conflict UI, per the guide's
explicit "no 409, no merge UI").

### Success Criteria

#### Automated Verification:
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./scripts/compile.sh` passes.
- [x] `SurveyResponseEditViewModelTest` (new): submit builds correctly-typed answers
      and calls `enqueueEdit` with a fresh `event_at`. (Compiles clean on the
      `iosSimulatorArm64` test-target proxy; see Phase 3's note on sandbox limits.)

#### Manual Verification:
- [ ] Online: submit a response, open it from "My survey responses," edit an answer,
      confirm the change round-trips (re-fetch shows the new answer).
- [ ] Airplane mode: edit a response submitted earlier on this device (still
      `SYNCED` and un-pruned) — edit queues locally; reconnect, confirm it syncs.
- [ ] Edit the same not-yet-synced response twice while still offline — confirm (via a
      temporary log/DB inspection) only one edit row exists locally before sync, i.e.
      coalescing works, not two competing edit attempts.
- [ ] A response this device never submitted (simulate: submit from a second
      device/account, or directly via `curl`) opens correctly in "My survey responses"
      detail view via the live `GET /survey-responses/{id}` fetch, and is editable.
- [ ] A `field_worker` who is not the original submitter (and not admin/superadmin)
      does not see the "Editar" button on someone else's response.

**Deviations from plan**:
- `SurveyResponseDetail` (Phase 1 domain model) was missing `submittedById`, which this
  phase's own edit-gating rule ("is the original submitter, or admin/superadmin")
  requires. Added it as `String?` — non-null from the live `GET /survey-responses/{id}`
  fetch, `null` only in the offline-local-fallback path (a row this device itself
  queued/synced is, by definition, its own submission, so a null value is treated as
  "yes, it's mine" in the gating check).
- No dedicated establishment-name lookup endpoint exists. The detail/edit screens resolve
  a friendly establishment name by matching `establishmentId` against
  `GetMyEstablishmentsUseCase()`'s results (same source `SurveyResponseFormViewModel`
  already uses), falling back to the raw id if not found (e.g. an admin viewing a
  response for an establishment they have no relation to).
- `SurveyResponseHistoryRepository.getPage` (as specified in Phase 1) returns
  `Result<List<SurveyResponseSummary>>` with no total-pages signal — it unions two
  differently-shaped sources (remote activity feed, local queue) that don't share one
  pagination cursor. `SurveyResponseHistoryState.canLoadMore` uses a short-page heuristic
  (`items.size < pageSize` ⇒ no more) instead of `page < totalPages`.
- The edit screen independently re-fetches `GetSurveyResponseDetailUseCase` +
  `GetSurveyDetailUseCase` on its own `init` rather than receiving the detail screen's
  already-fetched answers through navigation — passing a full question/answer set through
  nav args would mean serializing it into the route. Every other screen in this app
  fetches its own data on load; the extra live `GET` is one cache-cheap (survey) and one
  already-required (response detail, since a stale local copy could be wrong per
  last-write-wins) round trip, not a new offline-readiness gap.

---

## Phase 7: Navigation + Entry Points + Role Gating

### Overview
Register routes, wire `AppNavHost`, add Home entry points gated by
`canRespondToSurveys`.

### Changes Required

#### 1. Routes
**File**: `presentation/navigation/Route.kt` — add:
```kotlin
@Serializable @SerialName("survey_list") data object SurveyList : Route()
@Serializable @SerialName("survey_response_form") data class SurveyResponseForm(val surveyId: String) : Route()
@Serializable @SerialName("survey_response_history") data object SurveyResponseHistory : Route()
@Serializable @SerialName("survey_response_detail") data class SurveyResponseDetail(val responseId: String) : Route()
@Serializable @SerialName("survey_response_edit") data class SurveyResponseEdit(val responseId: String) : Route()
```

#### 2. `AppNavHost`
Register all five new `*Screen(navController)` composable builders, following the
existing registration list (`app/AppNavHost.kt:32-43`).

#### 3. Home entry points
**File**: `presentation/feature/home/HomeViewModel.kt` — after the existing
`getMyProfile()` call, additionally compute `canRespondToSurveys(profile.role)` into a
new `HomeState.canRespondToSurveys: Boolean` field (defaults `false`, so the entry
points are hidden until profile load resolves — consistent with "Home never blocks,
never over-shows" per ADR-015's spirit).

**File**: `presentation/feature/home/HomeAction.kt` / `HomeDestination.kt` — add
`RespondSurveyClick` / `NavigateToSurveyList` and `MySurveysClick` /
`NavigateToSurveyResponseHistory`.

**File**: `presentation/feature/home/HomeScreen.kt` — add two entry points
(conditionally rendered on `state.canRespondToSurveys`), alongside the existing
"Nuevo sujeto"/"Nueva medición" actions.

**File**: `presentation/feature/home/HomeNavigation.kt` — wire the two new
destinations to `navController.navigate(Route.SurveyList)` /
`navController.navigate(Route.SurveyResponseHistory)`.

### Success Criteria

#### Automated Verification:
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./scripts/compile.sh` passes.
- [ ] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:assembleDebug --no-daemon` succeeds.
      (Could not run in this sandbox — no Android SDK/`ANDROID_HOME` configured, fails at
      dependency-resolution before touching any of this phase's code. Same limitation
      as the unit test suite; see Phase 3's note.)
- [x] `HomeStateMapperTest`/`HomeViewModelTest` extended: `canRespondToSurveys` is
      `true` for `field_worker`/`admin`/`superadmin` profiles, `false` for `analyst`.
      (Added to `HomeViewModelTest.kt`, since `canRespondToSurveys` is computed in
      `HomeViewModel`'s profile-load, not in `HomeStateMapper.deriveState` — compiles
      clean on the `iosSimulatorArm64` test-target proxy.)

#### Manual Verification:
- [ ] Full end-to-end walkthrough on both Android and iOS (per CLAUDE.md's per-platform
      manual-verification requirement): log in as `field_worker`, see both survey
      entry points on Home, complete the fill → view → edit flow.
- [ ] Log in as `analyst`: neither entry point is visible on Home.
- [ ] Log out mid-flow with a pending survey response/edit in the queue: confirm the
      logout wipe (`SqlDelightDatabaseCleaner`) clears the new tables and a
      subsequent login as a different user sees no leaked data.

**Deviations from plan**:
- The plan's `HomeNavigation.kt` (`homeScreen()`/its `navigate()` function) turned out to
  be dead code — `Route.Home` is actually composed inline inside `app/AuthedShell.kt`,
  with its own separate `when (destination)` dispatch (Home lives on `AuthedShell`'s
  *inner* NavHost; `CreateSubject`/`CreateMeasurement`/the new survey screens live on the
  *outer* nav via `LocalOuterNavController`, same as CLAUDE.md's documented
  Profile/EditProfile split). Wired the two new destinations in **both** places —
  `HomeNavigation.kt` for consistency/exhaustiveness, and `AuthedShell.kt` since that's
  the one actually reached at runtime.
- `HomeViewModel`'s reactive `combine` block (subject/measurement/connectivity →
  `deriveState`) rebuilds `HomeState` from scratch on every emission, explicitly carrying
  forward only a named allowlist of fields set elsewhere (destination, greeting, current
  establishment, …). `canRespondToSurveys` had to be added to that allowlist too, or every
  queue/connectivity change would silently reset it to `false` and hide the entry points.
- `Route.kt`'s five survey routes were actually added back in Phase 5/6 (see those phases'
  deviation notes), not here — each six-file screen's `*Navigation.kt` needed its `Route`
  entry to compile, so adding them together with the screens (rather than waiting for this
  phase) avoided a forward reference. This phase's own scope narrowed to wiring
  `AppNavHost` + the Home entry points, as planned.

---

## Testing Strategy

### Unit Tests
- `SurveyResponseRepositoryImpl`/`SurveyResponseEditRepositoryImpl` sync-engine state
  machines (Phase 3) — the highest-value tests, since a sync bug silently loses or
  duplicates field data.
- Edit-coalescing behavior (same-device, multiple offline edits before sync).
- `SurveyQuestionForm`'s answer-building + required-question validation (Phase 5/6
  ViewModels).
- `HomeViewModel`/`HomeStateMapper` role-gating (Phase 7).

### Manual Testing Steps
1. Online happy path: browse → fill every question type → submit → appears in "My
   survey responses" as `Synced`.
2. Offline happy path: airplane mode → fill → submit → `Pending` locally → reconnect →
   auto-syncs.
3. Edit happy path, online and offline (including the coalescing case — two offline
   edits to the same response before reconnecting).
4. Cross-device: view/edit a response this device never submitted, via the live detail
   fetch.
5. Role gating: `field_worker`/`admin`/`superadmin` see entry points and can
   submit/edit; `analyst` sees neither and (if reached via a direct route/deep link)
   is blocked with the standard 403 error surface.
6. Error surfacing for each documented error code (400/403/404/401/5xx) on both
   submit and edit — easiest to trigger via a temporary bad `establishment_id` (404),
   a targeted survey with an out-of-audience establishment (400), and airplane mode
   (5xx/network → retry).
7. Logout data wipe: pending/synced survey rows and cached surveys are gone after
   sign-out.

## Performance Considerations

No new performance-sensitive paths beyond what the existing sync engine already
handles (bounded `MAX_SYNC_ATTEMPTS`, single-flight `Mutex`, hourly GC). The
`CachedSurvey.detailJson` payload (a full question/option set) is small per-survey
(bounded by `question_count`), so no pagination/streaming concerns there, unlike the
activity feed's page-capped cache (`ActivityLocalStore.DEFAULT_CAP = 200`).

## Migration Notes

Schema migration only (Phase 2, v5 → v6) — no existing data to migrate, since this is
a new feature with no prior rows in any of the three new tables. Standard append-only
`.sqm` + snapshot ceremony; no downgrade path exists or is needed.

## References

- Backend integration guide: `.context/attachments/iuel9i/pasted_text_2026-07-11_14-34-57.txt`
  ("Survey Responses: Mobile Integration Guide," updated version with `GET
  /survey-responses/{id}` and field-worker access to `GET /surveys/{id}`).
- Existing offline-first template: `thoughts/plans/subjects_offline_first_sync.md`.
- Offline-reads precedent (establishment cache-first pattern):
  `thoughts/grilling/offline-reads-synthesis.md`, `adr-001` through `adr-011`.
- Current-establishment default precedent: `thoughts/grilling/current-establishment-synthesis.md`,
  `adr-012` through `adr-015`.
- Six-file screen pattern reference: `presentation/feature/home/`,
  `presentation/feature/subject/create_subject/`.
- Blur-aware validation pattern: `core/validation/` (documented in this repo's
  `CLAUDE.md`).
- SQLDelight migration ceremony: this repo's memory note `sqldelight-migration-workflow`.
