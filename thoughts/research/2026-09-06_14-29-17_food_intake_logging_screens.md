---
date: 2026-09-06T14:29:17-03:00
researcher: duzacj@gmail.com
git_commit: b1bb51e21e55b8b62e810a27bf2ff8fa51d720ec
branch: feature/food-intake-logging
repository: copenhagen (app_mobile — Ingeniería en Alimentación CMP)
topic: "[Móvil] Cargar un ingreso de alimentos — select_food + create_food_intake: which code parts already exist"
tags: [research, codebase, food-intake, food-catalog, select_food, create_food_intake, pagination, savedstatehandle, problem-json, validation, koin, navigation]
status: complete
last_updated: 2026-09-06
last_updated_by: duzacj@gmail.com
---

# Research: food intake logging (`select_food` + `create_food_intake`) — existing code map

**Date**: 2026-09-06 14:29:17 -03:00
**Researcher**: duzacj@gmail.com
**Git Commit**: `b1bb51e21e55b8b62e810a27bf2ff8fa51d720ec`
**Branch**: `feature/food-intake-logging`
**Repository**: copenhagen

## Research Question

For the user story `.context/attachments/MRVZce/pasted_text_2026-09-06_14-28-53.txt` ("[Móvil] Cargar un ingreso de alimentos" — two screens: a food-catalog picker and an intake form, online-only, `POST /food-intakes` with a client UUID idempotency key): **locate every existing code part, file and line the feature would build on**. Research only — no implementation proposals.

---

## Summary

Almost every mechanism the story asks for already exists in this repo, and in most cases in **two** variants where only one is the right precedent. The findings that actually change the shape of the work:

1. **The story's two named precedents are correct but split.** `request_establishment` is the right model for *debounce + pagination + online submit*; `select_subject` is the right model for *returning a pick via `savedStateHandle`*. Neither one alone covers both.
2. **`savedStateHandle` picker results are JSON strings of the whole `@Serializable` domain object**, not ids — and the key constant lives in the picker's own `*Navigation.kt`, not in the shared `NavResults.kt` (which is Boolean-only). `presentation/navigation/NavResults.kt:14-17` + `SelectSubjectNavigation.kt:11`.
3. **problem+json `type` branching already has exactly one precedent**: `SurveyResponseApi.create` (`data/survey/SurveyResponseApi.kt:33-53`). The other one (`SubjectApi`) discriminates on an extension field, not `type`. Everything else in the repo branches on HTTP status only.
4. **`ProfileRepositoryImpl`'s status→error mapping is dead code in production** because `ProfileApi` sits on the authed client, which does not set `expectSuccess` (`HttpClientFactory.kt:38` vs `:57`). Any new food repository must use the `SubjectApi`/`SurveyResponseApi` style (`response.status.value` branching), not the `ClientRequestException` style.
5. **Three things the story needs do not exist yet**: a horizontally scrolling filter-chip row (zero `LazyRow`/`horizontalScroll`/`FilterChip` in the repo), a `suffix`/unit slot on `AlInput` (only `trailingIcon`), and a food/meal MingCute icon (95 icons available, none food-related).
6. **The pre-fill-not-gate establishment pattern is already implemented twice** with an identical fallback chain `current ?: approved.singleOrNull() ?: existing` — `MeasurementViewModel.kt:122` and `SurveyResponseFormViewModel.kt:116`.
7. **The idempotency-UUID pattern is already the house style**: `IdGenerator`/`UuidGenerator` (`domain/util/IdGenerator.kt`) → `localId` → request body `id`, in all four sync features. But every existing user of it goes through SQLDelight; this feature explicitly does not, so the id must be held in ViewModel state instead.
8. **Backend blocker confirmed by the thoughts docs**: `POST /food-intakes` only exists in the unmerged, stale backend PR #126, which hangs off the pre-rebase version of #123. Both GET endpoints are merged (#119).

---

## Detailed Findings

### 1. Precedent A — `request_establishment` (debounce + pagination + online submit)

