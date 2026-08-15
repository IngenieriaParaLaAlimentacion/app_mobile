# Survey Response Target (sujeto / establecimiento / anónima) — Implementation Plan

## Overview

A survey now declares **who it is answered for**: a `Subject`, an `Establishment`, or nobody
(anonymous). The app must pick that target *before* opening the questionnaire, send it on submit,
and handle the new server-side uniqueness rule (one response per survey+subject, one per
survey+establishment) that surfaces as a `409` **only at sync time**.

This is an incompatible contract change. The backend work must be merged and deployed first.

It also rides along with a **separate, already-merged backend refactor that relocates the
survey-response endpoints**. That relocation is not part of this ticket, but it breaks the app the
moment it reaches production, so absorbing it is Phase 0 — none of this ticket's acceptance criteria
are observable until it lands.

---

## Current State Analysis

### The endpoint relocation (blocking, but NOT a live production bug)

`SurveyResponseApi` (`data/survey/SurveyResponseApi.kt:24,43,61`) calls `api/v1/survey-responses`.
That is **correct against `origin/main`**, where `app/api/v1/survey_responses.py` declares
`APIRouter(prefix="/survey-responses")`. The app works in production today.

Backend commit **`a0074cc`** (2026-07-28, *"refactor: survey endpoints now live in two files"*) moved
`app/api/v1/survey_responses.py` → `app/api/v1/surveys/responses.py` and changed the prefix to
`/surveys/{survey_id}/responses`. Verified with `git merge-base --is-ancestor a0074cc <ref>`:
**merged into `origin/develop`, NOT into `origin/main`.**

| App calls today | `origin/main` serves | `origin/develop` serves |
|---|---|---|
| `POST api/v1/survey-responses` | same — **ok** | `POST /api/v1/surveys/{survey_id}/responses` |
| `PUT api/v1/survey-responses/{id}` | same — **ok** | `PUT /api/v1/surveys/{survey_id}/responses/{response_id}` |
| `GET api/v1/survey-responses/{id}` | same — **ok** | `GET /api/v1/surveys/{survey_id}/responses/{response_id}` |
| `GET api/v1/surveys/available` | ok | ok |
| `GET api/v1/surveys/{id}` | ok | ok |
| `GET api/v1/users/me/activity?type=survey_response` | ok | ok |

The break is therefore **latent, and fires on the next `develop → main` release**. When it does, the
failure is misleading rather than obvious: `404 → SurveyResponseUploadOutcome.NotFound →
markFailed("La encuesta o el establecimiento no existen.")`
(`SurveyResponseRepositoryImpl.kt:85-88`). A relevador would see **Pendiente → Error**, with a
*Reintentar* button that can never succeed.

**Environment mapping (confirmed with the team):** mobile **`tst` builds point at the backend's
`develop`**; `prod` tracks `main`. The backend repo's CI has no deploy job, so this is not
discoverable from the code — it came from the team directly.

**Therefore survey responses are broken on `tst` today.** Submit, edit, and response-detail all 404
against the tst backend, surfacing as **Pendiente → Error, "La encuesta o el establecimiento no
existen"** with a *Reintentar* button that cannot succeed. Two consequences:

- The current tst build cannot be used to validate *any* survey-response work, including this
  ticket's. Phase 0 is a prerequisite for testing the rest of the plan, not just for shipping it.
- Any tester exercising survey responses on tst right now is hitting a dead end with misleading copy.
  Worth telling them before they file it as a data problem.

`prod` is unaffected and stays correct until the backend `develop → main` release.

`docs/mobile/survey-responses.md` in the backend repo documents the `main` path and was accurate when
written; it is stale only relative to `develop`.

**Consequence beyond the path string:** `survey_id` becomes part of the URL for edit and detail, and
the app does not carry it everywhere it is needed:

- `Route.SurveyResponseDetail(responseId)` / `Route.SurveyResponseEdit(responseId)`
  (`presentation/navigation/Route.kt:105,109`) — no `surveyId`
- `SurveyResponseSummary` (`domain/model/survey/SurveyResponseHistory.kt:9`) — no `surveyId`; it is
  the model behind every history row *and* is persisted as JSON in `CachedActivityItem.payloadJson`
- `PendingSurveyResponseEdit.sq` — **no `surveyId` column at all**, so
  `SurveyResponseEditRepositoryImpl.syncPending` (`:99`) cannot build its PUT URL

### The target concept does not exist

- `Survey`/`SurveyDetail` carry `scope: SurveyScope { Generic, Targeted }`
  (`domain/model/survey/Survey.kt:6,12,40`), mapped from `"generic"|"targeted"`
  (`SurveyMapper.kt:67-71`) and cached as `'GENERIC'|'TARGETED'` in `CachedSurvey.scope`
  (`CachedSurvey.sq:7`, `SurveyLocalMapper.kt:7-16`).
- `SurveyResponseRequest.establishmentId` is **non-null** (`SurveyResponseDto.kt:11`), as is
  `PendingSurveyResponse.establishmentId` (`PendingSurveyResponse.sq:10`) and the backend column.
- The establishment is picked **inside** the form, in a bottom sheet, restricted to
  `EstablishmentStatus.Approved` from `GET /users/me/establishments`
  (`SurveyResponseFormViewModel.kt:88-90`) — exactly what CA4 removes.
- After a successful submit the form **clears answers and keeps the establishment**
  (`SurveyResponseFormViewModel.kt:124-132`) to loop through interviews at one comedor. Uniqueness
  makes that wrong for subject-targeted surveys.
- There is **no conflict state and no discard**: no `409` branch in the sync engine
  (`SurveyResponseRepositoryImpl.kt:72-93`), no `deleteById` in `PendingSurveyResponse.sq`, and the
  history row offers only *Reintentar* on `Failed` (`SurveyResponseHistoryContent.kt:101-108`).

### What can be reused as-is

- **Subject picker + inline create (CA3) is already built.** `SelectSubjectScreen` writes its result
  to `previousBackStackEntry.savedStateHandle[SELECTED_SUBJECT_RESULT]`
  (`SelectSubjectNavigation.kt:30-34`); `CreateSubjectScreen` pops past the picker and writes the
  same key (`CreateSubjectNavigation.kt:37-42`). `MeasurementNavigation.kt:20-28` is the reference
  consumer. The 409/`canonical_subject` handling lives in `SubjectApi.create`
  (`SubjectApi.kt:31-36`) and needs no change.
- **Offline-created subjects are already selectable** — `SubjectQueryRepositoryImpl` unions the
  local queue into page 1 of the picker (`:29-46`).
- **`MeasurementRepositoryImpl.kt:53-59` is the precedent for the hardest part of CA6**: before
  POSTing, it resolves a local subject id to the server's `canonicalId` when that subject 409'd on
  DNI, and defers the upload while the parent subject is still pending.
