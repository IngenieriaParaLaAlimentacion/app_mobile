---
date: 2026-07-13T14:34:55Z
researcher: Claude
git_commit: 925af56a363f1e94507044cf79934286a1e56122
branch: main
repository: app_mobile
topic: "Survey response uploads vs. activities feed / offline sync parity; adding a third tab to the activities screen"
tags: [research, codebase, survey-responses, activity-feed, history-hub, sync-engine, offline-first]
status: complete
last_updated: 2026-07-13
last_updated_by: Claude
---

# Research: Survey response uploads vs. activities feed / offline sync parity

## Research Question

1. After a survey response is uploaded, are we correctly updating "the activities" (the way a subject or measurement upload does)?
2. Are we using the correct offline sync strategy for survey responses, the same way we do for subjects/measurements?
3. Context: "we should have three tabs in the activities screen."

## Summary

**"The activities screen" is `HistoryHubScreen`** (`presentation/feature/history/HistoryHubScreen.kt`), reached from Home's bottom nav / "See all" and gated by `Route.History`. It currently renders exactly **two** tabs via `HistorySegment { Subjects, Measurements }` (`presentation/feature/history/HistorySegment.kt`), each backed by a merged local-queue ∪ server-history repository (`SubjectHistoryRepositoryImpl`, `MeasurementHistoryRepositoryImpl`).

**Survey responses do not appear here at all, by explicit prior design decision** (confirmed in memory and in `thoughts/plans/survey_responses_offline_first.md`): "Not extending the existing Home activity feed... to a third item type — the new 'My survey responses' screen is a separate, dedicated flow." That dedicated screen (`presentation/feature/survey/survey_response_history/SurveyResponseHistoryScreen.kt`, "Mis respuestas") already exists, is reachable only via `HomeAction.MySurveysClick`, and already shows per-row sync-state pills (Pendiente/Sincronizando/Sincronizado/Error) with a retry action — functionally equivalent to what `HistoryHubScreen`'s tabs show for subjects/measurements, just living in a separate screen instead of a third segment.

So, direct answers:

1. **No — a survey response upload does not touch Home's "Actividad reciente" or `HistoryHubScreen`.** This is intentional (see "Historical Context" below), not a bug: `ActivityItemType` is hardcoded to `{SUBJECT, MEASUREMENT}` and `HomeViewModel` only observes subject/measurement use cases. Survey responses have their own separate reactive path (`SurveyResponseHistoryRepositoryImpl` → `SurveyResponseHistoryViewModel`) that behaves the same way conceptually (local queue is the reactive source of truth, sync-state pills, retry action) but is invisible from Home's feed and from `HistoryHubScreen`.
2. **Mostly yes, with two real gaps** (see below) — the `SyncEngine`/`SyncCoordinator` wiring, backoff formula, retry cap, and DI registration for survey responses are byte-for-byte structurally identical to subjects/measurements, with justified, intentional divergences (no `MERGED` state, inverted prune policy, edit FK-gate). But: (a) there's **no persistent offline cache** for survey-response history beyond page 1 (subjects/measurements have `CachedActivityItem`), and (b) `SurveyResponseEditRepository.retry()` exists but is **never wired to any UI** — a `FAILED` edit is invisible to the user and silently garbage-collected after 7 days.
3. Given "the activities screen" = `HistoryHubScreen`, "three tabs" reads as: **add a third `HistorySegment.SurveyResponses` tab**, reusing the already-built `SurveyResponseHistoryRepositoryImpl`/`SurveyResponseHistoryViewModel` machinery, folded into the existing tab host instead of (or alongside) the standalone "Mis respuestas" screen. This would be a UI/navigation consolidation, not new sync engineering — the data layer for it already exists.

## Detailed Findings

### The "activities screen" (`HistoryHubScreen`) — current 2-tab structure