Directory: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/profile/request_establishment/`
Seven files: the canonical six plus a non-canonical `EstablishmentRequestErrorMessage.kt` (error→Spanish mapper). Test at `commonTest/.../request_establishment/RequestEstablishmentViewModelTest.kt`.

**Debounce** — a *second* `MutableStateFlow<String>` separate from UI state, so the text field updates instantly while the network pipeline is debounced:
- `RequestEstablishmentViewModel.kt:19` — `@OptIn(FlowPreview::class)` (required by `debounce`)
- `:28` — `private val query = MutableStateFlow("")`
- `:30-36` — `init { query.debounce(DEBOUNCE_MS).distinctUntilChanged().collectLatest { reload(it) } }`
- `:41-44` — `QueryChanged` writes both `_state.query` (instant) and `query.value` (debounced)
- `:149` — `DEBOUNCE_MS = 300L`, `:150` — `PAGE_SIZE = 20`
- `:96` — `if (throwable is CancellationException) throw throwable` (so `collectLatest` cancellation isn't swallowed into `loadError`)

**Pagination**
- State: `RequestEstablishmentState.kt:11-20` — `page`, `totalPages`, `isLoading`, `isLoadingMore`, `loadError`, and `canLoadMore = page < totalPages && !isLoadingMore && !isLoading`
- Trigger: `RequestEstablishmentScreen.kt:69-78` — `rememberLazyListState()` + `LaunchedEffect(listState, results.size, canLoadMore)` + `snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }`; `LOAD_MORE_THRESHOLD = 3` at `:38`
- Handler: `RequestEstablishmentViewModel.kt:102-125` — appends (`results + paged.items`), no dedup; failure only clears `isLoadingMore` and keeps the list
- `reload()` (page 1, replaces): `:81-100`
- Known wrinkle: `loadMore` reads `current.query` (undebounced) while `reload` gets the debounced value — a page-2 fetch mid-typing can use a newer search term than the page-1 rows already listed.

**Four render states** — plain booleans, not a sealed UI state; single `when` in a weighted `Box`: `RequestEstablishmentScreen.kt:114-194` (loading spinner → load error + retry → empty "Sin resultados" → LazyColumn + tail spinner item at `:167-178`).

**Online submit** — `RequestEstablishmentViewModel.kt:127-146`: `canSubmit` guard, `isSubmitting` deliberately left `true` on success (`:134-135` comment) so the button doesn't flash before the pop; failure unwraps a typed `EstablishmentRequestFailure`. Button at `Screen.kt:197-222` with `AlButton(enabled = canSubmit, loading = isSubmitting, fullWidth = true)`. Note the form is **not** fully locked while submitting — only `AlBackTopAppBar(enabled = !state.isSubmitting)` at `:89`. The story asks for a full lock.

**Data chain**
- `data/profile/ProfileApi.kt:34-44` — Ktor query params: `parameter("available", true)`, `parameter("page", page)`, `parameter("page_size", pageSize)`, and `search?.takeIf { it.isNotBlank() }?.let { parameter("search", it) }` (omitted entirely when blank)
- `data/profile/dto/AvailableEstablishmentResponse.kt:18-25` — `PaginatedAvailableResponse(items, total, page, @SerialName("page_size") pageSize, @SerialName("total_pages") totalPages)` — **exactly the envelope shape `GET /food-catalog/foods` returns**
- `data/profile/mapper/ProfileMapper.kt:45-50` → `domain/model/profile/PagedEstablishments.kt:3-8` (drops `pageSize`)
- `domain/usecase/profile/GetAvailableEstablishmentsUseCase.kt:7-11` — pure pass-through
- `data/profile/ProfileRepositoryImpl.kt:76-90` — `runCatching { }.recoverCatching { throw XFailure(map(it), it) }`

### 2. Precedent B — picker returning a result via `savedStateHandle`

Directory: `presentation/feature/subject/select_subject/`

- **Key constant lives in the picker's own navigation file**: `SelectSubjectNavigation.kt:10-11` — `const val SELECTED_SUBJECT_RESULT = "selected_subject_result"`
- **Write target is always `previousBackStackEntry`**: `SelectSubjectNavigation.kt:30-34`
- **Payload type is a JSON String of the full object**, not an id: `SelectSubjectDestination.kt:5` — `data class Confirm(val subjectJson: String)`; built at `SelectSubjectViewModel.kt:44-51` via `Json.encodeToString(action.subject)` over `domain/model/subject/SubjectSummary.kt:5-12` (`@Serializable`). The domain model must be `@Serializable` for this to work.
- **Consumption happens in the caller's `composable` lambda, not the Screen**: `MeasurementNavigation.kt:18-32` — `entry.savedStateHandle.getStateFlow<String?>(KEY, null).collectAsStateWithLifecycle()`, decode with `runCatching { Json.decodeFromString<SubjectSummary>(it) }.getOrNull()`, pass `pickedSubjectResult` + `onResultConsumed` into the Root
- **Consume-once is `entry.savedStateHandle[KEY] = null`** (set-to-null), *not* `remove()`: `MeasurementNavigation.kt:28`
- **Root-side effect**: `MeasurementScreen.kt:60-65` — `LaunchedEffect(pickedSubjectResult) { it?.let { viewModel.setSubjectFromResult(it); onResultConsumed() } }`

**The other picker, `select_establishment`, differs in four ways** worth knowing but *not* copying for `select_food`: route takes args (`preselectedJson`, `singleSelect` — `Route.kt:44-52`), payload is always a JSON **array**, VM built with `parametersOf(route)` (`ViewModelsModule.kt:63-65`), and it has **no pagination** (single `page_size=100` fetch, `SelectEstablishmentViewModel.kt:98-109`).

**The parallel, unrelated result family** — `presentation/navigation/NavResults.kt`:
- `:14-17` — four `Boolean` "refresh me" keys (`PROFILE_UPDATED_RESULT`, `ESTABLISHMENT_REQUESTED_RESULT`, `ESTABLISHMENT_ABSENCE_NOTIFIED_RESULT`, `SURVEY_RESPONSE_EDITED_RESULT`)
- `:26-41` — `RefreshOnNavResult(savedStateHandle, key, onResult)`, hardcoded to `getStateFlow<Boolean?>` and `handle.remove<Boolean?>(key)`. **Picker payloads cannot use it.**

**Complete `savedStateHandle` inventory** (7 writers, all `previousBackStackEntry`; 7 readers): writers at `SelectSubjectNavigation.kt:31-32`, `CreateSubjectNavigation.kt:38-41` (writes *between* two pops), `SelectEstablishmentNavigation.kt:32-33`, `EditProfileNavigation.kt:36`, `RequestEstablishmentNavigation.kt:31`, `NotifyEstablishmentNavigation.kt:29-30`, `SurveyResponseEditNavigation.kt:31`. Readers at `MeasurementNavigation.kt:20-28`, `SurveyListNavigation.kt:26-42`, `RegisterNavigation.kt:18-23`, `ProfileScreen.kt:56-61` (the one outer-nav read), `MyEstablishmentsNavigation.kt:15`, `RequestEstablishmentNavigation.kt:16`, `SurveyResponseDetailNavigation.kt:17`.

**Outer/inner nav relevance**: both `create_food_intake` and `select_food` would live on the **outer** nav (like `CreateMeasurement` ← `SelectSubject`), so the simple `previousBackStackEntry` path applies with no outer↔inner crossing. The `ProfileScreen.kt:53-55` comment documents the case where it does cross.

### 3. Networking — clients, envelopes, `problem+json`

- `data/network/ApiConfig.kt:13-21` — `BASE_URL = AlEnv.API_URL` only. **There is no `/api/v1` constant**; every call site writes the full relative path (`client.get("api/v1/...")`). Trailing slash on the prod default (`build.gradle.kts:70-75`) is what makes relative paths resolve.
- `data/network/HttpClientFactory.kt:26-30` — shared `Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }`. `explicitNulls` is left at `true` deliberately (see the `@EncodeDefault(NEVER)` note in `data/survey/dto/SurveyResponseDto.kt:13-16`).
- **Two clients**: `createRefreshClient` (`:33-47`, `expectSuccess = true`) and `createAuthedClient` (`:50-95`, **no `expectSuccess`**, Bearer + `refreshTokens`, `sendWithoutRequest { true }`). **No `HttpTimeout` plugin anywhere** — engine defaults only.
- `di/ClientsModule.kt:23-57` — `named("refresh")` for `AuthApi`/`EstablishmentApi`/`RegistrationApi`; default (authed) for `SubjectApi`, `MeasurementApi`, `ProfileApi`, `ActivityApi`, `SurveyApi`, `SurveyResponseApi`, `SurveyResponseActivityApi`.

**The `type`-discriminated problem+json precedent — the only one:**
- `data/survey/dto/SurveyResponseConflictProblem.kt:17-33` — `type: String? = null` + extension fields as **top-level siblings** of `type`/`title`/`status`, plus `const val SURVEY_RESPONSE_CONFLICT_TYPE = "/errors/survey-response-already-exists"`. Its KDoc at `:10-13` explains exactly why `type` is not optional to check when two endpoints share a status code — the same reason the food story has two different 404s.
- `data/survey/SurveyResponseApi.kt:27-57` — the full `when (response.status.value)` block including `409 -> { val problem = runCatching { response.body<SurveyResponseConflictProblem>() }.getOrNull(); if (problem?.type == …) … else … }`.
- Ktor's `ContentNegotiation` deserializes `application/problem+json` fine (the `+json` suffix matches) — proven by tests that respond with that content type: `commonTest/.../SubjectUploadOutcomeTest.kt:82,91`, `RegistrationRepositoryImplTest.kt:168`.
- The **weaker** precedent: `data/subject/dto/SubjectConflictProblem.kt:6-13` has no `type` at all, discriminating on the presence of `canonical_subject`.

**Api-layer return convention**: authed-client Apis return per-endpoint `*Outcome` sealed interfaces, never `Result` — `data/subject/SubjectUploadOutcome.kt:6-13`, `SurveyApi.kt:42-51`, `SurveyResponseApi.kt:96-128`. Transport failures collapse into `Retryable` via `runCatching { }.getOrElse { if (t is CancellationException) throw t; Retryable(t) }` (`SubjectApi.kt:44-47`).

**Repository return convention**: stdlib `kotlin.Result<T>` everywhere (28 signatures across 14 interfaces in `domain/repository/`); no custom `Either`/`Resource`. Three sub-idioms — `recoverCatching { throw XFailure(...) }` for refresh-client repos, bare `runCatching` for local writes, and explicit `Result.success/failure` after folding an `*Outcome` (`SurveyRepositoryImpl.kt:44-57`).

**Domain error sealed interfaces** to model after: `domain/model/survey/SurveyFeatureError.kt:5-13` (`Forbidden`/`NotFound`/`Network`/`Unknown` + a paired `SurveyFeatureFailure : Exception`), `domain/model/profile/EstablishmentRequestError.kt:3-9` (has an explicit `EstablishmentNotFound`).

**⚠️ Trap confirmed by two independent agents.** `ProfileApi` is on the authed client (`ClientsModule.kt:52`) which has no `expectSuccess`, so Ktor never throws `ClientRequestException` there. Every `is ClientRequestException ->` branch in `ProfileRepositoryImpl.mapProfileError` (`:107`), `mapRequestError` (`:118`) and `mapAbsenceError` (`:129`) is unreachable in production — all HTTP errors fall through to `Unknown(t)` (a `JsonConvertException` from trying to parse the error body into the success DTO). The 403 → `emptyList()` fallback at `:69-71` is dead too. `ProfileRepositoryImplTest.kt:38-45` masks it by building a mock client with `expectSuccess = true`. This is exactly the bug `SubjectApi`'s KDoc (`SubjectApi.kt:17-22`) says its status-branching style exists to avoid.

**Connectivity**: `data/network/connectivity/ConnectivityObserver.kt:10-16` — `val isOnline: Flow<Boolean>` + `suspend fun isOnline()`. A plain interface with Koin-bound platform classes (`Modules.android.kt:20`, `Modules.ios.kt:19`), **not** expect/actual. Consumers: `SyncCoordinator.kt:22,33`, `HomeViewModel.kt:36,61` (4th arm of a `combine`), `AuthedShell.kt:128,139,143,147` (one-shot gates). Test fake at `commonTest/.../fake/FakeConnectivityObserver.kt:7`. iOS caveat at `IosConnectivityObserver.kt:32`: `isOnline()` is `isOnline.first()` and **suspends until the monitor first emits** — there is no synchronous seed like Android's.

### 4. Navigation wiring

- `presentation/navigation/Route.kt` — `@Serializable sealed class Route` (`:6-7`), 24 routes, each with an explicit `@SerialName`. 18 `data object`, 6 `data class`. Multi-value payloads travel as **pre-serialized JSON strings** (`preselectedJson: String`, `:50`), never typed lists.
- `app/AppNavHost.kt:27-65` — the outer `NavHost`, wrapped in `CompositionLocalProvider(LocalOuterNavController provides navController)` at `:32`. Every feature registers via `fun NavGraphBuilder.xScreen(navController)`; the only inline `composable<>` is `Route.AuthedShell` at `:54`.
- `app/AuthedShell.kt:39-119` — inner `NavHost` with 3 tabs (Home/History/Profile), `outerNav = LocalOuterNavController.current` at `:43`. Home's destinations are dispatched **inline at `:88-105`**; `HomeNavigation.kt:3-7` explicitly documents that its own file is dead code for the running app.
- `app/LocalOuterNavController.kt:6-7` — `staticCompositionLocalOf<NavHostController>`. One provider, three consumers (`AuthedShell.kt:43`, `SurveyResponseHistoryContentRoot.kt:18`, `ProfileScreen.kt:57`).

**`*Navigation.kt` idiom** (three parts, in order):
1. `fun NavController.navigateToFoo(...)` — typed push helper
2. `fun NavGraphBuilder.fooScreen(navController: NavController)` — wraps `composable<Route.Foo> { … }`; this is what `AppNavHost` calls
3. `internal fun navigate(destination: FooDestination, navController: NavController)` — **overloaded per-package as plain `navigate`, not `handleFooDestination`**
Two full examples: `SelectSubjectNavigation.kt` (whole file) and `EditProfileNavigation.kt` (whole file).

**Destination-consumption pattern** (`SurveyResponseFormScreen.kt:41-57`):
```kotlin
LaunchedEffect(state.destination) {
    state.destination?.let { navigate(it); viewModel.clearDestination() }
}
```
Destination lives in state (`SurveyResponseFormState.kt:9`), cleared by `clearDestination()` (`SurveyResponseFormViewModel.kt:66-68`). Route args reach the VM via `entry.toRoute<Route.X>()` → `koinViewModel { parametersOf(route) }`.

### 5. Validation, decimals, ids, keyboard

- `core/validation/TextFieldState.kt` (whole file, 61 lines) — `Validator` typealias is declared **here** at `:4`, not in `Rules.kt`. `displayError` logic at `:35-41`; `onChange`/`onFocus`/`showError` at `:46-61`. Behavior pinned by `commonTest/.../TextFieldStateTest.kt:16-73`.
- `core/validation/Rules.kt` — **six** builders: `required`, `maxLength`, `minLength`, `matches`, `digitsInRange`, and **`decimalInRange(min, max, message)`** (`:39-43`, exclusive-low / inclusive-high, accepts `,` or `.`), plus `of(vararg)`. `minLength` has no call site. `decimalInRange` semantics pinned by `commonTest/.../RulesDecimalInRangeTest.kt:15-56`.
- `domain/util/DecimalInput.kt` (whole file, 14 lines) — `sanitizeDecimalInput(raw)`: comma→dot, strips non-digit/non-dot, keeps only the **first** dot (`"1.2.3"` → `"1.23"`). **No decimal-place cap and no max-digit cap** — the story's "max 3 decimals / cap 9 999 999,999" is not covered by it. Two call sites: `MeasurementViewModel.kt:51,62` (in the reducer) and `SurveyQuestionForm.kt:79-84` (in the Screen lambda). No unit test exists for it.
- `domain/util/IdGenerator.kt` (whole file, 14 lines) — `interface IdGenerator { fun newId(): String }` + `class UuidGenerator` using stdlib `kotlin.uuid.Uuid.random()` (v4, `@OptIn(ExperimentalUuidApi::class)`). **Not** expect/actual. DI at `RepositoriesModule.kt:75`.
  **Idempotency-key chain, four instances**: generated id → SQLDelight `localId` → request body `id`. `MeasurementRepositoryImpl.kt:35-41` → `MeasurementNetworkMapper.kt:8-16` (`id = localId`) → `data/measurement/dto/MeasurementRequest.kt:6-18` (KDoc: *"[id] is the client-generated UUID (idempotency key)"*). Same in `SubjectUploadMapper.kt:62-63`, `SurveyResponseUploadMapper.kt:54-63`, `SurveyResponseEditUploadMapper.kt:27-31`. **Every existing instance parks the id in the DB; this feature has no DB, so state is the only place to hold it.**
- **Numeric field reference** — `create_measurement`: validators at `MeasurementViewModel.kt:197-205`; reducers at `:48-68`; force-show sweep + `buildMeasurement` at `:138-195` (`toDoubleOrNull()` with **no** comma replacement, safe only because sanitize ran at typing time); inputs at `MeasurementScreen.kt:189-213` with `KeyboardOptions(keyboardType = KeyboardType.Decimal)`. Note `canSubmit` (`MeasurementState.kt:23-26`) gates on **non-blankness**, not validity.
- **`KeyboardType` census**: `Decimal` at `MeasurementScreen.kt:199,212` and `SurveyQuestionForm.kt:92`; `Number` at `SurveyQuestionForm.kt:128`, `CreateSubjectScreen.kt:107,151`. `Number` is the "no decimal separator" variant the story's `unidades` case would want.
- **Keyboard scroll pattern is implemented once, inside `AlInput`** — `core/design_system/components/input/AlInput.kt:58-88` (`onGloballyPositioned { boundsInRoot().top.toInt() + scrollState.value }`, `LaunchedEffect(isFocused, isImeVisible) { animateScrollTo(...) }`, plus the `hasFocusedOnce` guard so the initial unfocused state is never reported). Screens supply two values: `val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0` (identical line in 10 screens) and a `rememberScrollState()` on a `.verticalScroll(...)` column under an `.imePadding()` parent — canonical wiring at `MeasurementScreen.kt:76-86, 113-118, 161-167`. **Zero `BringIntoViewRequester` in the repo.**
- **`AlPasswordInput.kt:33-43` does not expose `onFocusChange`** — password fields can't do blur-aware validation. Irrelevant here, noted for completeness.

### 6. Design system inventory

| Need from the story | What exists | Gap |
|---|---|---|
| Screen shell | `AlScaffold.kt:12-14` (Box + `surfaceBase` + `safeDrawingPadding`, content-only — no topBar slot) | — |
| Back bar | `AlBackTopAppBar.kt:30-36` (`onBackClick`, `title`, `enabled`, `trailing`); `AlAppBarHeight = 56.dp` at `:22` | — |
| Search field | `AlInput.kt:41-56` — used as the search box in all three pickers, with `scrollState`/`isImeVisible` deliberately omitted (KDoc `:31-33`) | no leading search icon param |
| Result card | `AlListRow.kt:45-58` (subject picker) or `AlSelectableCard.kt:34-40` (establishment picker, has a checkbox) or `AlInfoCard.kt:41-48` | — |
| Category chips | `AlChoiceGroup.kt:30-38` — the only selectable chip, but it's a **`FlowRow`** (wraps, `:45`) and requires a `label`; `ChoiceChip` at `:71-77` is **private**. `AlSegmentedControl.kt:27-31` is equal-weight and non-scrolling. | **No `LazyRow`/`horizontalScroll`/`FilterChip` anywhere in the repo** (grep: zero matches) |
| Status pill | `AlPill.kt:26-29` — **has no `selected`/`onClick`**, purely decorative; ditto `AlStatusBadge.kt:20` | not usable as a filter chip |
| Picker trigger field | `AlSelectField.kt:28-37` — read-only KomoUI `Input`, taps via `PressInteraction.Release` (`:42-48`), default `"›"` trailing | — |
| Unit suffix on quantity | `AlInput` has **only** `trailingIcon: @Composable (() -> Unit)?` (`:53`), passed to KomoUI `Input` (`:105`). It does render arbitrary content (`AlSelectField.kt:68-74` puts a `Text` there). Measurement puts units in the **label**: `label = "Peso (kg)"` (`MeasurementScreen.kt:190`) | no `suffix`/`prefix` param |
| Submit button | `AlButton.kt:21-28` — has both `enabled` and `loading` (named `loading`, not `isLoading`) | — |
| Empty / error states | `EmptyState.kt:19-22` (no `Al` prefix, no action slot); pickers hand-roll `Column` + `Spinner` + `AlButton` instead | — |
| Success feedback | `AlToast.kt:48-52` (**`BoxScope` extension**, auto-dismiss 1 s at `:36`), `AlInlineBanner.kt:40-44`, `AlSuccessTick.kt:29-33` | — |
| Confirm delete a line | `AlConfirmDialog.kt:28-38` | — |

**Typography** (`theme/Type.kt`): the three mono data roles the story asks for — `dataLarge` (28/34 Medium, `:121-126`), `data` (16/22 Medium, `:127-132`), `dataSmall` (12/16 Medium, `:133-138`). IBM Plex Mono ships **Medium only** (`:67-69`) — Bold would be synthesized.
**Colors** (`theme/Color.kt:13-50`): 19 roles. **There is no `success`** — success reuses `accent`/`accentContainer` (`AlPill.kt:39-42`).
**Tokens**: `AlSpacing(xs 4, sm 8, md 16, lg 24, xl 32, xxl 48)` (`Spacing.kt:7-14`), `AlCorners(sm 4, md 8, lg 16)` (`Corner.kt:7-11`), `AlBorders(thin 1, medium 2)` (`Border.kt:7-10`).

**Icons** — 95 files in `composeApp/src/commonMain/composeResources/drawable/`. **No food/meal/plate/fruit/scale/calendar/filter/sort/minus icon exists.** Closest generic stand-ins: `ic_mingcute_package_line`, `ic_mingcute_package_2_line`, `ic_mingcute_tag_line`. Full list in the agent output; notable present ones for this feature: `add_line`, `add_fill`, `delete`, `edit`, `search_line`, `close_line`, `check_circle_fill`, `left_line`, `right_line`, `down_line`.

**Pinned-search-bar layout** — three instances, all "pinned by Column structure" (`stickyHeader` has zero matches). Canonical: `SelectSubjectScreen.kt:77-157` — `AlScaffold` → `Column(fillMaxSize + imePadding)` → `AlBackTopAppBar` → `Column(padding horizontal 24.dp)` → `AlInput` → `Box(weight(1f))` → `when { loading / error / empty / LazyColumn }`. Rows inside pass `horizontalPadding = 0.dp` so titles stay flush with the input (`:176-178`).

### 7. Dates & times

- kotlinx-datetime **0.7.1** (`gradle/libs.versions.toml:16,32`; `build.gradle.kts:186`, commonMain only). `Clock`/`Instant` come from **stdlib `kotlin.time`** (7 files import `kotlin.time.Clock`; zero import `kotlinx.datetime.Clock`). `LocalDate`/`TimeZone`/`toLocalDateTime`/`atStartOfDayIn` come from kotlinx-datetime.
- **ISO-8601 UTC for request bodies is produced in exactly three places**, all `Instant.fromEpochMilliseconds(ms).toString()` (emits `…Z`): `MeasurementRepositoryImpl.kt:35-41`, `SurveyResponseRepositoryImpl.kt:43-49`, `SurveyResponseEditRepositoryImpl.kt:47-49`.
- **Every wire date field is typed `String`** — `MeasurementRequest.kt:17` (`@SerialName("measured_at") val measuredAt: String, // ISO 8601 UTC (…Z)`), `SurveyResponseDto.kt:19-50` (`event_at`, `created_at`, `updated_at`), `SubjectRequest.kt:13` (`birth_date`). No custom `@Serializable(with=...)` anywhere.
- **Clock injection**: repositories take `now: () -> Long`, wired at `RepositoriesModule.kt:73,81,86,94,…` as `{ Clock.System.now().toEpochMilliseconds() }`. **ViewModels call `Clock.System.now()` directly** (`MeasurementViewModel.kt:209`, `CreateSubjectViewModel.kt:232`, `SurveyResponseFormViewModel.kt:180`) and are therefore not clock-injectable in tests.
- **No Material3 DatePicker anywhere** (grep for `DatePicker`/`rememberDatePickerState`/`DatePickerDialog`: zero matches). All date entry is a digits-only buffer + `core/design_system/components/input/DateVisualTransformation.kt` (whole file quoted in the agent output — `DDMMYYYY` → `DD/MM/AAAA` with an `OffsetMapping`), used at exactly two sites: `CreateSubjectScreen.kt:141-153` and `SurveyQuestionForm.kt:112-130`.
- **`create_subject` birth-date chain**: raw `"01011990"` in `TextFieldState.value` → `parseBirthDate` (`CreateSubjectViewModel.kt:220-229`) → `LocalDate` → persisted `.toString()` → `SubjectRequest.birthDate`. The validator (`:207-218`) is the only one mixing `Rules` with a raw lambda, because it needs `today()` for the not-future check — **the direct precedent for the story's "no future date" rule**.
- **No date-formatting utility exists.** The same `padStart(2,'0')` block is duplicated six times: `HomeStateMapper.kt:179-186`, `MeasurementUploadUi.kt:119-126`, `SurveyResponseDetailViewModel.kt:137-144`, `SurveyResponseHistoryContent.kt:191-196`, `MeasurementViewModel.kt:207-216` (`friendlyNow()` → `"Ahora · dd/MM/yyyy HH:mm"`, the read-only "measured at" caption), `SurveyAnswerFormatting.kt:23-28`. **No relative-time ("hace 2 días") helper exists** — deliberate, per the comment at `HomeStateMapper.kt:178`.
- `TimeZone.currentSystemDefault()` is inlined at 8 call sites; only `HomeStateMapper.kt:32,173` makes it an injectable default parameter (which is what makes it testable — `HomeStateMapperTest.kt:20` pins `TimeZone.UTC`). `toInstant` is never used.