- **Sync ordering is already correct.** `DatabaseModule.kt` registers engines in dependency order —
  `SubjectRepository` → `MeasurementRepository` → `SurveyResponseRepository` →
  `SurveyResponseEditRepository` — and `SyncCoordinator.requestSync` runs them sequentially under a
  single-flight mutex (`SyncCoordinator.kt:47`).
- **`SelectEstablishmentScreen` exists** with search over `GET /api/v1/establishments`
  (`EstablishmentApi.kt:19`), but is multi-select and built for registration.

---

## Contract Reference (confirmed against the backend branch)

Routes (all four keep their existing paths; `{survey_id}` comes from the router prefix):

```
POST   /api/v1/surveys/{survey_id}/responses
GET    /api/v1/surveys/{survey_id}/responses/{response_id}          <- app uses this one
GET    /api/v1/surveys/{survey_id}/responses/{response_id}/detail   <- dashboard shape, not used
PUT    /api/v1/surveys/{survey_id}/responses/{response_id}
```

POST body:

```json
{ "id": "uuid?", "subject_id": "uuid?", "establishment_id": "uuid?",
  "answers": { "<question_id>": "<value>" }, "event_at": "ISO-8601" }
```

Exactly one of `subject_id` / `establishment_id` per the survey's target; **neither** for anonymous.

- **`response_target`** is the wire field name. Values `"subject" | "establishment" | "anonymous"`.
  It **replaces `scope`**; `scope` and `establishment_ids` disappear from every survey payload. It
  is present in both `SurveyListItem` (`GET /surveys`, `GET /surveys/available`) and the detail
  model (`GET /surveys/{survey_id}`).
- `GET /surveys/available` stays and stops filtering — every active survey, to every field worker
  (CA1). `GET /surveys/{survey_id}` keeps a `403` for field workers **only when the survey is
  inactive**; the establishment-based `403` is gone.
- The targeted-audience concept is deleted entirely (CA4). The `400`s that replace it are shape
  errors: wrong id kind for the target, both sent, neither sent when one is required, or anything
  sent to an anonymous survey. `404` if the `subject_id`/`establishment_id` does not exist.
- **`409` body** (`application/problem+json`), for both subject- and establishment-duplicates;
  anonymous never conflicts:

```json
{ "type": "/errors/survey-response-already-exists",
  "title": "Survey response already exists", "status": 409,
  "detail": "This survey has already been answered for that target.",
  "instance": "/api/v1/surveys/{survey_id}/responses",
  "existing_response": {
    "id": "0f7c…",
    "submitted_by": { "id": "3ab1…", "name": "Ana" },
    "event_at": "2026-08-12T14:03:00Z" } }
```

`existing_response` is a **top-level sibling** of `type`/`title`/`status`/`detail`/`instance`, not
nested under `extensions`. There is no machine-readable error-code field: discriminate on
**status 409 AND `type == "/errors/survey-response-already-exists"`**. Do not key off `409` alone —
`POST /subjects` also returns 409, with a `canonical_subject` extension and a different `type`.

- **Replay is id-first (CA8).** `submit()` looks up `data.id` before it resolves the target or
  touches uniqueness; found → returns the stored record with `200`. A dropped-connection retry gets
  `200`, not `409`. The concurrent race is disambiguated by PostgreSQL constraint name: a
  primary-key violation routes to the replay lookup, a `uq_survey_responses_survey_subject` /
  `uq_survey_responses_survey_establishment` violation routes to the `409`.
  **This only holds if the device reuses the same client-generated `id` on retry** — a fresh id for
  the same target is indistinguishable from a genuine second answer and correctly gets a `409`.
  The app already satisfies this: `localId` is minted once at enqueue and reused on every attempt
  (`SurveyResponseRepositoryImpl.kt:48`, `SurveyResponseUploadMapper.kt:30`).

Read shapes:

```jsonc
// POST / GET /{response_id} / PUT  — flat ids, no nested objects
{ "id": "…", "survey_id": "…", "subject_id": "uuid|null",
  "establishment_id": "uuid|null", "submitted_by_id": "…",
  "answers": {…}, "score_fies": 0, "event_at": "…",
  "created_at": "…", "updated_at": "…" }

// GET /users/me/activity item — both refs nullable
{ "id": "…", "event_at": "…",
  "survey": { "id": "…", "name": "…" },
  "establishment": { "id": "…", "name": "…", "city": "…", "province": "…" } | null,
  "subject": { "id": "…", "dni": 12345678, "sex": "…", "name": "…",
               "last_name": "…", "birth_date": "…" } | null }
```

History subtitle: branch on the survey's `response_target`, **not** on null-checking — so anonymous
does not render as an error state. Note the activity item itself does **not** carry
`response_target` (confirmed), so mobile reconstructs it from which ref is populated and then
branches on that; see Phase 6 for why the reconstruction is safe and where it is not.

`PUT` request body confirmed unchanged: `{id?, answers, event_at}` — the target is immutable on a
response. Only the *response* bodies of `POST` / `GET /{response_id}` / `PUT` gained `subject_id`
and `establishment_id`.

---

## Desired End State

1. The survey list shows every active survey, including for a relevador with zero establishments.
2. Tapping a survey routes by `response_target`: subject picker → form, establishment picker → form,
   or straight to the form for anonymous.
3. Submitting sends `subject_id` **or** `establishment_id` **or** neither, to
   `POST /api/v1/surveys/{surveyId}/responses`, and the response lands.
4. Offline, the response queues as `PENDING` with the client-generated id and syncs later unchanged.
5. A `409` at sync time becomes a terminal, non-retrying row showing who answered and when, with a
   **Descartar** action.
6. Editing and viewing a response work again (they 404 today).

**Verification:** submit one response of each target kind online and offline; force a duplicate and
confirm the conflict copy + discard; kill the network mid-POST and confirm the retry returns `200`
rather than a spurious conflict.

---

## What We're NOT Doing

- No new endpoints. The subject search and establishment list are reused exactly as they are.
- No pre-flight duplicate check before answering — the conflict is discovered at sync time, by
  design, to avoid a connectivity dependency at answer time.
