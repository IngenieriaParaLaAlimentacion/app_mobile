# Select Establishment Search Screen Implementation Plan

## Overview

Replace the inline `EstablishmentMultiSelect` dropdown on the register screen with a dedicated full-screen establishment picker. Tapping the field navigates to a new `SelectEstablishment` screen that shows a search bar at the top and a `LazyColumn` of full-width establishment cards below. Typing debounces and requests the establishments endpoint with a `search` query param (multi-select preserved; a confirm action returns the chosen set to register).

## Current State Analysis

- **Register multi-select is inline.** `RegisterScreen.kt:148` renders `EstablishmentMultiSelect`, a KomoUI `DropdownMenu` of `Checkbox` rows (`presentation/feature/register/component/EstablishmentMultiSelect.kt`). All options are crammed into the dropdown.
- **Register owns establishment loading.** `RegisterViewModel.kt:28` calls `loadEstablishments()` in `init`, storing `establishments: List<Establishment>` and `selectedEstablishmentIds: Set<String>` in `RegisterState` (`RegisterState.kt:11-12`).
- **Backend has no search param.** `GET /api/v1/establishments` (`docs/tpp-backend.openapi.generated.json`) accepts only `page` and `page_size`; `EstablishmentApi.kt:15` sends those. Per decision, we wire a `search` query param now — the backend ignores it (returns all) until implemented.
- **No navigation-result pattern exists.** `grep` for `savedStateHandle`/`previousBackStackEntry` across `composeApp/src` returns nothing. Routes are type-safe `data object`s in `Route.kt`; navigation uses Jetbrains `navigation-compose` 2.9.2.
- **Conventions.** Six-file feature pattern (Action/Destination/Navigation/Screen/State/ViewModel), Koin `viewModelOf`, `AlScaffold` + `AlTheme` for layout, KomoUI `Input`/`Spinner`/`Card`. Coroutines 1.10.2 (`Flow.debounce` is stable).

### Key Discoveries
- Register selection model: `RegisterState.kt:11-12`, `selectedEstablishmentIds` consumed at `RegisterViewModel.kt:104` (`current.selectedEstablishmentIds.toList()`).
- Establishment field error wiring: `RegisterScreen.kt:156`, `RegisterViewModel.kt:132`.
- Data layer chain to extend for search: `EstablishmentApi.kt:15` → `EstablishmentRepository.kt:6` → `EstablishmentRepositoryImpl.kt:8` → `GetEstablishmentsUseCase.kt:7`.
- Navigation wiring points: `Route.kt`, `AppNavHost.kt:19-20`, `ViewModelsModule.kt:9-13`, `RegisterNavigation.kt:25` (`navigate()`).
- `Establishment` is a plain `data class` (`Establishment.kt:3`) — needs `@Serializable` to be passed as a JSON nav payload.

## Desired End State

On the register screen, the "Establecimientos" field is a read-only trigger showing the count/names of selected establishments. Tapping it navigates to `SelectEstablishment`. That screen shows a search `Input` pinned at the top and a `LazyColumn` of full-width cards. Typing debounces (~300 ms) and re-requests the endpoint with the query; tapping a card toggles its selection (selections persist even when filtered out by a later search); a "Listo" confirm action returns the selected establishments to register and pops back. Register displays the returned selection and submits their ids unchanged.

Verify: register compiles, picker screen navigates and back-returns selection, register shows selected names, submission still sends the correct establishment ids.

## What We're NOT Doing

- Not changing the backend or the `POST register/establishment-manager` request shape (still sends `establishment_ids`).
- Not switching to single-select (multi-select retained per decision).
- Not adding infinite-scroll pagination — keep the existing single page request (`page_size = 100`); search narrows results server-side once supported.
- Not adding unit tests for KomoUI rendering; ViewModel + use case tests only (per testing rules).
- Not introducing a nested nav graph or shared scoped ViewModel — using the `savedStateHandle` result pattern.

## Implementation Approach

1. **Data layer:** thread an optional `search: String?` through API → repository → use case (default `null`, backward compatible).
2. **New picker feature** `presentation/feature/establishment/select_establishment/` with the six-file pattern, debounced search in its ViewModel, seeded from a JSON payload of the currently-selected establishments and returning the new selection via `savedStateHandle`.
3. **Register refactor:** drop inline loading/multiselect; store `selectedEstablishments: List<Establishment>`; field becomes a trigger that navigates to the picker; consume the returned result.
4. **Navigation/DI wiring** for the new route, ViewModel, and result observation.

`Establishment` gains `@Serializable` so the selection can be JSON-encoded as a route arg (seed) and a `savedStateHandle` result (return) — both are single `String` values, which are CMP `SavedState`-safe and retain names across searches.