### 8. Establishments: approved list + current/default (pre-fill, not gate)

**Two separate concepts, two SQLDelight tables.**

- Approved list: `GET api/v1/users/me/establishments` (`ProfileApi.kt:46-47`) → cached read-through in `CachedEstablishment` (`data/profile/local/EstablishmentLocalStore.kt`, `DefaultEstablishmentLocalStore.kt`). Cache-first read at `ProfileRepositoryImpl.kt:37-46`; warmed once at `AuthedShell.kt:137-140`.
- Current establishment: **id only**, single-row SQLDelight table — `CurrentEstablishment.sq` (whole file quoted in agent output), `CurrentEstablishmentLocalStore.kt` (`get(): String?` / `set(id: String?)`), `DefaultCurrentEstablishmentLocalStore.kt:14-20`.
- `GetCurrentEstablishmentUseCase.kt:11-15` — takes the caller's **already-approved-filtered** list and resolves the id against it; a revoked id silently → `null` (ADR-014). `SetCurrentEstablishmentUseCase.kt:6-9` persists only the id.
- `GetMyEstablishmentsUseCase.kt:6-8` returns the **unfiltered** list; **approved-filtering is repeated at every read site**: `HomeViewModel.kt:83`, `MeasurementViewModel.kt:114`, `SurveyResponseFormViewModel.kt:114`.
- Models: `EstablishmentWithStatus.kt` (9 fields, incl. `formattedAddress`, `status`), `EstablishmentStatus.kt:3` (`Approved/Pending/Rejected/Unknown`), and the *different, thinner* catalog model `domain/model/establishment/Establishment.kt` (`@Serializable`, `id/name/address`).