- `presentation/feature/history/HistoryHubScreen.kt` — `HistoryHubScreenRoot`/`HistoryHubScreen`, hosts an `AlSegmentedControl` tab switcher and delegates to `SubjectUploadsContentRoot` / `MeasurementUploadsContentRoot`. Local `rememberSaveable` selected-tab state, **not** a ViewModel — it doesn't follow the app's standard 6-file (`Action`/`Destination`/`Navigation`/`Screen`/`State`/`ViewModel`) pattern; it's a thin router over two existing sub-features.
- `presentation/feature/history/HistorySegment.kt` — `enum class HistorySegment { Subjects, Measurements }`, the two tabs.
- Registered at `Route.History` (`presentation/navigation/Route.kt:30`), wired in `app/AuthedShell.kt` (`composable<Route.History> { HistoryHubScreenRoot() }`) and reachable from Home's bottom nav and `HomeDestination.NavigateToHistory` (`presentation/feature/home/HomeNavigation.kt`).
- Each tab is backed by:
  - `presentation/feature/subject/uploads/SubjectUploadsViewModel.kt` (+ `SubjectUploadsState`/`Action`/`Screen`, `SubjectUploadUi`)
  - `presentation/feature/measurement/uploads/MeasurementUploadsViewModel.kt` (+ equivalents, `MeasurementUploadsContentRoot`)
- Both ViewModels use the same shape: a reactive local-merged `Flow` (via `ObserveSubjectUploadsUseCase`/`ObserveMeasurementsUseCase`) as the always-populated floor, plus manual load-more pagination (`GetSubjectHistoryPageUseCase`/`GetMeasurementHistoryPageUseCase`) against `/me/activity`. Retry/Delete actions are gated on `isLocal` (remote-only ledger rows are read-only).

### How Home's activity feed updates after a subject/measurement upload

This is a **pure reactive Flow pipeline rooted in SQLDelight** — there is no navigation-result signal, no `LifecycleResumeEffect`, and no manual `refresh()` call anywhere in this path:

1. `CreateSubjectViewModel.submit()` (`presentation/feature/subject/create_subject/CreateSubjectViewModel.kt:104`) → `CreateSubjectUseCase` → `SubjectRepositoryImpl.enqueue()` (`data/subject/SubjectRepositoryImpl.kt:29`) → `SubjectLocalStore.insert()` → SQLDelight `INSERT INTO PendingSubject` (`syncState = 'PENDING'`).
2. SQLDelight's `Query.Listener` fires on that table → `queries.selectAll().asFlow()` re-emits (`data/subject/local/DefaultSubjectLocalStore.kt:26`).
3. `SubjectHistoryRepositoryImpl.observeLocalMerged()`'s `combine()` re-runs — unions the queue `Flow` with a `CachedActivityItem` cache `Flow` (`data/activity/local/ActivityLocalStore.kt`, populated only by explicit `/me/activity` fetches), **local wins on id collision** (ADR-009).
4. `ObserveSubjectUploadsUseCase()` re-emits → `HomeViewModel`'s outer `combine(observeSubjectUploads(), observeMeasurements(), connectivity.isOnline) { … deriveState(...) }` re-runs (`presentation/feature/home/HomeViewModel.kt:56-76`).
5. `HomeState.recent` updates immediately with a `RecentActivityUi(badgeText = "Pendiente", …)` row — **pending/syncing/failed/merged rows are all shown**, each with a distinct badge (`HomeStateMapper.kt:105-119`: Pendiente/Sincronizando/Sincronizado/Ya existía/Error).
6. `syncCoordinator.requestSync()` is called fire-and-forget right after `enqueue()` (`CreateSubjectViewModel.kt:130`); each subsequent `markSyncing`/`markSynced`/`markFailed` write during `syncPending()` is itself another SQLDelight `UPDATE`, independently re-triggering the exact same Flow chain — this is why the badge visibly walks Pendiente → Sincronizando → Sincronizado/Error/Ya existía in real time, with zero explicit "notify Home" code anywhere.
7. Offline-then-later-synced case uses the identical mechanism, just triggered later (by `SyncCoordinator`'s connectivity-restored hook, `AuthedShell`'s foreground `LifecycleStartEffect`, or the 15-minute periodic backstop) — no code path distinguishes "queued offline" from "queued online but slow."