---

## Phase 1: Data layer — optional search param

### Changes Required

#### 1. EstablishmentApi
**File**: `data/establishment/EstablishmentApi.kt`
Add an optional `search` parameter, sent only when non-blank.
```kotlin
suspend fun getEstablishments(
    search: String? = null,
    pageSize: Int = DEFAULT_PAGE_SIZE,
): PaginatedEstablishmentsResponse =
    refreshClient.get("api/v1/establishments") {
        parameter("page", 1)
        parameter("page_size", pageSize)
        search?.takeIf { it.isNotBlank() }?.let { parameter("search", it) }
    }
```

#### 2. EstablishmentRepository (interface)
**File**: `domain/repository/EstablishmentRepository.kt`
```kotlin
suspend fun getEstablishments(search: String? = null): Result<List<Establishment>>
```

#### 3. EstablishmentRepositoryImpl
**File**: `data/establishment/EstablishmentRepositoryImpl.kt`
```kotlin
override suspend fun getEstablishments(search: String?): Result<List<Establishment>> = runCatching {
    api.getEstablishments(search = search).items.map { it.toDomain() }
}
```

#### 4. GetEstablishmentsUseCase
**File**: `domain/usecase/establishment/GetEstablishmentsUseCase.kt`
```kotlin
suspend operator fun invoke(search: String? = null): Result<List<Establishment>> =
    repository.getEstablishments(search)
```

#### 5. Establishment model — make serializable
**File**: `domain/model/establishment/Establishment.kt`
```kotlin
@Serializable
data class Establishment(val id: String, val name: String, val address: String)
```

### Success Criteria

#### Automated Verification:
- [x] Kotlin compilation passes: `./scripts/compile.sh`

#### Manual Verification:
- [ ] Existing register flow still loads establishments (no behavior change yet, default `search = null`).

---

## Phase 2: Select Establishment picker feature

### Overview
New feature folder `presentation/feature/establishment/select_establishment/` following the six-file pattern. ViewModel debounces the query and calls `GetEstablishmentsUseCase(search)`; selection is seeded from the route payload and returned via the destination.

### Changes Required

#### 1. Route
**File**: `presentation/navigation/Route.kt`
```kotlin
@Serializable
@SerialName("select_establishment")
data class SelectEstablishment(val preselectedJson: String = "[]") : Route()
```

#### 2. SelectEstablishmentAction
**File**: `.../select_establishment/SelectEstablishmentAction.kt`
```kotlin
sealed interface SelectEstablishmentAction {
    data class QueryChanged(val value: String) : SelectEstablishmentAction
    data class EstablishmentToggled(val establishment: Establishment) : SelectEstablishmentAction
    data object ConfirmClick : SelectEstablishmentAction
    data object BackClick : SelectEstablishmentAction
    data object RetryClick : SelectEstablishmentAction
}
```

#### 3. SelectEstablishmentDestination
**File**: `.../select_establishment/SelectEstablishmentDestination.kt`
```kotlin
sealed class SelectEstablishmentDestination {
    data object Back : SelectEstablishmentDestination()
    data class Confirm(val selectedJson: String) : SelectEstablishmentDestination()
}
```

#### 4. SelectEstablishmentState
**File**: `.../select_establishment/SelectEstablishmentState.kt`
```kotlin
data class SelectEstablishmentState(
    val destination: SelectEstablishmentDestination? = null,
    val query: String = "",
    val results: List<Establishment> = emptyList(),
    val selected: List<Establishment> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
) {
    fun isSelected(id: String): Boolean = selected.any { it.id == id }
    val selectedCount: Int get() = selected.size
}
```

#### 5. SelectEstablishmentViewModel
**File**: `.../select_establishment/SelectEstablishmentViewModel.kt`
- Receives `route: Route.SelectEstablishment`; decodes `preselectedJson` into `selected` on init.
- Holds a `MutableStateFlow<String>` for the query; a debounced collector (`debounce(300).distinctUntilChanged().flatMapLatest { ... }` or `mapLatest`) calls `getEstablishments(search)` and updates `results`/`isLoading`/`loadError`.
- `EstablishmentToggled` adds/removes from `selected` by id (retaining the full object so names survive filtering).
- `ConfirmClick` emits `Destination.Confirm(Json.encodeToString(selected))`; `BackClick` emits `Back`.
- `clearDestination()` resets destination.

```kotlin
class SelectEstablishmentViewModel(
    route: Route.SelectEstablishment,
    private val getEstablishments: GetEstablishmentsUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(
        SelectEstablishmentState(
            selected = Json.decodeFromString<List<Establishment>>(route.preselectedJson),
        )
    )
    val state = _state.asStateFlow()
    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            query
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { search(it) }
        }
    }
    // onAction(...) dispatches; search(query) calls getEstablishments(search)
}
```