**The pre-fill-not-gate reference, twice, with an identical fallback chain:**
- `MeasurementViewModel.kt:109-136`, line `:122` — `selectedEstablishment = current ?: approved.singleOrNull() ?: it.selectedEstablishment`, with the comment *"Current establishment (Home's default) wins over auto-select-when-single"*
- `SurveyResponseFormViewModel.kt:94-118`, line `:116` — same chain; KDoc at `:94-97` states *"a convenience, not a gate"*
- Not a gate: override is free (`MeasurementViewModel.kt:82-90`), validation is a plain nullable check on submit (`:143` — `establishmentError = if (selected == null) "Elegí el establecimiento." else null`), and empty options render a caption rather than blocking (`MeasurementScreen.kt:181-188`).

**In-form establishment selector UI** — `AlSelectField` + Material3 `ModalBottomSheet` of `AlListRow`s, hand-rolled identically in two places (no shared composable): `MeasurementScreen.kt:122-142` + `:173-188`, and `HomeScreen.kt:116-137` + `:141-178`. `thoughts/plans/home_current_establishment.md:26-30,66-68` records the decision to duplicate rather than extract a seam.

**`select_establishment` does NOT set the current establishment** — its VM only takes `GetEstablishmentsUseCase` (`SelectEstablishmentViewModel.kt:21-24`) and returns the pick via `savedStateHandle`.