### Survey response submit flow — what actually happens

- **Create (`SurveyResponseFormViewModel`)** (`presentation/feature/survey/survey_response_form/SurveyResponseFormViewModel.kt:107-142`): on success, stays on the same screen, resets the form's answer fields, and sets `successMessage = "Respuesta guardada"` (rendered at `SurveyResponseFormScreen.kt:157`) — **this is the identical pattern** `CreateSubjectViewModel` uses (`successMessage = "Sujeto guardado"`, stays in place). `SurveyResponseFormDestination` only has `Back`, no `SavedStateHandle` result key — consistent with subjects, since Home doesn't need a result signal (it's reactively subscribed, not refresh-on-return).
  - Note: the prior session's note that this screen was "missing a success banner like other upload screens" does not match what this research found in code — the banner exists and follows the same convention as `CreateSubjectScreen`. This is worth a quick visual/manual re-check (positioning/styling could differ even if the state field and render call both exist) rather than treating it as confirmed-fixed or confirmed-still-broken from static analysis alone.
- **Edit (`SurveyResponseEditViewModel`)** (`.../survey_response_edit/SurveyResponseEditViewModel.kt:97-118`) *does* navigate back with a `SavedStateHandle` result (`SurveyResponseEditDestination.Saved` → `SURVEY_RESPONSE_EDITED_RESULT` → consumed by `SurveyResponseDetailViewModel.refresh()`), following the CLAUDE.md-documented "navigate-back-with-result" pattern — appropriate here since editing is a separate detail screen that needs a one-shot refresh signal, unlike Home's continuous subscription.
- **enqueue()** writes into `PendingSurveyResponse` exactly like `SubjectRepositoryImpl.enqueue()`/`MeasurementRepositoryImpl` write into their tables — same reactive-DB pattern. But **nothing observes `PendingSurveyResponse` from Home or `HistoryHubScreen`** — `ActivityItemType` (`data/activity/local/ActivityLocalStore.kt:5`) is hardcoded `{ SUBJECT, MEASUREMENT }`, and `HomeViewModel` is constructed only from `ObserveSubjectUploadsUseCase`/`ObserveMeasurementsUseCase`. This is a **deliberate design decision**, not an oversight — see Historical Context below.
- Survey responses instead have their own separate reactive path: `SurveyResponseHistoryRepositoryImpl` (`data/survey/SurveyResponseHistoryRepositoryImpl.kt`) unions the local `PendingSurveyResponse` queue with `/me/activity?type=survey_response` (via a **deliberately independent** `SurveyResponseActivityApi`, so it never touches `ActivityResponse`/`ActivityMapper`), feeding `SurveyResponseHistoryViewModel` → the dedicated "Mis respuestas" screen (`presentation/feature/survey/survey_response_history/SurveyResponseHistoryScreen.kt`), reachable only via `HomeAction.MySurveysClick` — a separate nav entry point from Home's activity feed / "See all".
- That screen already renders per-row sync-state pills (Pendiente/Sincronizando/Sincronizado/Error) and a "Reintentar" button gated on `FAILED`, functionally mirroring the Subject/Measurement Uploads tabs' pattern — it's just not inside `HistoryHubScreen`.

### Offline sync strategy — side-by-side (subjects / measurements / survey responses)

All four repository impls (`SubjectRepositoryImpl`, `MeasurementRepositoryImpl`, `SurveyResponseRepositoryImpl`, `SurveyResponseEditRepositoryImpl`) implement the shared `domain/sync/SyncEngine.kt` interface (`syncPending()`, `resetStuckSyncing()`, `pruneTerminal(olderThan)`) and are registered identically:

```kotlin
// di/DatabaseModule.kt
single<List<SyncEngine>> {
    listOf(
        get<SubjectRepository>(),
        get<MeasurementRepository>(),
        get<SurveyResponseRepository>(),
        get<SurveyResponseEditRepository>(),
    )
}
single { SyncCoordinator(engines = get(), connectivity = get(), scope = get()) }
```

`SyncCoordinator` (`data/subject/sync/SyncCoordinator.kt`) drives all four uniformly — single-flight mutex, connectivity-restored trigger, `AppViewModel.init { syncCoordinator.start() }` crash-recovery (`resetStuckSyncing()` for every engine), `AuthedShell`'s `SyncTriggers` (foreground + 15-minute backstop), and an hourly `pruneTerminal(olderThan = 7.days)` sweep across all engines. **The coordinator itself has zero feature-specific branching.**

Backoff formula and retry cap are shared, not duplicated:
```kotlin
fun XUpload.isEligibleAt(nowMs: Long): Boolean {
    if (attemptCount == 0) return true
    val backoff = minOf(30_000L, 1_000L shl attemptCount) + (createdAt % 1_000L)
    return nowMs >= updatedAt + backoff
}
```
`MAX_SYNC_ATTEMPTS = 5` (defined once in `domain/model/subject/SubjectUpload.kt:6`) is imported directly by both survey-response repos and `MeasurementRepositoryImpl` — same constant, not a re-derived copy.

**Justified divergences** (each has an explicit rationale, either in code comments or grilling ADRs):

| Aspect | Subjects | Measurements | Survey Response (create) | Survey Response (edit) |
|---|---|---|---|---|
| `MERGED` state | Yes (DNI 409 conflict) | No | No (no server uniqueness constraint) | No |
| FK-gate before POST | — | Gates on subject's `SyncState` | — | Gates on parent response's `SyncState`; **treats missing local parent as "proceed" (assume already synced)**, not "resolve id" — handles cross-device edits, which measurements never need |
| `pruneTerminalOlderThan` | `SYNCED` pruned after 7d; `FAILED` kept forever; `MERGED` permanently exempt | `SYNCED` pruned after 7d | **Inverted: `FAILED` pruned after 7d, `SYNCED` exempt forever** — kept as the offline edit-prefill source | Both `SYNCED` and `FAILED` pruned after 7d (no downstream reader needs the edit row to survive; the parent response row is the prefill source) |
| Persistent offline-history cache (page > 1) | `CachedActivityItem` table | `CachedActivityItem` table (shared) | **None** — only the local queue serves as an offline fallback, page 1 only | N/A (no ledger UI) |

**Real gaps found** (not by design, worth fixing):

1. **No persistent cache for survey-response history beyond page 1.** `SurveyResponseHistoryRepositoryImpl.getPage()` mirrors `SubjectQueryRepositoryImpl`'s page-1-only union rule but, unlike subjects'/measurements' history repos, has no `ActivityLocalStore`-style cache table — so offline pagination past page 1 simply returns nothing, whereas subjects/measurements degrade gracefully via their cached rolling window.
2. **`SurveyResponseEditRepository.retry(editId)` is implemented but never called from any use case or UI.** No `RetrySurveyResponseEditUploadUseCase` exists; `SurveyResponseHistoryScreen`/`SurveyResponseDetailScreen`/`SurveyResponseDetailState` have no edit-sync-state field or retry affordance. A `FAILED` edit attempt is therefore invisible to the user and silently deleted by the hourly `pruneTerminal(7.days)` sweep — the only queue in the app where a dead-lettered row has no visible recovery path.

### Design system note

No `TabRow`/`Pager` exists anywhere in this codebase — `HistoryHubScreen`'s existing two-tab UI uses `core/design_system/components/segmented/AlSegmentedControl.kt`. A third tab would extend `HistorySegment` and reuse this same component; it is designed to be a simple 2-value/N-value segmented switch, not hardcoded to exactly two options at the type level (confirm the component's API before assuming N-way support — not verified in this pass).