#### 6. SelectEstablishmentNavigation
**File**: `.../select_establishment/SelectEstablishmentNavigation.kt`
- `NavController.navigateToSelectEstablishment(preselectedJson)`.
- `NavGraphBuilder.selectEstablishmentScreen(navController)` → `composable<Route.SelectEstablishment>`.
- Internal `navigate()`: on `Confirm`, write result to the caller and pop:
```kotlin
SelectEstablishmentDestination.Back -> navController.popBackStack()
is SelectEstablishmentDestination.Confirm -> {
    navController.previousBackStackEntry
        ?.savedStateHandle?.set(SELECTED_ESTABLISHMENTS_RESULT, destination.selectedJson)
    navController.popBackStack()
}
```
Define `const val SELECTED_ESTABLISHMENTS_RESULT = "selected_establishments_result"` (shared key, e.g. in this file or `Route.kt` companion).

#### 7. SelectEstablishmentScreen
**File**: `.../select_establishment/SelectEstablishmentScreen.kt`
- `SelectEstablishmentScreenRoot` (koinViewModel via `koinViewModel { parametersOf(route) }`) + stateless `SelectEstablishmentScreen`.
- `AlScaffold`; top: KomoUI `Input` bound to `query` with placeholder "Buscar establecimiento"; a "Listo" button (header/top-right or bottom bar) dispatching `ConfirmClick`, plus back affordance dispatching `BackClick`.
- Body states: `isLoading` → `Spinner`; `loadError` → message + retry (`RetryClick`); empty results → "Sin resultados"; else `LazyColumn(items = results, key = { it.id })` of full-width cards.
- Each card: `Column`/`Row` with `Modifier.fillMaxWidth().border(1.dp, ...).clickable { onAction(EstablishmentToggled(it)) }.padding(...)`, showing name (`bodyLarge`) + address (`bodyMedium`, muted) and a trailing check indicator when `state.isSelected(id)`. Reuse the border/clickable styling from `EstablishmentMultiSelect.kt:57-79`.

#### 8. Koin registration
**File**: `di/ViewModelsModule.kt`
```kotlin
viewModel { (route: Route.SelectEstablishment) ->
    SelectEstablishmentViewModel(route = route, getEstablishments = get())
}
```

#### 9. NavHost registration
**File**: `app/AppNavHost.kt`
```kotlin
selectEstablishmentScreen(navController)
```

### Success Criteria

#### Automated Verification:
- [x] Kotlin compilation passes: `./scripts/compile.sh`

#### Manual Verification:
- [ ] Picker opens; search bar at top, full-width cards below.
- [ ] Typing narrows results after debounce (network request fires per the OpenAPI search param).
- [ ] Tapping cards toggles selection; selections persist after changing the query.
- [ ] "Listo" returns to register; back without confirm leaves register selection unchanged.

---

## Phase 3: Register integration

### Overview
Register stops loading establishments and stops managing a `Set<String>`. The field becomes a trigger that navigates to the picker, seeded with the current selection; the returned selection is applied to state.

### Changes Required

#### 1. RegisterState
**File**: `presentation/feature/register/RegisterState.kt`
- Replace `establishments`, `selectedEstablishmentIds`, `isLoadingEstablishments` with `selectedEstablishments: List<Establishment> = emptyList()`.
- `canSubmit` uses `selectedEstablishments.isNotEmpty()`.

#### 2. RegisterAction
**File**: `presentation/feature/register/RegisterAction.kt`
- Remove `EstablishmentToggled`.
- Add `data object OpenEstablishmentSelector` and `data class EstablishmentsSelected(val establishments: List<Establishment>)`.

#### 3. RegisterDestination
**File**: `presentation/feature/register/RegisterDestination.kt`
- Add `data class SelectEstablishment(val preselectedJson: String) : RegisterDestination()`.

#### 4. RegisterViewModel
**File**: `presentation/feature/register/RegisterViewModel.kt`
- Remove `GetEstablishmentsUseCase` dependency, `init`, `loadEstablishments()`, `toggleEstablishment()`.
- `OpenEstablishmentSelector` → `destination = SelectEstablishment(Json.encodeToString(selectedEstablishments))`.
- `EstablishmentsSelected` → `copy(selectedEstablishments = action.establishments, fieldErrors = ...copy(establishments = null))`.
- `submit()` maps `current.selectedEstablishments.map { it.id }`; `validate()` checks `selectedEstablishments.isEmpty()`.