### 9. DI registration

Koin 4.2.1, started via `KoinGlobalContext` (`di/KoinGlobalContext.kt:6-22`) from `AlApp.onCreate()` (Android, with `androidContext(this@AlApp)`) and `InitKoinKt.doInitKoin()` (iOS, `AppDelegate.swift:9`). Seven modules aggregated at `di/InitKoin.kt:12-22`.

| Layer | File | Idiom |
|---|---|---|
| Api | `ClientsModule.kt:50-56` | `single { FooApi(client = get()) }` (authed) or `get(named("refresh"))` |
| SQLDelight queries | `DatabaseModule.kt:17-24` | `single { get<AppDatabase>().fooQueries }` — **not needed for this feature** |
| LocalStore / Repository | `RepositoriesModule.kt:55-157` | `single<Iface> { Impl(...) }`, all deps as named args + `get()` |
| UseCase | `UseCasesModule.kt:49-94` | `factory { X(get()) }` — 44 entries, all positional |
| ViewModel | `ViewModelsModule.kt:32-104` | `viewModelOf(::X)` when every param is Koin-resolvable; `viewModel { (route: Route.X) -> … }` the moment a nav arg is a param |

Idioms **not** used anywhere: `singleOf(`, `factoryOf(`, `bind`, `scope {`, `includes(`, `createdAtStart`.
ViewModel resolution in Composables: `koinViewModel()` as a defaulted param on `*ScreenRoot` (`SurveyListScreen.kt:46`), or `koinViewModel { parametersOf(route) }` (`SurveyResponseFormScreen.kt:44`). `koinInject()` only for non-VM deps, only in `AuthedShell.kt:125-132`. There is no `KoinContext { }` composable wrapper.
`platformModule` is the only expect/actual (`di/Module.kt:6`); `sharedModule` (`:8-10`) is empty.