## Code References

- `presentation/feature/history/HistoryHubScreen.kt` — the "activities screen" tab host
- `presentation/feature/history/HistorySegment.kt` — `enum class HistorySegment { Subjects, Measurements }`
- `presentation/feature/home/HomeViewModel.kt:56-76` — reactive `combine()` driving Home's recent-activity list
- `presentation/feature/home/HomeStateMapper.kt:105-119` — sync-state → badge mapping (Pendiente/Sincronizando/Sincronizado/Ya existía/Error)
- `data/activity/local/ActivityLocalStore.kt:5` — `ActivityItemType { SUBJECT, MEASUREMENT }`, hardcoded to two types
- `data/subject/SubjectHistoryRepositoryImpl.kt:32-70` — local-merged Flow + local-wins dedup (ADR-009)
- `data/measurement/MeasurementHistoryRepositoryImpl.kt` — mirrors subjects' history repo, shares `CachedActivityItem`
- `data/survey/SurveyResponseHistoryRepositoryImpl.kt:17-51,53-89` — survey response's separate history repo, no persistent cache, live-fetch-preferred `getDetail`
- `data/survey/SurveyResponseActivityApi.kt:10-27` — deliberately independent `/me/activity?type=survey_response` client
- `presentation/feature/survey/survey_response_form/SurveyResponseFormViewModel.kt:107-142` — create-submit success handling (stay in place, `successMessage`)
- `presentation/feature/survey/survey_response_edit/SurveyResponseEditViewModel.kt:97-118` + `SurveyResponseEditNavigation.kt:29-33` — edit-submit navigate-back-with-result
- `presentation/feature/survey/survey_response_history/SurveyResponseHistoryScreen.kt:76,136-162` — "Mis respuestas" screen, sync-state pills, retry button
- `presentation/feature/home/HomeViewModel.kt:99-115` — `SeeAllActivityClick`→`NavigateToHistory` vs. `RespondSurveyClick`/`MySurveysClick`→survey routes (separate nav trees)
- `di/DatabaseModule.kt:27-35` — `List<SyncEngine>` registration, all four engines
- `data/subject/sync/SyncCoordinator.kt` — shared coordinator, feature-agnostic
- `composeApp/src/commonMain/sqldelight/.../PendingSurveyResponse.sq` — inverted prune policy (`FAILED` pruned, `SYNCED` exempt)
- `composeApp/src/commonMain/sqldelight/.../PendingSurveyResponseEdit.sq` — both terminal states pruned
- `domain/repository/SurveyResponseEditRepository.kt:18` + `data/survey/SurveyResponseEditRepositoryImpl.kt:131` — unwired `retry()`
- `core/design_system/components/segmented/AlSegmentedControl.kt` — the tab component `HistoryHubScreen` uses