#### 5. RegisterScreen
**File**: `presentation/feature/register/RegisterScreen.kt`
- Replace the `Spinner`/`EstablishmentMultiSelect` block (`:145-155`) with a read-only trigger (border + clickable, same style as before) showing: none → "Seleccionar establecimiento", else the selected names (or "N establecimientos seleccionados"), dispatching `OpenEstablishmentSelector`. Keep the field-error `Text` (`:156-158`).

#### 6. RegisterNavigation — navigate out + observe result back
**File**: `presentation/feature/register/RegisterNavigation.kt`
- In `registerScreen`'s `composable<Route.Register>`, read the result and feed it to the Root:
```kotlin
composable<Route.Register> { entry ->
    val resultJson by entry.savedStateHandle
        .getStateFlow<String?>(SELECTED_ESTABLISHMENTS_RESULT, null)
        .collectAsStateWithLifecycle()
    RegisterScreenRoot(
        establishmentsResult = resultJson,
        onResultConsumed = { entry.savedStateHandle[SELECTED_ESTABLISHMENTS_RESULT] = null },
        navigate = { destination -> navigate(destination, navController) },
    )
}
```
- `internal fun navigate(...)` handles the new destination:
```kotlin
is RegisterDestination.SelectEstablishment ->
    navController.navigateToSelectEstablishment(destination.preselectedJson)
```

#### 7. RegisterScreenRoot — apply returned result
**File**: `presentation/feature/register/RegisterScreen.kt`
- Accept `establishmentsResult: String?` and `onResultConsumed: () -> Unit`; in a `LaunchedEffect(establishmentsResult)`, decode JSON → `EstablishmentsSelected`, then call `onResultConsumed()`.

#### 8. Delete obsolete component
**File**: `presentation/feature/register/component/EstablishmentMultiSelect.kt` — delete (no longer referenced).

### Success Criteria

#### Automated Verification:
- [x] Kotlin compilation passes: `./scripts/compile.sh`
- [x] No remaining references to `EstablishmentMultiSelect` / `selectedEstablishmentIds`: `grep -rn "EstablishmentMultiSelect\|selectedEstablishmentIds" composeApp/src` returns nothing.

#### Manual Verification:
- [ ] Register field shows "Seleccionar establecimiento" when empty; tapping opens the picker pre-checking current selection.
- [ ] Returning from the picker updates the register field with selected names.
- [ ] Establishment validation error clears after a selection is made.
- [ ] Submitting registers the correct establishment ids (verify request payload).
- [ ] Scroll-to-field-on-keyboard behavior on the remaining inputs is unaffected (iOS + Android).

---

## Testing Strategy

### Unit tests (commonTest, kotlin.test + Turbine + coroutines-test)
- `SelectEstablishmentViewModel`: seed from `preselectedJson`; query change triggers debounced `getEstablishments(search)` (use `runTest` virtual time + a `FakeEstablishmentRepository`); toggle adds/removes retaining objects; `ConfirmClick` emits `Confirm` with JSON of selected; load failure sets `loadError`.
- `RegisterViewModel`: `EstablishmentsSelected` updates state and clears the field error; `OpenEstablishmentSelector` emits destination with current selection JSON; submit maps ids; validation fails with empty selection.
- `GetEstablishmentsUseCase` / `EstablishmentRepositoryImpl`: `search` forwarded to the API (assert via `FakeEstablishmentApi`/Ktor `MockEngine`).

### Manual Testing Steps
1. Open register, tap establishments field → picker opens.
2. Type a query → results update after debounce.
3. Select two, change query so one is filtered out, confirm → both appear in register.
4. Reopen picker → previously selected are checked.
5. Submit → confirm payload `establishment_ids` matches.

## Performance Considerations
- Debounce (~300 ms) + `distinctUntilChanged` + `mapLatest/collectLatest` cancels in-flight requests on new keystrokes, avoiding request pile-up.
- `LazyColumn` with stable `key = { it.id }` for efficient recomposition.

## Migration Notes
- `search = null` defaults keep the data layer backward compatible; no other caller changes.
- Until the backend implements `search`, the param is ignored and the full list returns — the screen still works (client sees all results).

## References
- Register feature: `presentation/feature/register/` (six-file pattern)
- Reference screen/scaffold: `presentation/feature/home/HomeScreen.kt`
- Existing multi-select styling to reuse: `presentation/feature/register/component/EstablishmentMultiSelect.kt:57-99`
- Data layer chain: `data/establishment/EstablishmentApi.kt`, `EstablishmentRepositoryImpl.kt`, `domain/usecase/establishment/GetEstablishmentsUseCase.kt`
- Backend contract: `docs/tpp-backend.openapi.generated.json` (`GET /api/v1/establishments`)
- Skill: `feature-screen-generator` (file pattern; note its template package is `com.extra.marketplace` — use `ar.com.ingenieriaalimentacion`)