### 10. Home entry point for a fourth quick action

`HomeScreen.kt:243-268` — `QuickActions` is a fixed `Row` of exactly three `AlCardIconButton`s, each `Modifier.weight(1f)`:
- `ic_mingcute_ruler_line` "Medición" → `HomeAction.NewMeasurementClick`
- `ic_mingcute_user_3_line` "Sujeto" → `HomeAction.NewSubjectClick`
- `ic_mingcute_doc_line` "Encuesta" → `HomeAction.RespondSurveyClick`

`HomeAction.kt:6-14` (8 actions) and `HomeDestination.kt:3-9` (5 destinations) are where a fourth would register. `AlCardIconButton.kt:32-38` takes `icon: DrawableResource`, `label`, `onClick`, `modifier`, `container`, `iconTint`. Note Home's destinations are dispatched **inline in `AuthedShell.kt:88-105`**, not in `HomeNavigation.kt` (which is documented dead code at `HomeNavigation.kt:3-7`).

### 11. Character-count / max-length precedent

There is **no character-counter UI anywhere** in the repo. The nearest thing is `Rules.maxLength` used as a validator without a live counter:
- `NotifyEstablishmentViewModel.kt:29,38` — `maxLengthRule` shared across fields, `optionalFieldValidator = Rules.of(maxLengthRule)` — **the exact shape an optional 500-char `comments` field needs**
- `RegisterViewModel.kt:165,168,172` (`MAX_LOCALITY_LENGTH`, `MAX_DESCRIPTION_LENGTH`, `MAX_PHONE_LENGTH`)
- `CreateSubjectViewModel.kt:199,204` (`maxLength(50)`)

Hard truncation in the reducer (the alternative to a validator) has two precedents: `CreateSubjectViewModel.kt:84` (`.filter(Char::isDigit).take(8)`) and `SurveyQuestionForm.kt:117`.

---

## Code References

**Precedent A — debounced search + pagination + online submit**
- `presentation/feature/profile/request_establishment/RequestEstablishmentViewModel.kt:19,28,30-36,41-44,81-100,102-125,127-146,148-150`
- `presentation/feature/profile/request_establishment/RequestEstablishmentState.kt:11-24`
- `presentation/feature/profile/request_establishment/RequestEstablishmentScreen.kt:38,69-78,114-194,197-222`
- `presentation/feature/profile/request_establishment/RequestEstablishmentNavigation.kt:26-38`
- `presentation/feature/profile/request_establishment/EstablishmentRequestErrorMessage.kt:5-13`
- `data/profile/ProfileApi.kt:34-55`
- `data/profile/dto/AvailableEstablishmentResponse.kt:18-25`
- `data/profile/mapper/ProfileMapper.kt:34-50`
- `domain/model/profile/PagedEstablishments.kt:3-8`
- `commonTest/.../request_establishment/RequestEstablishmentViewModelTest.kt`

**Precedent B — picker returning a result**
- `presentation/feature/subject/select_subject/SelectSubjectNavigation.kt:10-41` (whole file)
- `presentation/feature/subject/select_subject/SelectSubjectViewModel.kt:26-35,44-51,92-115`
- `presentation/feature/subject/select_subject/SelectSubjectScreen.kt:68-75,77-157,160-185,209`
- `presentation/feature/subject/select_subject/SelectSubjectDestination.kt:5`
- `presentation/feature/measurement/create_measurement/MeasurementNavigation.kt:18-32,40`
- `presentation/feature/measurement/create_measurement/MeasurementScreen.kt:53-65`
- `presentation/navigation/NavResults.kt:7-41`
- `domain/model/subject/SubjectSummary.kt:5-12`

**Network / errors**
- `data/network/ApiConfig.kt:13-21`
- `data/network/HttpClientFactory.kt:26-30,33-47,50-95`
- `di/ClientsModule.kt:23-57`
- `data/survey/dto/SurveyResponseConflictProblem.kt:6-33` ← the `type`-discriminated envelope
- `data/survey/SurveyResponseApi.kt:27-57,96-128`
- `data/subject/SubjectApi.kt:17-22,25-47,49-57`
- `data/subject/SubjectUploadOutcome.kt:6-13`
- `data/survey/SurveyRepositoryImpl.kt:44-57`
- `domain/model/survey/SurveyFeatureError.kt:5-13`
- `domain/model/profile/EstablishmentRequestError.kt:3-9`
- `data/profile/ProfileRepositoryImpl.kt:69-71,106-140` ← the dead-mapping trap
- `data/network/connectivity/ConnectivityObserver.kt:10-16`; `androidMain/.../AndroidConnectivityObserver.kt:18-46`; `iosMain/.../IosConnectivityObserver.kt:21-32`