- No linking from the conflict message to the conflicting response (a relevador viewing someone
  else's response gets `403`).
- No change to the target after submit — the target is immutable; `PUT` stays answers-only.
- No `score_fies` support. It is new on the response payload; `ignoreUnknownKeys` absorbs it.
- No iOS-specific work — the whole feature is `commonMain`.
- Not adopting `GET /{response_id}/detail` (the dashboard shape with resolved option text).

---

## Implementation Approach

Eight phases, ordered so the app compiles and the queue stays coherent after each. Phase 0 is a
standalone bug fix that could ship on its own; Phases 1–2 land the contract and schema; 3–5 land the
user-visible flow; 6–7 clean up the read paths and tests.

Two cross-cutting decisions worth stating up front:

**Conflict is modelled as a payload on a `FAILED` row, not a new `SyncState`.** `SyncState`
(`domain/model/subject/SyncState.kt`) is shared with subjects and measurements; adding a `Conflict`
variant would force exhaustive-`when` updates across unrelated mappers and UI. Instead
`PendingSurveyResponse` gains nullable `conflictBy` / `conflictAt` columns, and the UI branches on
`conflictBy != null` to swap *Reintentar* for the conflict copy. Same reasoning that kept
`SurveyResponseUpload` free of a `MERGED` state (`PendingSurveyResponse.sq:4-5`).

**Null target ids must be omitted from the POST body, not sent as `null`.** The shared `Json` sets
`encodeDefaults = true` and leaves `explicitNulls` at its `true` default
(`HttpClientFactory.kt:26-30`, `RepositoriesModule.kt:57`), so a nullable field would serialize as
`"subject_id": null` — which the backend may reject under "anything sent to an anonymous survey".
Flipping `explicitNulls` globally would change every request and response in the app. Instead
annotate the two fields with `@EncodeDefault(EncodeDefault.Mode.NEVER)`, which takes precedence over
`encodeDefaults = true` per-property.

---

## Phase 0: Move to the relocated endpoints and thread `surveyId`

### Overview
Point the three calls at the post-`a0074cc` routes and carry `survey_id` everywhere it is now needed.

**Do not ship this ahead of the backend release.** The app currently matches `origin/main`; adopting
the new paths before `develop → main` ships would break survey responses against production. Phase 0
must land no earlier than the backend release that carries `a0074cc` — which is the same release that
carries this ticket's backend work, so in practice they ship together.

### Changes Required

#### 1. API client
**File**: `data/survey/SurveyResponseApi.kt`
**Changes**: every method takes `surveyId` and builds the real path.

```kotlin
suspend fun create(surveyId: String, body: SurveyResponseRequest): SurveyResponseUploadOutcome =
    runCatching {
        val response = client.post("api/v1/surveys/$surveyId/responses") { … }
        …
    }

suspend fun edit(surveyId: String, responseId: String, body: SurveyResponseEditRequest) =
    client.put("api/v1/surveys/$surveyId/responses/$responseId") { … }

suspend fun detail(surveyId: String, responseId: String) =
    client.get("api/v1/surveys/$surveyId/responses/$responseId")
```

#### 2. Edit queue gains `surveyId`
**Files**: `sqldelight/…/db/PendingSurveyResponseEdit.sq`, new `sqldelight/…/db/6.sqm`,
`domain/model/survey/SurveyResponseEditUpload.kt`,
`data/survey/mapper/SurveyResponseEditUploadMapper.kt`,
`data/survey/local/SurveyResponseEditLocalStore.kt` + `DefaultSurveyResponseEditLocalStore.kt`,
`domain/repository/SurveyResponseEditRepository.kt`, `data/survey/SurveyResponseEditRepositoryImpl.kt`,
`domain/usecase/survey/EditSurveyResponseUseCase.kt`

`ALTER TABLE … ADD COLUMN` is supported by the pinned `sqlite_3_18` dialect, so this one is additive.
Existing rows get `''`; they are pre-fix rows that could never have synced anyway.

```sql
-- 6.sqm (v6 -> v7)
ALTER TABLE PendingSurveyResponseEdit ADD COLUMN surveyId TEXT NOT NULL DEFAULT '';
```

`enqueueEdit` takes `surveyId` and persists it; `syncPending` passes
`api.edit(edit.surveyId, edit.responseId, …)`.

#### 3. Routes and the history row carry `surveyId`
**Files**: `presentation/navigation/Route.kt`,
`presentation/feature/survey/survey_response_detail/*`, `survey_response_edit/*`,
`survey_response_history/SurveyResponseHistoryAction.kt` + `…Content.kt` + `…ViewModel.kt` + `…State.kt`,
`domain/model/survey/SurveyResponseHistory.kt`, `data/survey/mapper/SurveyResponseHistoryMapper.kt`

```kotlin
@Serializable @SerialName("survey_response_detail")
data class SurveyResponseDetail(val surveyId: String, val responseId: String) : Route()

@Serializable @SerialName("survey_response_edit")
data class SurveyResponseEdit(val surveyId: String, val responseId: String) : Route()
```

`SurveyResponseSummary` gains `val surveyId: String`. It is `@Serializable` and persisted into
`CachedActivityItem.payloadJson`, so **old cached rows would fail to decode** — the same `6.sqm`
migration clears the `SURVEY_RESPONSE` rows of `CachedActivityItem` (see Phase 1, which clears
`CachedSurvey` for the same reason). `SurveyResponseHistoryAction.ResponseClick` carries both ids;
`SurveyResponseDetailDestination.SurveyResponseEdit` and its navigator pass `surveyId` through.

#### 4. Detail read path
**Files**: `domain/repository/SurveyResponseHistoryRepository.kt`,
`data/survey/SurveyResponseHistoryRepositoryImpl.kt`,
`domain/usecase/survey/GetSurveyResponseDetailUseCase.kt`

`getDetail(surveyId, responseId)`; the offline `fallbackToLocal` path is unchanged (it already has
`upload.surveyId`, `:118`).

### Success Criteria

#### Automated Verification:
- [x] `./scripts/compile.sh`
- [x] `./gradlew :composeApp:generateCommonMainAppDatabaseSchema --no-daemon` writes `databases/7.db` (committed)
- [x] `./gradlew :composeApp:testProdDebugUnitTest --no-daemon`

#### Manual Verification:
Run these against a backend built from `develop` (or a released `main` that carries `a0074cc`) —
against today's `main` they will fail by design, since this phase moves off the paths `main` serves.
- [ ] Submitting a response on an existing survey reaches **Sincronizado**, not **Error**
- [ ] Opening a response from Actividades loads its answers
- [ ] Editing a response saves and the detail reflects it
- [ ] An app upgraded from 0.1.1 (existing local DB) launches without a migration crash

---

## Phase 1: `response_target` replaces `scope`

### Overview
Swap the survey classification end to end — wire, domain, cache — and stop trusting the old
`403`-means-no-establishment-access reading of survey detail.

### Changes Required

#### 1. Domain model
**File**: `domain/model/survey/Survey.kt`

```kotlin
@Serializable
enum class SurveyResponseTarget { Subject, Establishment, Anonymous }
```

`SurveyScope` is deleted. `Survey` and `SurveyDetail` take `val responseTarget: SurveyResponseTarget`
in its place.

#### 2. DTOs and mapper
**Files**: `data/survey/dto/SurveyDto.kt`, `data/survey/mapper/SurveyMapper.kt`

`SurveyListItemResponse.scope` / `SurveyDetailResponse.scope` become
`@SerialName("response_target") val responseTarget: String`.

An unrecognized value must **not** crash the list the way `toDomainScope` does today
(`SurveyMapper.kt:70` throws). If the backend later adds a fourth target, an old app would take down
the whole survey list. Map leniently and drop the row:

```kotlin
internal fun String.toDomainResponseTargetOrNull(): SurveyResponseTarget? = when (this) {
    "subject" -> SurveyResponseTarget.Subject
    "establishment" -> SurveyResponseTarget.Establishment
    "anonymous" -> SurveyResponseTarget.Anonymous
    else -> null   // forward-compatible: a target this build can't answer is hidden, not fatal
}

internal fun PaginatedSurveysResponse.toDomain(): PagedSurveys = PagedSurveys(
    items = items.mapNotNull { it.toDomainOrNull() },
    page = page,
    totalPages = totalPages,
)
```

`SurveyDetailResponse.toDomain()` keeps returning a non-null `SurveyDetail`; an unknown target there
is a `SurveyFeatureError.Unknown` failure, since there is nothing sensible to render.

#### 3. Cache
**Files**: `sqldelight/…/db/CachedSurvey.sq`, `sqldelight/…/db/6.sqm`,
`data/survey/mapper/SurveyLocalMapper.kt`, `data/survey/local/DefaultSurveyLocalStore.kt`

`scope TEXT NOT NULL` becomes `responseTarget TEXT NOT NULL` storing
`'SUBJECT'|'ESTABLISHMENT'|'ANONYMOUS'`. Old rows hold `'GENERIC'|'TARGETED'`, which map to nothing,
so the migration drops and recreates the table — it is a pure read-through cache and re-warms via
`WarmSurveyCacheUseCase` on the next online launch (`AuthedShell.kt:131`).

```sql
-- 6.sqm, continued
DROP TABLE IF EXISTS CachedSurvey;
CREATE TABLE CachedSurvey (
    id             TEXT NOT NULL PRIMARY KEY,
    name           TEXT NOT NULL,
    responseTarget TEXT NOT NULL,   -- 'SUBJECT' | 'ESTABLISHMENT' | 'ANONYMOUS'
    isActive       INTEGER NOT NULL,
    questionCount  INTEGER NOT NULL,
    detailJson     TEXT,
    updatedAt      INTEGER NOT NULL
);

-- payloadJson shape changed (SurveyResponseSummary gained surveyId + target fields).
-- Scoped to SURVEY_RESPONSE: this table is shared with SUBJECT and MEASUREMENT rows
-- (CachedActivityItem.sq:3), whose payloads are unaffected and must not be discarded.
DELETE FROM CachedActivityItem WHERE type = 'SURVEY_RESPONSE';
```

`detailJson` holds a serialized `SurveyDetail`, whose shape also changes — dropping the table
discards those too, which is the point.

#### 4. Survey detail 403 semantics
**File**: `data/survey/SurveyApi.kt`

The status mapping is unchanged; only the comment is. `403` now means "survey deactivated", not
"no approved establishment for this targeted survey". Note it at `SurveyDetailOutcome.Forbidden` so
nobody re-derives the old meaning.

### Success Criteria

#### Automated Verification:
- [x] `./scripts/compile.sh`
- [x] `./gradlew :composeApp:generateCommonMainAppDatabaseSchema --no-daemon`
- [x] `./gradlew :composeApp:testProdDebugUnitTest --no-daemon`
- [x] New `SurveyMapperTest` case: unknown `response_target` in a list page is dropped, not thrown

#### Manual Verification:
- [ ] A relevador with **zero** establishments sees the full survey list and can open a survey (CA1)
- [ ] Killing the network still shows the cached list after it re-warms once online

---

## Phase 2: Local queue schema and domain models for targets

### Overview
Make the response queue able to hold any of the three targets, and keep the denormalized display
label that lets the history list render offline.

### Changes Required

#### 1. Queue table
**Files**: `sqldelight/…/db/PendingSurveyResponse.sq`, `sqldelight/…/db/6.sqm`

`establishmentId`/`establishmentName` must become nullable, which `ALTER TABLE` cannot do — recreate
and copy. Existing rows are establishment-scoped by construction.

```sql
-- 6.sqm, continued
ALTER TABLE PendingSurveyResponse RENAME TO PendingSurveyResponse_old;

CREATE TABLE PendingSurveyResponse (
    localId        TEXT    NOT NULL PRIMARY KEY,
    surveyId       TEXT    NOT NULL,
    surveyName     TEXT    NOT NULL,
    responseTarget TEXT    NOT NULL,   -- 'SUBJECT' | 'ESTABLISHMENT' | 'ANONYMOUS'
    targetId       TEXT,               -- subject or establishment id; NULL when anonymous
    targetLabel    TEXT,               -- denormalized display name; NULL when anonymous
    answersJson    TEXT    NOT NULL,
    eventAt        TEXT    NOT NULL,
    syncState      TEXT    NOT NULL,
    canonicalId    TEXT,
    errorMessage   TEXT,
    conflictBy     TEXT,               -- 409: existing_response.submitted_by.name
    conflictAt     TEXT,               -- 409: existing_response.event_at (ISO-8601)
    attemptCount   INTEGER NOT NULL DEFAULT 0,
    createdAt      INTEGER NOT NULL,
    updatedAt      INTEGER NOT NULL
);

INSERT INTO PendingSurveyResponse(localId, surveyId, surveyName, responseTarget, targetId,
    targetLabel, answersJson, eventAt, syncState, canonicalId, errorMessage, conflictBy,
    conflictAt, attemptCount, createdAt, updatedAt)
SELECT localId, surveyId, surveyName, 'ESTABLISHMENT', establishmentId, establishmentName,
    answersJson, eventAt, syncState, canonicalId, errorMessage, NULL, NULL,
    attemptCount, createdAt, updatedAt
FROM PendingSurveyResponse_old;

DROP TABLE PendingSurveyResponse_old;
CREATE INDEX PendingSurveyResponse_state ON PendingSurveyResponse(syncState);
```

A single `targetId`/`targetLabel` pair is used rather than four columns because exactly one target
can ever be set — a two-column shape makes "both set" unrepresentable instead of merely invalid.

New queries: `deleteById`, and `markConflict`.

```sql
markConflict:
UPDATE PendingSurveyResponse SET syncState = 'FAILED', errorMessage = NULL, conflictBy = ?,
    conflictAt = ?, attemptCount = attemptCount + 1, updatedAt = ? WHERE localId = ?;

deleteById:
DELETE FROM PendingSurveyResponse WHERE localId = ?;
```

#### 2. Domain models
**File**: `domain/model/survey/SurveyResponseUpload.kt`

```kotlin
/** The chosen answer target. Exactly one shape is valid per survey's [SurveyResponseTarget]. */
sealed interface ResponseTargetRef {
    data class Subject(val id: String, val label: String) : ResponseTargetRef
    data class Establishment(val id: String, val label: String) : ResponseTargetRef
    data object Anonymous : ResponseTargetRef
}

data class NewSurveyResponse(
    val surveyId: String,
    val surveyName: String,
    val target: ResponseTargetRef,
    val answers: Map<String, SurveyAnswerValue>,
    val eventAtMs: Long,
)

data class SurveyResponseUpload(
    val localId: String,
    val surveyId: String,
    val surveyName: String,
    val target: ResponseTargetRef,
    val answersJson: String,
    val eventAt: String,
    val syncState: SyncState,
    val canonicalId: String?,
    val errorMessage: String?,
    /** Set only on a 409: who already answered, and when. Non-null ⇒ terminal, never retried. */
    val conflict: ResponseConflict?,
    val attemptCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class ResponseConflict(val submittedByName: String, val eventAt: String)
```

`isEligibleAt` gains `if (conflict != null) return false` so a conflicted row can never be picked up
by the backoff sweep (CA7: "el envío no queda reintentando indefinidamente").

#### 3. Store, mapper, cleaner
**Files**: `data/survey/local/SurveyResponseLocalStore.kt` + `Default…`,
`data/survey/mapper/SurveyResponseUploadMapper.kt`, `data/db/SqlDelightDatabaseCleaner.kt`

Store gains `markConflict(localId, ResponseConflict)` and `delete(localId)`. `SqlDelightDatabaseCleaner`
needs no change (it already wipes by table name, `:15-22`).

### Success Criteria

#### Automated Verification:
- [x] `./scripts/compile.sh`
- [x] `./gradlew :composeApp:generateCommonMainAppDatabaseSchema --no-daemon` → `databases/7.db`
- [x] `DefaultSurveyResponseLocalStoreTest` covers insert/read of each of the three target shapes,
      `markConflict`, and `delete`

#### Manual Verification:
- [ ] Upgrading from a 0.1.1 build with queued responses does not crash and the rows still list

---

## Phase 3: Pick the target before the form

### Overview
CA2. The survey list routes by `response_target` through the existing pickers, then into the form
with the chosen target as route arguments.

### Changes Required

#### 1. Form route carries the target
**File**: `presentation/navigation/Route.kt`

```kotlin
@Serializable
@SerialName("survey_response_form")
data class SurveyResponseForm(
    val surveyId: String,
    val targetId: String? = null,
    val targetLabel: String? = null,
) : Route()
```

The *kind* of target is not a route argument — the form reads it from `SurveyDetail.responseTarget`,
which it already loads and which is cached offline. That keeps one source of truth.

#### 2. Survey list routes by target
**Files**: `survey_list/SurveyListDestination.kt`, `…State.kt`, `…ViewModel.kt`, `…Navigation.kt`

```kotlin
sealed class SurveyListDestination {
    data object Back : SurveyListDestination()
    data class SurveyResponseForm(
        val surveyId: String,
        val targetId: String? = null,
        val targetLabel: String? = null,
    ) : SurveyListDestination()
    data object SelectSubject : SurveyListDestination()
    data object SelectEstablishment : SurveyListDestination()
}
```

`SurveyClick` now carries the whole `Survey` (it already has `responseTarget`), so the reducer can
branch without a lookup. The tapped survey is held in `SurveyListState.pendingSurveyId` while the
picker is on top — the list's `ViewModel` survives, since the picker is pushed above it.

`SurveyListNavigation` consumes the picker result on the list's own back stack entry, mirroring
`MeasurementNavigation.kt:20-28`:

```kotlin
composable<Route.SurveyList> { entry ->
    val subjectJson by entry.savedStateHandle
        .getStateFlow<String?>(SELECTED_SUBJECT_RESULT, null).collectAsStateWithLifecycle()
    val establishmentsJson by entry.savedStateHandle
        .getStateFlow<String?>(SELECTED_ESTABLISHMENTS_RESULT, null).collectAsStateWithLifecycle()
    SurveyListScreenRoot(
        pickedSubject = subjectJson?.let { runCatching { Json.decodeFromString<SubjectSummary>(it) }.getOrNull() },
        pickedEstablishment = establishmentsJson?.let {
            runCatching { Json.decodeFromString<List<Establishment>>(it) }.getOrNull()?.firstOrNull()
        },
        onResultConsumed = {
            entry.savedStateHandle[SELECTED_SUBJECT_RESULT] = null
            entry.savedStateHandle[SELECTED_ESTABLISHMENTS_RESULT] = null
        },
        navigate = { navigate(it, navController) },
    )
}
```

Resulting back stack after a pick: `SurveyList → SurveyResponseForm` — the picker pops itself on
confirm (`SelectSubjectNavigation.kt:33`), so it is not left between them.

The subject label is `"$name $lastName"`; the establishment label is `establishment.name`. Both are
persisted as `targetLabel` so the history list renders offline with no join.

#### 3. Single-select mode on the establishment picker
**Files**: `presentation/navigation/Route.kt`,
`establishment/select_establishment/{SelectEstablishmentViewModel,State,Screen}.kt`

```kotlin
data class SelectEstablishment(
    val preselectedJson: String,
    val singleSelect: Boolean = false,
) : Route()
```

In single-select mode, `EstablishmentToggled` confirms immediately with a one-element list and the
*Listo* button is hidden — a picker with one choice needs no confirm step. The result key stays
`SELECTED_ESTABLISHMENTS_RESULT`, so registration is untouched.

#### 4. Form consumes the target instead of picking one
**Files**: `survey_response_form/{ViewModel,State,Action,Screen}.kt`

Deleted: `EstablishmentPickerOpen`/`Dismiss`/`EstablishmentSelected`, `establishmentOptions`,
`showEstablishmentPicker`, the `getMyEstablishments` filter at `:88-90`, and the bottom sheet.

The form shows the chosen target read-only in a header row, and for establishment surveys keeps the
Home default pre-fill: if the route carries no `targetId` and
`GetCurrentEstablishmentUseCase` resolves one, the list is entered with it already picked (decision
11 — pre-fill, not gate; the picker still offers every establishment per CA4).

### Success Criteria

#### Automated Verification:
- [x] `./scripts/compile.sh`
- [x] `SurveyListViewModelTest`: each of the three targets emits the right destination
- [x] `SurveyResponseFormViewModelTest`: `canSubmit` is false when a required target id is absent
- [x] `SelectEstablishmentViewModelTest`: single-select replaces rather than accumulates

#### Manual Verification:
- [ ] Subject survey → picker → search by DNI → form, with the subject shown (CA3)
- [ ] Subject not on file → *Crear sujeto* → back in the form with the new subject selected
- [ ] Establishment survey → picker lists establishments the user has **no** access to (CA4)
- [ ] Anonymous survey → straight into the questionnaire (CA2)
- [ ] Back from a picker returns to the survey list with nothing submitted

---

## Phase 4: Submit with a target, and sync it

### Overview
CA5, CA6, CA8. Build the POST body from the target, resolve offline subject ids to their canonical
server ids, and classify the `409`.

### Changes Required

#### 1. Request DTO
**File**: `data/survey/dto/SurveyResponseDto.kt`

```kotlin
@Serializable
data class SurveyResponseRequest(
    val id: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("subject_id") val subjectId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("establishment_id") val establishmentId: String? = null,
    val answers: Map<String, JsonElement>,
    @SerialName("event_at") val eventAt: String,
)
```

`survey_id` leaves the body — it is in the path now. `@EncodeDefault(NEVER)` keeps a null target out
of the JSON entirely; without it the shared `Json` would emit `"subject_id": null` and risk the
"anything sent to an anonymous survey" `400`.

`SurveyResponseResponse.establishmentId` becomes nullable and gains
`@SerialName("subject_id") val subjectId: String? = null`. Confirmed: `POST`, `GET /{response_id}`
and `PUT` all share this model, so all three gain the two keys.

The **`PUT` request body is unchanged** — still `{id?, answers, event_at}`. The target is immutable
on a response, so `SurveyResponseEditRequest` and the whole edit queue need no target fields.

Third place that must follow: `SurveyResponseResponseDomain` (`SurveyMapper.kt:59-65`), the thin
projection the sync engines and history repo read, also declares `establishmentId: String` non-null.
It becomes nullable and gains `subjectId`.

#### 2. Conflict DTO
**File**: new `data/survey/dto/SurveyResponseConflictProblem.kt`

Modelled on `SubjectConflictProblem.kt`, plus the `type` discriminator, since `409` alone is
ambiguous across this API.

```kotlin
@Serializable
data class SurveyResponseConflictProblem(
    val type: String? = null,
    @SerialName("existing_response") val existingResponse: ExistingResponseDto? = null,
)

@Serializable
data class ExistingResponseDto(
    val id: String,
    @SerialName("submitted_by") val submittedBy: SubmittedByDto? = null,
    @SerialName("event_at") val eventAt: String? = null,
)

@Serializable
data class SubmittedByDto(val id: String, val name: String? = null)

const val SURVEY_RESPONSE_CONFLICT_TYPE = "/errors/survey-response-already-exists"
```

#### 3. API outcome
**File**: `data/survey/SurveyResponseApi.kt`

```kotlin
409 -> {
    val problem = runCatching { response.body<SurveyResponseConflictProblem>() }.getOrNull()
    if (problem?.type == SURVEY_RESPONSE_CONFLICT_TYPE) {
        SurveyResponseUploadOutcome.AlreadyAnswered(
            submittedByName = problem.existingResponse?.submittedBy?.name,
            eventAt = problem.existingResponse?.eventAt,
        )
    } else {
        // A 409 this build doesn't recognize: surface it, don't spin on it.
        SurveyResponseUploadOutcome.InvalidData
    }
}
```

#### 4. Sync engine
**File**: `data/survey/SurveyResponseRepositoryImpl.kt`

Two additions. First, subject-id resolution and gating, a direct port of
`MeasurementRepositoryImpl.kt:53-73` (this repository takes a new `SubjectLocalStore` dependency):

```kotlin
// Subject targets may point at a subject this device queued offline. Resolve to the server's
// canonical id (set when that subject 409'd on DNI) and defer while the parent is still in flight.
val target = upload.target
val resolvedTargetId = when {
    target !is ResponseTargetRef.Subject -> (target as? ResponseTargetRef.Establishment)?.id
    else -> {
        val subject = subjects.find(target.id)
        when {
            subject == null -> target.id                       // already a server id
            subject.syncState == SyncState.Synced ||
                subject.syncState == SyncState.Merged -> subject.canonicalId ?: target.id
            subject.syncState == SyncState.Failed -> {
                local.markFailed(upload.localId, "El sujeto no se pudo sincronizar.")
                failed++; return@forEach
            }
            else -> { pending++; return@forEach }              // parent still queued: try next pass
        }
    }
}
```

Ordering is already guaranteed: `SubjectRepository` runs before `SurveyResponseRepository` in the
engine list (`DatabaseModule.kt`), so within a single sweep the subject lands first.

Second, the conflict branch:

```kotlin
is SurveyResponseUploadOutcome.AlreadyAnswered -> {
    local.markConflict(
        upload.localId,
        ResponseConflict(
            submittedByName = outcome.submittedByName ?: "otro relevador",
            eventAt = outcome.eventAt.orEmpty(),
        ),
    )
    failed++
}
```

`markConflict` writes a terminal row, and `isEligibleAt` returns false while `conflict != null`, so
no automatic retry ever picks it up again (CA7).

#### 5. Enqueue and use case
**Files**: `SurveyResponseRepositoryImpl.enqueue`, `domain/usecase/survey/SubmitSurveyResponseUseCase.kt`,
`survey_response_form/SurveyResponseFormViewModel.kt`

`buildNewResponse` builds a `ResponseTargetRef` from `SurveyDetail.responseTarget` plus the route
arguments, and refuses to build when a required target id is missing.

#### 6. After-submit navigation
**Files**: `survey_response_form/{Destination,ViewModel,Navigation}.kt`

```kotlin
sealed class SurveyResponseFormDestination {
    data object Back : SurveyResponseFormDestination()
    /** Target consumed — that subject/establishment can't answer again, so return to the list. */
    data object Done : SurveyResponseFormDestination()
}
```

Subject and establishment targets emit `Done` → `popBackStack()`. Anonymous keeps today's
clear-answers-and-stay loop (`:124-132`), which is still correct — anonymous surveys have no
uniqueness rule.

### Success Criteria

#### Automated Verification:
- [x] `./scripts/compile.sh`
- [x] `SurveyResponseSyncEngineTest`: subject / establishment / anonymous each produce the right body
- [x] Serialization test: the anonymous body contains **neither** `subject_id` nor `establishment_id`
      (guards the `@EncodeDefault` decision)
- [x] `SurveyResponseSyncEngineTest`: `409` + matching `type` → conflict row, not retried on the next
      sweep; `409` with a different `type` → ordinary failure
- [x] `SurveyResponseSyncEngineTest`: a subject-targeted response defers while its subject is
      `PENDING`, then uploads with `canonicalId` after the subject merges
- [x] `SurveyResponseSyncEngineTest`: a repeated sweep reuses the same `id` (CA8 replay contract)

#### Manual Verification:
- [ ] Online submit of each target kind reaches **Sincronizado** (CA5)
- [ ] Airplane mode: submit queues as **Pendiente**, then syncs on reconnect (CA6)
- [ ] Offline-create a subject, answer a subject survey for them, reconnect — both land, and the
      response points at the surviving subject even when the DNI merged
- [ ] Network dropped mid-POST: the retry lands as success, **not** as a duplicate conflict

---

## Phase 5: Surface the conflict and allow discarding

### Overview
CA7 and CA9. A conflicted response explains itself and can be thrown away; ordinary failures keep
*Reintentar* and gain *Descartar* too.

> **Note on the *Descartar*-on-`Failed` decision.** It was originally agreed on the premise that
> testers had unfixable stuck rows from a live 404 bug. That premise was wrong — the app matches
> `main` and there is no live bug. The option still stands on its own merits: a row dead-lettered
> after `MAX_SYNC_ATTEMPTS`, or failed on a terminal `400`/`403`, has no other escape today, and
> *Reintentar* on it is a button that cannot work. Worth a second confirmation before implementing,
> since the original justification no longer applies.

### Changes Required

#### 1. Discard plumbing
**Files**: `domain/repository/SurveyResponseRepository.kt`,
`data/survey/SurveyResponseRepositoryImpl.kt`,
new `domain/usecase/survey/DiscardSurveyResponseUploadUseCase.kt`, `di/UseCasesModule.kt`

Mirrors `DeleteSubjectUploadUseCase` exactly.

```kotlin
class DiscardSurveyResponseUploadUseCase(private val repository: SurveyResponseRepository) {
    suspend operator fun invoke(localId: String): Result<Unit> = repository.delete(localId)
}
```

#### 2. History row
**Files**: `survey_response_history/{Action,State,ViewModel,Content}.kt`,
`domain/model/survey/SurveyResponseHistory.kt`

`SurveyResponseSummary` gains `val conflict: ResponseConflict?`. The row branches:

```kotlin
when {
    item.conflict != null -> {
        Text("Ya la respondió ${item.conflict.submittedByName} el ${formatDate(item.conflict.eventAt)}")
        AlButton(onClick = { onAction(DiscardClick(item.id)) }) { Text("Descartar") }
    }
    item.syncState == SyncState.Failed -> {
        item.errorMessage?.let { Text(it) }          // CA9: never silently dropped
        AlButton(onClick = { onAction(RetryUploadClick(item.id)) }) { Text("Reintentar") }
        AlButton(onClick = { onAction(DiscardClick(item.id)) }, variant = AlButtonVariant.Outline) {
            Text("Descartar")
        }
    }
}
```

The pill reads **Conflicto** (`AlPillTone.Danger`) when `conflict != null`, otherwise as today. A
confirm dialog guards *Descartar* — it destroys unsynced answers.

`errorMessage` is already populated for every failure path in the sync engine
(`SurveyResponseRepositoryImpl.kt:78-92`) and is now rendered rather than dropped, which is CA9.

### Success Criteria

#### Automated Verification:
- [x] `./scripts/compile.sh`
- [x] `SurveyResponseHistoryViewModelTest`: discard removes the row; a conflicted row exposes no retry
- [x] `./gradlew :composeApp:testProdDebugUnitTest --no-daemon`

#### Manual Verification:
- [ ] Answer the same survey for the same subject twice on one device: the second shows the conflict
      copy naming who answered and when (CA7)
- [ ] *Descartar* removes it and it does not come back after a sync sweep
- [ ] Network error copy is legible and the row is never silently dropped (CA9)
- [ ] A row dead-lettered after `MAX_SYNC_ATTEMPTS` can be cleared

---

## Phase 6: History and activity feed with nullable targets

### Overview
The activity feed now returns a nullable establishment and a nullable subject. Render per target
kind so anonymous looks intentional.

### Changes Required

#### 1. Activity DTO
**File**: `data/survey/dto/SurveyResponseActivityDto.kt`

`establishment` becomes nullable and gains `province`; a nullable `subject` block is added.

**`response_target` is confirmed absent from `SurveyResponseListItem`** — the item is exactly
`{id, event_at, survey: {id, name}, establishment|null, subject|null}`, and `SurveyRefResponse` is a
plain `IdentifierRefSchema` (id + name). Mobile therefore derives the target, but parses the field
when present so no second change is needed if the backend adds it later:

```kotlin
@SerialName("response_target") val responseTarget: String? = null,
```

```kotlin
/** Prefer the server's own value; fall back to deriving it from which ref is populated.
 *  The derivation is safe by construction on the server side — `ck_survey_responses_single_target`
 *  forbids both refs being set, the service rejects a payload omitting a required entity,
 *  `response_target` is locked once a survey has responses, and both FKs are ON DELETE RESTRICT,
 *  so a deleted entity cannot silently null a ref out. The risk it does NOT cover is a future
 *  fourth target value that populates neither ref: that would silently render as "Anónima". */
private fun SurveyResponseActivitySummaryResponse.target(): SurveyResponseTarget =
    responseTarget?.toDomainResponseTargetOrNull()
        ?: when {
            subject != null -> SurveyResponseTarget.Subject
            establishment != null -> SurveyResponseTarget.Establishment
            else -> SurveyResponseTarget.Anonymous
        }
```

Because the field is optional on the wire and defaulted here, mobile and backend can ship in either
order — this is not a coordination point.

#### 2. Summary model and mapper
**Files**: `domain/model/survey/SurveyResponseHistory.kt`,
`data/survey/mapper/SurveyResponseHistoryMapper.kt`

`SurveyResponseSummary.establishmentName: String` becomes
`responseTarget: SurveyResponseTarget` + `targetLabel: String?`:

```kotlin
val targetLabel = when (responseTarget) {
    SurveyResponseTarget.Establishment -> establishment?.name
    SurveyResponseTarget.Subject -> subject?.let { "${it.name} ${it.lastName}" }
    SurveyResponseTarget.Anonymous -> null
}
```

The row subtitle uses `targetLabel ?: "Anónima"`; it never falls back to a null-check, so an
anonymous response reads as anonymous rather than as missing data.

#### 3. Response detail
**Files**: `domain/model/survey/SurveyResponseHistory.kt`,
`data/survey/SurveyResponseHistoryRepositoryImpl.kt`, `survey_response_detail/*`

`SurveyResponseDetail.establishmentId` becomes the same target pair. The detail screen's
establishment lookup (`SurveyResponseDetailViewModel.kt:117-118`) becomes a target label that no
longer depends on `getMyEstablishments` — that lookup fails by construction now that any
establishment is selectable (CA4), so it falls back to `targetLabel` from the summary/queue row.

#### 4. Home recent activity
**File**: `presentation/feature/home/HomeStateMapper.kt:111-115`

`toRecentUi()` uses `targetLabel` with the same anonymous fallback.

### Success Criteria

#### Automated Verification:
- [x] `./scripts/compile.sh`
- [x] `SurveyResponseHistoryRepositoryImplTest`: activity items with a subject / an establishment /
      neither each map to the right label
- [x] `HomeStateMapperTest` still green

#### Manual Verification:
- [ ] Actividades → Encuestas shows subject names, establishment names, and "Anónima" appropriately
- [ ] Home recent activity matches
- [ ] Opening any of the three from the list loads its detail

---

## Phase 7: Regression pass and cleanup

### Changes Required
- Delete `SurveyScope` and every remaining reference.
- Update `docs/` if the app-side API notes mention `/survey-responses`.
- Refresh `CLAUDE.md` if the survey feature description references `scope`.
- Confirm `SqlDelightDatabaseCleaner` still wipes every table (`SqlDelightDatabaseCleanerTest`).

### Success Criteria

#### Automated Verification:
- [x] `./scripts/compile.sh`
- [x] `./gradlew :composeApp:testProdDebugUnitTest --no-daemon`
- [x] `./gradlew :composeApp:assembleTstDebug --no-daemon`
- [x] `grep -r "SurveyScope\|survey-responses" composeApp/src` returns nothing in live code.
      One match remains, deliberately: `5.sqm:2` describes "the survey-responses feature" in a
      shipped migration's header comment. Shipped migrations are historical records and are not
      edited.

#### Manual Verification:
- [ ] Sign out wipes the queue; sign back in shows a clean state
- [ ] Full pass on a physical device across all three target kinds, online and offline

---

## Testing Strategy

### Manual Testing Steps
1. **CA1** — sign in as a relevador with no establishments; the survey list is populated.
2. **CA2** — tap one survey of each target kind; confirm subject picker, establishment picker, and
   direct entry respectively.
3. **CA3** — in the subject picker, search by DNI, then by surname; create a subject inline and
   confirm you land back in the form with it selected.
4. **CA4** — in the establishment picker, select an establishment you have no approved access to.
5. **CA5** — submit each kind; verify with a proxy that the body carries exactly one target id, and
   none at all for anonymous.
6. **CA6** — airplane mode, submit, confirm **Pendiente**; reconnect and confirm it syncs with the
   same client id.
7. **CA7** — submit the same survey for the same subject twice; the second shows the conflict copy;
   *Descartar* clears it; leave the app running and confirm no retry.
8. **CA8** — proxy-kill the connection after the request leaves but before the response returns;
   confirm the retry returns 200 and no duplicate appears in Actividades.
9. **CA9** — force a 500 and a DNS failure; confirm each produces legible copy and the row survives.
10. **Migration** — install 0.1.1, queue a response, upgrade to this build, confirm no crash and the
    row still lists.

### Test Coverage Additions
- `SurveyResponseSyncEngineTest` — target shapes, conflict classification, subject-canonical
  resolution, replay-id stability
- `SurveyMapperTest` — unknown `response_target` is dropped, not fatal
- `SurveyListViewModelTest` — routing per target
- `SelectEstablishmentViewModelTest` — single-select
- `SurveyResponseHistoryViewModelTest` — discard, conflict row
- `DefaultSurveyResponseLocalStoreTest` — the three target shapes, `markConflict`, `delete`

---

## Migration Notes

One migration, `6.sqm` (v6 → v7), doing four things:
1. `ALTER TABLE PendingSurveyResponseEdit ADD COLUMN surveyId`
2. Recreate `PendingSurveyResponse` with the target columns; existing rows become `'ESTABLISHMENT'`
3. Drop and recreate `CachedSurvey` (`scope` → `responseTarget`; stale `detailJson` discarded)
4. `DELETE FROM CachedActivityItem WHERE type = 'SURVEY_RESPONSE'` (the `SurveyResponseSummary`
   payload shape changed). Scoped by type — the table also holds `SUBJECT` and `MEASUREMENT` rows.

Per the project workflow: after editing the `.sq` files, run
`./gradlew :composeApp:generateCommonMainAppDatabaseSchema` and **commit both `6.sqm` and the
generated `databases/7.db`**. `verifyMigrations` is on, so a missing migration fails the build.
Never lower the schema version or delete an old `.sqm`/`.db` — a device ahead of the app crashes
with `Can't downgrade database`.

Blast radius is small: 0.1.1 is a tester-only build, so the number of devices carrying a populated
queue through this migration is low.

---

## Deployment Order

This is an incompatible contract change in **both** directions, so the two sides must ship together.

1. Backend `develop → main` release merged **and deployed**. It carries two independent breaks for
   mobile: this ticket's `response_target` contract, and the already-merged endpoint relocation
   (`a0074cc`).
2. **Mobile ships after, never before.** Every phase — including Phase 0 — depends on that release.
   Shipping Phase 0 early points the app at routes `main` does not yet serve; shipping nothing while
   the backend releases leaves the app calling routes `main` no longer serves. There is no safe
   window in between, so coordinate the two releases.
3. **Confirmed:** `tst` → backend `develop`, `prod` → backend `main`. So Phase 0 is *immediately*
   needed for tst (responses are broken there now and the rest of this plan cannot be tested until
   it lands) and *premature* for prod (which still serves the old path). Option 2 below is the
   applicable resolution: adopt the new path now, hold the prod release until the backend one ships.

### Why "urgent for tst, premature for prod" cannot be satisfied by both at once

The environment split is **only the base URL**. `ApiConfig.BASE_URL` is baked per flavor by
BuildKonfig, but every endpoint path is hardcoded in shared `commonMain` code — `SurveyResponseApi`
issues relative requests (`api/v1/…`). There is one path for both flavors, so adopting the new one
fixes `tst` and breaks `prod` in the same commit, for as long as `main` lags.

This is a **release-timing** constraint, not a development one: day-to-day work builds
`assembleTstDebug`, where the new path is simply correct. It only bites if a **prod release is cut in
the window** between mobile adopting the path and the backend `develop → main` release.

Options, in preference order:

1. **Land the backend `develop → main` release first**, or close behind. Removes the fork entirely and
   is the only option with no residue. Preferred where the release timing allows.
2. **Adopt the new path and do not cut a prod release until the backend one ships.** ← **applicable
   here.** `tst` is unblocked immediately, which it has to be for the rest of this plan to be
   testable at all; the only cost is a hold on prod releases, and everything from Phase 1 onward is
   already blocked on that same backend release.
3. **Branch on `ApiConfig.IS_PROD` inside `SurveyResponseApi`** so each flavor gets its matching path.
   `IS_PROD` already exists (`data/network/ApiConfig.kt`), so it is a handful of lines — but it puts
   an environment conditional into a data-layer client that has never had one, and this kind of
   temporary fork reliably outlives its reason. Only if testers are actively blocked *and* a prod
   release cannot wait; if used, remove it in the same PR that follows the backend release.

---

## References

- Ticket: Épica Encuesta Alimentaria — destino de las respuestas (sujeto / establecimiento / anónima)
- Backend routes (`develop`): `app/api/v1/surveys/responses.py:28,111,147,170,194`
- Backend routes (`main`, what the app targets today): `app/api/v1/survey_responses.py:16,23,58,81`
- The relocation commit: `a0074cc` (2026-07-28) — on `develop`, not on `main`
- Backend models: `app/models/survey.py`, `app/models/survey_response.py`
- Superseded by this ticket: `docs/adr/0003-anonymous-unbounded-survey-responses.md` (asserts no
  `Subject` relationship and no uniqueness — both reversed here)
- Still current: `docs/adr/0004-offline-first-response-sync-via-client-ids.md` (client-generated-id
  idempotency)
- `docs/mobile/survey-responses.md` — accurate for `main`, stale for `develop` (still documents
  `POST /api/v1/survey-responses`, and still describes the pre-uniqueness "no per-survey uniqueness"
  rule that this ticket reverses)
- Subject-canonical resolution precedent: `data/measurement/MeasurementRepositoryImpl.kt:53-73`
- Picker-result precedent: `presentation/feature/measurement/create_measurement/MeasurementNavigation.kt:20-28`
- 409 problem+json parsing precedent: `data/subject/SubjectApi.kt:31-36`
- Prior plans: `thoughts/plans/survey_responses_offline_first.md`, `thoughts/plans/survey_list_offline_first.md`