## Architecture Insights

- **Home's feed is a live subscription, not a refresh-on-event screen.** Unlike the CLAUDE.md-documented "navigate-back-with-result" pattern (used for one-shot refreshes like `EditProfile`), Home never needs it — it's permanently combining Flows over local SQLDelight tables, so any local DB write anywhere in the sync pipeline (enqueue, markSyncing, markSynced, markFailed) is automatically visible with zero explicit signaling code. This is a strong, consistent pattern; extending it to a third entity type is a matter of widening the `combine()`/`ActivityItemType` enum, not inventing new plumbing.
- **The `SyncEngine`/`SyncCoordinator` abstraction has proven itself feature-agnostic across 4 engines now** — DI registration and coordinator logic required zero changes to onboard two new engines (create + edit) for survey responses. This validates the "reuse the outbox pattern" decision from `[[extra-outbox-reference]]`.
- **The "activity feed" (`data/activity/*` / `HistoryHubScreen`) and "survey response history" (`data/survey/*` / `SurveyResponseHistoryScreen`) are two structurally-parallel but code-independent stacks**, by explicit prior decision (both memory and the plan doc confirm this was deliberate, to avoid touching the hardcoded two-type activity pipeline). Given the user's new ask for three tabs, the cleanest path is likely: fold `SurveyResponseHistoryRepositoryImpl`'s data into a new `HistorySegment.SurveyResponses` tab inside `HistoryHubScreen` (reusing its already-built union/pagination logic), rather than widening `ActivityItemType`/`ActivityMapper`'s hardcoded two-type pipeline — that would touch fewer layers and match survey responses' existing separate-DTO philosophy.
- **Inconsistent prune direction is intentional but easy to misread as a bug**: `PendingSubject`/`PendingMeasurement` age out `SYNCED` rows after 7 days; `PendingSurveyResponse` ages out `FAILED` rows instead and keeps `SYNCED` forever. Both are correct for their use case (subjects/measurements rely on the server-backed `CachedActivityItem` cache to survive pruning; survey responses rely on the never-pruned `SYNCED` row itself as the edit-prefill source, since there's no cache to fall back to).

## Historical Context (from thoughts/)

- `thoughts/plans/survey_responses_offline_first.md` — the original 7-phase plan (2026-07-11). Explicitly states, under "What We're NOT Doing": *"Not extending the existing Home activity feed (`ActivityItemResponse`/`ActivityMapper`/`HomeState`) to a third item type — the new 'My survey responses' screen is a separate, dedicated flow (per user decision)."* This is the direct answer to "why doesn't a survey upload touch the activities screen" — it was a conscious scope decision, not an oversight, made when survey responses were greenfield.
- `thoughts/plans/activity_feed_history.md` — the plan that built `HistoryHubScreen`'s current two-tab (`Sujetos`/`Mediciones`) structure and the `CachedActivityItem` cache, predating the survey-responses work. All 6 phases are checked complete.
- `thoughts/grilling/activity-feed-synthesis.md` — ADR-005 through ADR-011, the design session behind the current Home/History merge behavior (local-wins dedup, capped cache, Home also merges).
- `thoughts/grilling/adr-007-merged-rows-exempt-from-prune.md` — precedent for permanently exempting certain terminal rows from pruning, the same shape `PendingSurveyResponse`'s exemption of `SYNCED` rows follows (though for a different underlying reason: edit-prefill vs. no-remote-fallback).
- Memory `survey-responses-design-decisions` (2026-07-11) — corroborates all of the above and additionally notes: *"'View responses' is a dedicated new screen, NOT an extension of Home's activity feed — that feed... is hardcoded to exactly 2 item types across 5+ layers with no polymorphism; adding a 3rd type would touch all of them."* This "5+ layers" claim is confirmed here concretely: DTO (`ActivityItemResponse`), mapper (`ActivityMapper`/`ParsedActivityItem`), local-store enum (`ActivityItemType`), `HistorySegment`, and `HomeViewModel`'s constructor dependencies would all need to change to add a type directly to that pipeline — reinforcing that a separate `HistorySegment.SurveyResponses` tab reusing the survey-specific stack is the lower-risk path to "three tabs."
- Previous session note (2026-07-13, session summary): *"In the screen for responding surveys after a survey is responded we don't have a success banner above like other upload screens."* This research found `successMessage` IS set and rendered in `SurveyResponseFormScreen.kt:157`, following the same convention as `CreateSubjectScreen`. Worth a manual re-check in the running app (positioning/visibility could still differ even though the code path exists) rather than assuming this is resolved or still broken from static analysis alone.

## Related Research

None found specifically on this cross-feature comparison; this document is the first to directly compare survey-response sync/activity-feed parity against subjects/measurements.

## Open Questions

1. **Does `AlSegmentedControl` support more than 2 segments cleanly (spacing/visual design), or was it only ever built/tested for a 2-way switch?** Not verified in this pass — check before assuming a drop-in third tab.
2. **Should the third tab in `HistoryHubScreen` reuse `SurveyResponseHistoryRepositoryImpl` as-is, or should the standalone "Mis respuestas" screen be removed/redirected once folded in as a tab?** This is a product decision (duplicate entry points vs. single source), not purely technical.
3. **Should the missing persistent offline cache for survey-response history (gap #1 above) be fixed as a prerequisite, given a third tab would make that gap more visible (users toggling between tabs while offline)?**
4. **Should `SurveyResponseEditRepository.retry()` get wired to a UI (gap #2), especially if edits become visible in a more prominent "activities" tab where users would expect parity with subjects/measurements' retry behavior?**
5. **Confirm in a running build whether the create-flow success banner (`SurveyResponseFormScreen.kt:157`) visually matches other upload screens** — the prior session note and this session's static-analysis finding disagree, and only one of them was checked against the actual rendered UI.