**Navigation**
- `presentation/navigation/Route.kt:6-128`
- `app/AppNavHost.kt:27-65`
- `app/AuthedShell.kt:39-119,125-155`
- `app/LocalOuterNavController.kt:6-7`
- `presentation/feature/profile/edit/EditProfileNavigation.kt` (whole file)
- `presentation/feature/survey/survey_response_form/SurveyResponseFormNavigation.kt` (whole file)
- `presentation/feature/survey/survey_response_form/SurveyResponseFormScreen.kt:41-57`

**Validation / decimals / ids / keyboard**
- `core/validation/TextFieldState.kt:1-61`
- `core/validation/Rules.kt:22-47` (incl. `decimalInRange` at `:39-43`)
- `domain/util/DecimalInput.kt:1-14`
- `domain/util/IdGenerator.kt:1-14`; DI `di/RepositoriesModule.kt:75`
- `data/measurement/dto/MeasurementRequest.kt:6-18`; `data/measurement/mapper/MeasurementNetworkMapper.kt:8-16`
- `presentation/feature/measurement/create_measurement/MeasurementViewModel.kt:48-68,138-195,197-205,207-216`
- `presentation/feature/measurement/create_measurement/MeasurementScreen.kt:76-86,113-118,161-167,173-188,189-213,222`
- `core/design_system/components/input/AlInput.kt:25-56,58-88,91-109`
- `core/design_system/components/input/AlSelectField.kt:17-49,68-74`
- `presentation/feature/subject/create_subject/CreateSubjectViewModel.kt:80-92,111-126,173-190,192-208,207-232`
- `core/design_system/components/input/DateVisualTransformation.kt` (whole file)

**Establishments**
- `domain/usecase/profile/GetCurrentEstablishmentUseCase.kt:11-15`; `SetCurrentEstablishmentUseCase.kt:6-9`; `GetMyEstablishmentsUseCase.kt:6-8`; `RefreshMyEstablishmentsUseCase.kt:6-7`
- `data/profile/local/CurrentEstablishmentLocalStore.kt` (whole file); `DefaultCurrentEstablishmentLocalStore.kt:8-20`; `sqldelight/.../CurrentEstablishment.sq` (whole file)
- `data/profile/local/EstablishmentLocalStore.kt` (whole file); `data/profile/ProfileRepositoryImpl.kt:37-46,48-52`
- `domain/model/profile/EstablishmentWithStatus.kt`; `EstablishmentStatus.kt:3`
- `presentation/feature/measurement/create_measurement/MeasurementViewModel.kt:109-136` ← the pre-fill reference
- `presentation/feature/survey/survey_response_form/SurveyResponseFormViewModel.kt:94-118`
- `presentation/feature/home/HomeViewModel.kt:64-74,77-90,101-110`; `HomeScreen.kt:89-93,116-137,141-178`

**DI**
- `di/Module.kt:6-10`; `di/InitKoin.kt:7-32`; `di/KoinGlobalContext.kt:6-24`; `di/AppScope.kt:11`
- `di/ClientsModule.kt:23-57`; `di/RepositoriesModule.kt:55-157`; `di/UseCasesModule.kt:49-94`; `di/ViewModelsModule.kt:32-104`; `di/DatabaseModule.kt:15-36`
- `androidMain/.../di/Modules.android.kt:15-21`; `iosMain/.../di/Modules.ios.kt:14-20`

**Design system**
- `core/design_system/theme/Type.kt:29-54,67-69,121-138`; `Color.kt:13-50`; `Spacing.kt:7-14`; `Corner.kt:7-11`; `Border.kt:7-10`
- `core/design_system/components/scaffold/AlScaffold.kt:12-23`; `top_app_bar/AlBackTopAppBar.kt:22,30-36`; `button/AlButton.kt:21-28`; `button/AlCardIconButton.kt:32-38`; `card/AlSelectableCard.kt:34-40`; `card/AlInfoCard.kt:41-48`; `list/AlListRow.kt:45-58`; `pill/AlPill.kt:18,26-29`; `input/AlChoiceGroup.kt:21,30-38,71-95`; `segmented/AlSegmentedControl.kt:27-31`; `toast/AlToast.kt:36,48-52`; `banner/AlInlineBanner.kt:40-44,97`; `dialog/AlConfirmDialog.kt:14,28-38`; `EmptyState.kt:19-22`
- `composeApp/src/commonMain/composeResources/drawable/` — 95 `ic_mingcute_*.xml`

**Home entry point**
- `presentation/feature/home/HomeScreen.kt:243-268`; `HomeAction.kt:6-14`; `HomeDestination.kt:3-9`; `app/AuthedShell.kt:88-105`; `HomeNavigation.kt:3-7`

---

## Architecture Insights

- **The six-file feature contract is real and uniformly followed**, with two documented exceptions: `home` (dispatch inlined in `AuthedShell`, its `*Navigation.kt` is dead) and `profile/view` (registered inline, dispatcher named `navigateProfile`). `request_establishment` also carries a seventh file for error-message mapping — a precedent for a `FoodIntakeErrorMessage.kt`.
- **Error handling is split by client, and the split is load-bearing.** Refresh-client repos map `ClientRequestException` → typed error; authed-client Apis branch on `response.status.value` → `*Outcome`. Mixing them silently breaks (see the `ProfileRepositoryImpl` finding). The food endpoints are all authed, so they belong to the second family.
- **Nav-arg payloads are pre-serialized JSON strings**, both for route arguments (`preselectedJson`) and for `savedStateHandle` results (`subjectJson`, `selectedJson`). Any domain model crossing that boundary must be `@Serializable`.
- **Result consumption is consume-once by set-to-null** (`entry.savedStateHandle[KEY] = null`) for picker payloads and by `remove()` for Boolean refresh flags. The two families do not interoperate.
- **Everything client-generated-id is currently DB-parked.** The four existing idempotency-key users all persist the UUID as a SQLDelight `localId`. A no-DB feature has no such anchor, so surviving process death and retries is a genuinely new problem here, not a copy.
- **Validation eagerly recomputes `error` and separately decides `displayError`** — `isValid` is always current, so a submit can force-show and still read validity in the same pass (`TextFieldState.kt:24-41`). Non-text fields (a `Sex` selector, an establishment) keep a plain `String?` error set only on submit.
- **`AlInput` owns the CMP keyboard-scroll pattern**; screens supply `scrollState` + `isImeVisible` and nothing else. The pattern degrades gracefully — omitting both disables it, which is what all three search boxes do.
- **There is no shared establishment-picker composable**, deliberately (`thoughts/plans/home_current_establishment.md:26-30`); the `AlSelectField` + `ModalBottomSheet` block is duplicated in Home and Measurement. A third copy would be the point at which extraction becomes defensible.
- **Two `Json` instances and two `HttpClient`s coexist under Koin qualifiers** — default vs `named("auth")` for Json, default (authed) vs `named("refresh")` for the client. Getting the qualifier wrong compiles and fails at runtime.

---

## Historical Context (from thoughts/)

- `thoughts/stories/create_food_intake_screen.md` — the story itself, identical to the attachment. Names `request_establishment` and `select_subject` as the two patterns to copy; states "no SQLDelight, no migration, no SyncEngine — the first loading screen in the app that does not touch the local DB"; flags that the authed client has no `expectSuccess` so `type` must be read manually to tell the two 404s apart; and pins `quantity` as a `String` normalized to 3 decimals up to the network edge.
- `thoughts/stories/food_stock_management.md` — the contract-level research behind it. Key facts: **`base_unit` is a scalar column** (no `allowed_units`, no conversion factor); **decision 2026-09-06 is option A** — the app never offers a unit choice and renders the unit as a field suffix, "la UI no debe insinuar una elección que no existe". Seed: 24 categories, 898 foods, split 767 kg / 94 L / 37 unidades — so ~85% of the catalog is weighed, and copy should say "Cantidad en kg" rather than implying counting. `is_active` was **removed** by PR #119, which is what makes the `food-not-found` 404 a live race. Backend does **not** validate integer-ness for `unidades` (it accepts 12.5 eggs) — that check is client-side only. There is **no intake list endpoint**, so the app cannot show "what I loaded". Two open product decisions: where "Alimentos" lives among Home's three quick actions, and whether a stock screen exists.
- **Backend dependency status** (from the same doc, re-verified 2026-09-06): #119 `feat/food-catalog` **merged** (`2a3f6df`); #123 `feat/food-input-datamodel` open, **force-push-rebased** (`5a775aa`), now data-model only; #126 `feat/food-stock-per-establishment` open and **stale** (`ebe3cde`), hanging off the pre-rebase #123 (merge-base `8fa5644`) and still carrying the removed `is_active`. **#126 is the only branch with `POST /food-intakes`.**
- `thoughts/grilling/adr-012` … `adr-015` — the current-establishment decisions: pre-fill not gate, one-off override never written back (012); id-only single-row store, no denormalized snapshot (013); stale id resolves to `null` silently, no banner (014); the affordance shows regardless of establishment count and never gates the CTA (015).
- `thoughts/grilling/adr-010-reuse-manual-load-more.md` — manual `page`/`totalPages` + `LoadMore`, **no Paging3**. `DEFAULT_PAGE_SIZE = 20`. Accepts offset-pagination drift as cosmetic, and suggests **client-side dedup of already-rendered ids before appending** as a cheap defensive one-liner — which the current `request_establishment`/`select_subject` implementations do **not** do.
- `thoughts/plans/select_subject_screen.md` — the documented design of the picker (300 ms debounce, `snapshotFlow` end-detection with a 3-item threshold, `savedStateHandle` return, `AlListRow` not `AlSelectableCard`, no local caching).
- `thoughts/plans/select_establishment_search_screen.md` — the original mold; explicitly *did not* add pagination (single `page_size=100`). `own_profile_screens.md:487` records the later switch to real pagination.
- `thoughts/plans/create_measurement_screen.md` — where `Rules.decimalInRange` was introduced, and the record that `measured_at` is stamped in the repository as UTC with zone with **no date picker**.
- `thoughts/plans/create_subject_screen.md` — the *older* ad-hoc `fieldErrors` convention, superseded by `core/validation/`. Useful only for its success-flow shape (form clears, user stays, inline banner).
- `thoughts/plans/home_current_establishment.md:26-30,66-68` — the deliberate decision to duplicate the bottom-sheet picker rather than extract a seam.
- **No plan document for the food feature exists yet** — only the two stories. No food-related ADRs (grilling ADRs 001–015 cover offline reads, the activity feed, and the current establishment).

---

## Related Research

- `thoughts/research/2026-07-13_14-34-55_survey-response-activities-sync.md`
- `thoughts/research/2026-07-13_16-29-36_survey-available-offline-gap.md`
- `thoughts/research/2026-05-01_23-05-58_auth_token_storage.md`

---

## Open Questions

1. **`POST /food-intakes` is not on any merged branch.** Backend #126 needs a rebase over the force-pushed #123 before the submit path can be integrated or tested. Both GETs are available on `develop` (after the catalog seed runs in the environment).
2. **Where does "Alimentos" enter?** `HomeScreen.kt:243-268` is a fixed 3-wide `Row`; a fourth `AlCardIconButton` forces either a 2×2 grid or a horizontal scroll. Unresolved in the story.
3. **No food icon exists.** All 95 MingCute drawables are listed above and none are food/meal-related — a new one is needed via the `add-mingcute-icon` skill.
4. **No horizontal chip row exists** (`LazyRow`/`horizontalScroll`/`FilterChip`: zero matches). `AlChoiceGroup` is a wrapping `FlowRow` with a mandatory label and a private `ChoiceChip`; `AlPill` has no `selected`/`onClick`. With 24 categories, the shape of that control is an open design question.
5. **`AlInput` has no suffix slot.** `trailingIcon` accepts arbitrary content but sits in the field's trailing icon area; the existing repo answer for units is to put them in the label (`"Peso (kg)"`). The story asks for `Cantidad (kg)` — which the label approach already satisfies — but "renders as a suffix of the field" could be read either way.
6. **`sanitizeDecimalInput` caps nothing.** It normalizes the separator and collapses dots, but enforces neither the 3-decimal limit nor the 9 999 999,999 cap, and has no unit test. The `unidades` integer case has no sanitizer at all.
7. **Where does the idempotency UUID live across process death?** Every existing user parks it in SQLDelight; this feature has no DB. Whether ViewModel state is sufficient (vs `SavedStateHandle`) is undecided by the story.
8. **Full-form lock during submit is not the existing behavior.** `request_establishment` only disables the back button (`RequestEstablishmentScreen.kt:89`); the story asks for the whole form to be blocked.
9. **Accent-insensitive search is a backend gap** (`ILIKE %name%`, no `unaccent`) and, given the no-cache decision, cannot be compensated client-side.
10. **iOS `ConnectivityObserver.isOnline()` suspends until the first `nw_path_monitor` emission** (`IosConnectivityObserver.kt:32`), unlike Android's seeded version. For a screen whose entry state is "sin conexión al entrar", that first-check latency matters.
