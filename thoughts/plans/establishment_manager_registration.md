# Establishment Manager Registration Screen — Implementation Plan

## Overview

Build a single-screen self-registration flow for **establishment managers** that posts to
`POST /api/v1/auth/register/establishment-manager`. The screen collects email, password,
first name, last name, and a **multi-select of establishments**, then submits. On success the
account is created as `pending_validation` (pending admin approval), so the user is taken to a
**confirmation screen** — they cannot log in yet.

The screen is built entirely with the **KomoUI** Compose Multiplatform design system
(`io.github.derangga:komoui:0.3.0`); no `Al*` design-system components are used on this feature
(per direction). The establishments dropdown is populated from `GET /api/v1/establishments`.

## Decisions Locked In (from clarification)

| Question | Decision |
|----------|----------|
| Establishment picker | **Multi-select** (API accepts `establishments: [uuid]`, `minItems: 1`) |
| Design system | **shadcn/KomoUI only** for this feature — no `Al*` components |
| After success | **Separate confirmation screen** ("pending approval") |
| Navigation entry | **Branch start destination on `sessionStatus`** (`NotAuthenticated → Register`) |

### ⚠️ Library substitution (please confirm)
The link provided (`shadcn-compose.site`) maps to the artifact `io.github.derangga:shadcn-ui-kmp:0.2.x`,
which the author has **rebranded to KomoUI** and marked **end-of-life** (README: "The old artifact is
end-of-life and will receive no further releases"). This plan uses the maintained successor
**`io.github.derangga:komoui:0.3.0`** — identical shadcn-inspired components, package `com.komoui`,
theme `KomoTheme`. If you specifically want the frozen `shadcn-ui-kmp:0.2.2` instead, only Phase 1
coordinates/imports change.

## Current State Analysis

- **Backend contract** (`docs/tpp-backend.openapi.generated.json`): `register/establishment-manager`
  takes `RegisterManagerRequest { email, password, name, last_name, establishments[uuid] (minItems 1) }`
  and returns `201 RegistrationResponse { id, email, name, last_name, role, status, establishments[uuid] }`.
  Error responses: `400` invalid, `404` establishments not found, `409` user already exists — all
  `application/problem+json` (`ProblemDetail`).
- **Establishments source**: `GET /api/v1/establishments` → `PaginatedEstablishmentsResponse` of
  `EstablishmentResponse { id, name, address }`. Unauthenticated. Needed to populate the multi-select.
- **HTTP stack** (`data/network/HttpClientFactory.kt`, `data/network/ApiConfig.kt`): Ktor with two
  clients — a `named("refresh")` unauthenticated client and the default Bearer-authed client. Registration
  and establishments are **unauthenticated**, so both use the `named("refresh")` client.
  `ApiConfig.BASE_URL` is still the placeholder `https://api.example.com/`.
- **Auth precedent** (`data/auth/AuthApi.kt`, `AuthRepositoryImpl.kt`): thin `*Api` class wrapping an
  injected `HttpClient`; repo uses `runCatching { … }.recoverCatching { throw DomainFailure(mapToError(it)) }`;
  typed sealed error (`AuthError`) wrapped in an `Exception` (`AuthFailure`). **No mappers dir, no
  ProblemDetail parsing exists yet** — this feature introduces both.
- **Presentation pattern** (`presentation/feature/home/`, 6 files): immutable `State` with embedded
  `destination: *Destination?`; `ViewModel` exposes `state` via `stateIn(...WhileSubscribed(5000))`,
  reduces sealed `Action`s, sets `destination` to navigate, `clearDestination()` after. Screen split into
  `*ScreenRoot` (Koin `koinViewModel()`, `collectAsStateWithLifecycle`, `LaunchedEffect(destination)`)
  and pure `*Screen(state, onAction)`.
- **Navigation** (`presentation/navigation/Route.kt`, `app/AppNavHost.kt`): `@Serializable sealed class
  Route` with `@SerialName` data objects; per-feature `NavGraphBuilder.xScreen(navController)` extensions.
  `App.kt:22` currently hardcodes `startScreen = Route.Home` inside `AlTheme { … }`, but already collects
  `sessionStatus` and notes "Future Login screen will branch start destination on this value."
- **Design system**: custom `AlTheme` with only `AlScaffold` + `AlButton`; **no form inputs**. Home uses
  `AlTheme`. KomoUI brings its own `KomoTheme` (wraps Material3).
- **DI** (Koin): `clientsModule` (clients/APIs), `repositoriesModule`, `useCasesModule` (factories),
  `viewModelsModule` (`viewModelOf`). `named("refresh")` client + `Json` already registered.
- **Build**: Kotlin `2.3.20`, Compose MP `1.10.3`, Ktor `3.4.2`, Koin `4.2.1`, Nav `2.9.2`, minSdk 26.
  KomoUI states Kotlin 2.2.x / Compose 1.9.x — **newer here**, so Phase 1 verifies compatibility first.

### Key Discoveries
- Auth API call shape to copy: `data/auth/AuthApi.kt:17-30` (`refreshClient.post("path"){ setBody(dto) }.body()`).
- Error-mapping precedent: `data/auth/AuthRepositoryImpl.kt:39-76` (`runCatching` → typed sealed error).
- VM/state/nav precedent: all six files in `presentation/feature/home/`.
- Start-destination hook already in place: `app/App.kt:17-27` collects `sessionStatus`.
- KomoUI real APIs (verified from source, package `com.komoui.components`):
  - `Input(value, onValueChange, modifier, placeholder, enabled, isError, visualTransformation, keyboardOptions, leadingIcon, trailingIcon, supportingText)`
  - `Select<T>(options, selectedOption, onOptionSelected, label, modifier, enabled, placeholder)` — **single-select**
  - `Button(onClick, modifier, variant, size, enabled, loading, …, content)` — has `loading`
  - `Checkbox(checked, onCheckedChange, modifier, enabled)`
  - `DropdownMenu(expanded, onDismissRequest, trigger, modifier, content)` + `DropdownMenuItem(onClick, …, content)`
  - `Spinner(...)`, tokens via `MaterialTheme.styles.foreground/mutedForeground`, `KomoTheme { }`
  - **No native multi-select** → compose one from `DropdownMenu` + `Checkbox` rows.

## Desired End State

Launching the app while unauthenticated lands on the Register screen. The user fills the form, opens the
establishments dropdown, checks one or more establishments, submits; a spinner shows on the button while the
request is in flight. On `201` the app navigates to a confirmation screen explaining the account is pending
approval. Validation errors and server errors (409 already exists, 404 establishment not found, network) show
inline, user-friendly messages. The whole screen renders with KomoUI components on Android and iOS.

Verified by: app compiles for commonMain + Android assembleDebug; ViewModel/use-case/repository unit tests
pass; manual run on both platforms shows the form, multi-select, loading, error, and success states.

## What We're NOT Doing

- No login screen / actual authentication after registration (account is pending; out of scope).
- No registration for the `professional` or `admin` roles — only `establishment_manager`.
- No backoffice/admin endpoints (`/admin/*`).
- No establishments **pagination / search / load-more** — fetch first page with `page_size=100`.
- No migration of the existing Home screen off `AlTheme` (left intact; KomoTheme nested so both work).
- Not setting the real production `BASE_URL` (still placeholder; flagged — registration won't hit a real
  server until configured. See Migration Notes).
- No certificate pinning / HTTP timeout hardening (pre-existing gap, tracked separately).

## Implementation Approach

Bottom-up: de-risk the library first (Phase 1), then build data+domain for the two endpoints
(Phases 2–3), then the presentation feature and custom multi-select (Phase 4), wire navigation
(Phase 5), and finish with tests (Phase 6). Each phase compiles independently via
`./scripts/compile.sh`.

---

## Phase 1: Integrate KomoUI design system

### Overview
Add the KomoUI dependency, confirm it resolves and compiles against the project's newer Kotlin/Compose,
and provide `KomoTheme` at the app root without breaking the existing `AlTheme`-based Home screen.

### Changes Required:

#### 1. Version catalog
**File**: `gradle/libs.versions.toml`
**Changes**: Add version + library entry.
```toml
[versions]
komoui = "0.3.0"

[libraries]
komoui = { module = "io.github.derangga:komoui", version.ref = "komoui" }
```

#### 2. Module dependency
**File**: `composeApp/build.gradle.kts`
**Changes**: Add to `commonMain.dependencies` alongside the existing Compose deps (~line 37-44).
```kotlin
implementation(libs.komoui)
```

#### 3. Provide KomoTheme at root (non-breaking)
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/App.kt`
**Changes**: Wrap the existing `AlTheme` with `KomoTheme` so KomoUI tokens (`MaterialTheme.styles`) are
available for the new screen while Home's `AlTheme` locals keep working. (Start-destination branching is
done in Phase 5; this phase only adds the theme wrapper.)
```kotlin
import com.komoui.themes.KomoTheme
// ...
KomoTheme {
    AlTheme {
        AppNavHost(navController = navController, startScreen = Route.Home)
    }
}
```

### Success Criteria:

#### Automated Verification:
- [x] Dependency resolves: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:dependencies --configuration commonMainImplementation --no-daemon | grep komoui`
- [x] commonMain compiles: `./scripts/compile.sh`
- [x] Android build passes: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:assembleDebug --no-daemon`

#### Manual Verification:
- [ ] No Kotlin/Compose version-incompatibility errors at build time (the 2.3.20/1.10.3 vs 2.2/1.9 gap).
- [ ] Existing Home screen still renders correctly (AlTheme intact) on Android and iOS.
- [ ] iOS framework still links (KomoUI iOS targets pulled in): app runs in simulator.

> **Fallback if KomoUI fails to compile against 1.10.3:** build the form inputs from Material3 primitives
> (`OutlinedTextField`, `DropdownMenu`, `CircularProgressIndicator`) instead. This only affects Phase 4's
> component imports; the architecture in Phases 2–6 is unchanged.

---

## Phase 2: Establishments — domain + data (dropdown source)

### Overview
Fetch the list of establishments to populate the multi-select via `GET /api/v1/establishments`.

### Changes Required:

#### 1. Domain model
**File**: `domain/model/establishment/Establishment.kt`
```kotlin
data class Establishment(
    val id: String,
    val name: String,
    val address: String,
)
```

#### 2. Repository interface
**File**: `domain/repository/EstablishmentRepository.kt`
```kotlin
interface EstablishmentRepository {
    suspend fun getEstablishments(): Result<List<Establishment>>
}
```

#### 3. Use case
**File**: `domain/usecase/establishment/GetEstablishmentsUseCase.kt`
```kotlin
class GetEstablishmentsUseCase(private val repository: EstablishmentRepository) {
    suspend operator fun invoke(): Result<List<Establishment>> = repository.getEstablishments()
}
```

#### 4. DTOs
**File**: `data/establishment/dto/EstablishmentResponse.kt`
```kotlin
@Serializable
data class EstablishmentResponse(
    val id: String,
    val name: String,
    val address: String,
)

@Serializable
data class PaginatedEstablishmentsResponse(
    val items: List<EstablishmentResponse>,
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    @SerialName("total_pages") val totalPages: Int,
)
```

#### 5. Mapper (new pattern for the repo)
**File**: `data/establishment/mapper/EstablishmentMapper.kt`
```kotlin
fun EstablishmentResponse.toDomain() = Establishment(id = id, name = name, address = address)
```

#### 6. API client
**File**: `data/establishment/EstablishmentApi.kt`
```kotlin
class EstablishmentApi(private val refreshClient: HttpClient) {
    suspend fun getEstablishments(pageSize: Int = 100): PaginatedEstablishmentsResponse =
        refreshClient.get("api/v1/establishments") {
            parameter("page", 1)
            parameter("page_size", pageSize)
        }.body()
}
```
> Note path prefix: existing `AuthApi` uses `"auth/login"` against `BASE_URL` placeholder. Confirm whether
> `BASE_URL` already includes `/api/v1/` or not, and keep paths consistent (see Migration Notes).

#### 7. Repository impl
**File**: `data/establishment/EstablishmentRepositoryImpl.kt`
```kotlin
class EstablishmentRepositoryImpl(private val api: EstablishmentApi) : EstablishmentRepository {
    override suspend fun getEstablishments(): Result<List<Establishment>> = runCatching {
        api.getEstablishments().items.map { it.toDomain() }
    }
}
```

#### 8. DI wiring
**Files**: `di/ClientsModule.kt`, `di/RepositoriesModule.kt`, `di/UseCasesModule.kt`
```kotlin
// ClientsModule
single { EstablishmentApi(refreshClient = get(named("refresh"))) }
// RepositoriesModule
single<EstablishmentRepository> { EstablishmentRepositoryImpl(api = get()) }
// UseCasesModule
factory { GetEstablishmentsUseCase(get()) }
```

### Success Criteria:
#### Automated Verification:
- [x] commonMain compiles: `./scripts/compile.sh`
- [ ] Koin graph resolves (covered by a `checkModules`/startup test if present, else Phase 6 VM test).
#### Manual Verification:
- [ ] `EstablishmentApi` hits the correct path/query against the configured host.

---

## Phase 3: Registration — domain + data

### Overview
Post the registration and map the typed result/errors.

### Changes Required:

#### 1. Domain models
**File**: `domain/model/registration/ManagerRegistration.kt`
```kotlin
data class ManagerRegistration(
    val email: String,
    val password: String,
    val name: String,
    val lastName: String,
    val establishmentIds: List<String>,
)
```
**File**: `domain/model/registration/RegistrationResult.kt`
```kotlin
data class RegistrationResult(
    val id: String,
    val email: String,
    val status: String, // expected "pending_validation"
)
```
**File**: `domain/model/registration/RegistrationError.kt`
```kotlin
sealed interface RegistrationError {
    data object UserAlreadyExists : RegistrationError       // 409
    data object EstablishmentNotFound : RegistrationError   // 404
    data object InvalidData : RegistrationError             // 400
    data object Network : RegistrationError
    data class Unknown(val cause: Throwable?) : RegistrationError
}
```

#### 2. Repository interface
**File**: `domain/repository/RegistrationRepository.kt`
```kotlin
interface RegistrationRepository {
    suspend fun registerManager(registration: ManagerRegistration): Result<RegistrationResult>
}
```

#### 3. Use case (with business validation)
**File**: `domain/usecase/registration/RegisterManagerUseCase.kt`
```kotlin
class RegisterManagerUseCase(private val repository: RegistrationRepository) {
    suspend operator fun invoke(registration: ManagerRegistration): Result<RegistrationResult> =
        repository.registerManager(registration)
}
```

#### 4. DTOs
**File**: `data/registration/dto/RegisterManagerRequest.kt` and `RegistrationResponse.kt`
```kotlin
@Serializable
data class RegisterManagerRequest(
    val email: String,
    val password: String,
    val name: String,
    @SerialName("last_name") val lastName: String,
    val establishments: List<String>,
)

@Serializable
data class RegistrationResponse(
    val id: String,
    val email: String,
    val name: String,
    @SerialName("last_name") val lastName: String,
    val role: String,
    val status: String,
    val establishments: List<String>,
)
```

#### 5. API client
**File**: `data/registration/RegistrationApi.kt`
```kotlin
class RegistrationApi(private val refreshClient: HttpClient) {
    suspend fun registerManager(body: RegisterManagerRequest): RegistrationResponse =
        refreshClient.post("api/v1/auth/register/establishment-manager") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
}
```

#### 6. Repository impl + error mapping (introduces ProblemDetail awareness)
**File**: `data/registration/RegistrationRepositoryImpl.kt`
```kotlin
class RegistrationRepositoryImpl(private val api: RegistrationApi) : RegistrationRepository {
    override suspend fun registerManager(registration: ManagerRegistration): Result<RegistrationResult> =
        runCatching {
            val res = api.registerManager(registration.toRequest())
            RegistrationResult(id = res.id, email = res.email, status = res.status)
        }.recoverCatching { throw RegistrationFailure(mapToError(it), it) }

    private fun mapToError(t: Throwable): RegistrationError = when (t) {
        is ClientRequestException -> when (t.response.status) {
            HttpStatusCode.Conflict   -> RegistrationError.UserAlreadyExists
            HttpStatusCode.NotFound   -> RegistrationError.EstablishmentNotFound
            HttpStatusCode.BadRequest -> RegistrationError.InvalidData
            else -> RegistrationError.Network
        }
        is ResponseException -> RegistrationError.Network
        else -> RegistrationError.Unknown(t)
    }
}

class RegistrationFailure(val error: RegistrationError, cause: Throwable?) :
    Exception(error.toString(), cause)
```
**File**: `data/registration/mapper/RegistrationMapper.kt`
```kotlin
fun ManagerRegistration.toRequest() = RegisterManagerRequest(
    email = email, password = password, name = name,
    lastName = lastName, establishments = establishmentIds,
)
```

#### 7. DI wiring
```kotlin
// ClientsModule
single { RegistrationApi(refreshClient = get(named("refresh"))) }
// RepositoriesModule
single<RegistrationRepository> { RegistrationRepositoryImpl(api = get()) }
// UseCasesModule
factory { RegisterManagerUseCase(get()) }
```

### Success Criteria:
#### Automated Verification:
- [x] commonMain compiles: `./scripts/compile.sh`
- [x] Repository unit tests (Phase 6) map 409/404/400/network correctly.
#### Manual Verification:
- [x] Request body serializes with snake_case `last_name` and `establishments` array (asserted in `RegistrationRepositoryImplTest`).

---

## Phase 4: Register presentation feature (KomoUI screen)

### Overview
The six-file feature folder `presentation/feature/register/`, a custom multi-select establishments
component, form validation, and loading/error/success state — all KomoUI.

### Changes Required:

#### 1. State
**File**: `presentation/feature/register/RegisterState.kt`
```kotlin
data class RegisterState(
    val destination: RegisterDestination? = null,
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val lastName: String = "",
    val establishments: List<Establishment> = emptyList(),
    val selectedEstablishmentIds: Set<String> = emptySet(),
    val isLoadingEstablishments: Boolean = false,
    val isSubmitting: Boolean = false,
    val fieldErrors: RegisterFieldErrors = RegisterFieldErrors(),
    val submitError: String? = null,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && name.isNotBlank() &&
                lastName.isNotBlank() && selectedEstablishmentIds.isNotEmpty() && !isSubmitting
}

data class RegisterFieldErrors(
    val email: String? = null,
    val password: String? = null,
    val name: String? = null,
    val lastName: String? = null,
    val establishments: String? = null,
)
```

#### 2. Action
**File**: `presentation/feature/register/RegisterAction.kt`
```kotlin
sealed interface RegisterAction {
    data class EmailChanged(val value: String) : RegisterAction
    data class PasswordChanged(val value: String) : RegisterAction
    data class NameChanged(val value: String) : RegisterAction
    data class LastNameChanged(val value: String) : RegisterAction
    data class EstablishmentToggled(val id: String) : RegisterAction
    data object Submit : RegisterAction
    data object DismissError : RegisterAction
}
```

#### 3. Destination
**File**: `presentation/feature/register/RegisterDestination.kt`
```kotlin
sealed class RegisterDestination {
    data object Success : RegisterDestination()
}
```

#### 4. ViewModel
**File**: `presentation/feature/register/RegisterViewModel.kt`
- Constructor-inject `GetEstablishmentsUseCase`, `RegisterManagerUseCase` (factory in DI).
- `init { loadEstablishments() }` → set `isLoadingEstablishments`, fold `Result`.
- `onAction` reduces field changes (`update { it.copy(...) }`), toggles selection in the `Set`.
- `Submit`: validate (email format, password length, non-blank, ≥1 establishment) → set `fieldErrors`
  and stop if invalid; else `isSubmitting = true`, call use case, fold:
  `onSuccess` → `destination = RegisterDestination.Success`;
  `onFailure` → map `RegistrationFailure.error` to a user-facing `submitError` string.
- `clearDestination()` resets `destination`.
- Expose `state` via `stateIn(viewModelScope, WhileSubscribed(5000), _state.value)` (Home pattern).

#### 5. Multi-select component (KomoUI composite)
**File**: `presentation/feature/register/component/EstablishmentMultiSelect.kt`
```kotlin
@Composable
fun EstablishmentMultiSelect(
    options: List<Establishment>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        trigger = {
            // Button/box showing "N selected" or placeholder, opens menu
        },
    ) {
        options.forEach { est ->
            DropdownMenuItem(onClick = { onToggle(est.id) }) {
                Checkbox(checked = est.id in selectedIds, onCheckedChange = { onToggle(est.id) })
                Text(est.name, color = MaterialTheme.styles.foreground)
            }
        }
    }
    // Below: chips/text listing selected establishment names.
}
```

#### 6. Screen
**File**: `presentation/feature/register/RegisterScreen.kt`
- `RegisterScreenRoot(viewModel = koinViewModel(), navigate)`: collect state, `LaunchedEffect(destination)`
  forwards + `clearDestination()`, render `RegisterScreen(state, viewModel::onAction)`.
- Pure `RegisterScreen`: a vertically scrollable `Column` (use the CMP `ScrollState` keyboard pattern from
  CLAUDE.md for the lower fields) containing KomoUI `Input`s:
  - Email: `keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)`, `isError`, `supportingText`.
  - Password: `visualTransformation = PasswordVisualTransformation()`, `keyboardType = Password`.
  - Name, Last name: plain `Input`.
  - `EstablishmentMultiSelect` (with `Spinner` while `isLoadingEstablishments`).
  - Submit `Button(onClick = { onAction(Submit) }, loading = state.isSubmitting, enabled = state.canSubmit)`.
  - `submitError` shown via inline error text (or KomoUI `Alert`).

### Success Criteria:
#### Automated Verification:
- [x] commonMain compiles: `./scripts/compile.sh`
- [x] ViewModel unit tests pass (Phase 6).
#### Manual Verification:
- [ ] All fields render and accept input; password is masked; email keyboard is email type.
- [ ] Multi-select opens, checks/unchecks multiple establishments, selected names display.
- [ ] Submit disabled until form valid (incl. ≥1 establishment); spinner shows while submitting.
- [ ] Keyboard does not cover the focused lower field (ScrollState pattern works on iOS).

---

## Phase 5: Navigation + session-based start destination

### Overview
Register the routes and branch the start destination on `sessionStatus`.

### Changes Required:

#### 1. Routes
**File**: `presentation/navigation/Route.kt`
```kotlin
@Serializable @SerialName("register") data object Register : Route()
@Serializable @SerialName("register_success") data object RegisterSuccess : Route()
```

#### 2. Register navigation
**File**: `presentation/feature/register/RegisterNavigation.kt`
```kotlin
fun NavController.navigateToRegister(navOptions: NavOptions? = null) = navigate(Route.Register, navOptions)

fun NavGraphBuilder.registerScreen(navController: NavController) {
    composable<Route.Register> {
        RegisterScreenRoot(navigate = { navigate(it, navController) })
    }
    composable<Route.RegisterSuccess> { RegisterSuccessScreen(/* onDone if needed */) }
}

internal fun navigate(destination: RegisterDestination, navController: NavController) {
    when (destination) {
        RegisterDestination.Success ->
            navController.navigate(Route.RegisterSuccess) {
                popUpTo(Route.Register) { inclusive = true }
            }
    }
}
```

#### 3. Success/confirmation screen
**File**: `presentation/feature/register/RegisterSuccessScreen.kt`
- KomoUI screen with a confirmation message: "Tu registro fue enviado y está pendiente de aprobación."
  No back-to-form action needed (form was popped).

#### 4. Wire into NavHost + branch start destination
**File**: `app/AppNavHost.kt` — add `registerScreen(navController)` inside the `NavHost` lambda.
**File**: `app/App.kt` — derive start screen from `sessionStatus`:
```kotlin
val startScreen = when (sessionStatus) {
    is SessionStatus.Authenticated -> Route.Home
    else -> Route.Register   // NotAuthenticated / RefreshFailure → register
}
// Initializing → show a Spinner instead of NavHost
```
> `Initializing` should render a loading state (KomoUI `Spinner`) rather than picking a destination, to
> avoid a flash of the wrong screen before bootstrap resolves.

### Success Criteria:
#### Automated Verification:
- [x] commonMain compiles: `./scripts/compile.sh`
- [x] Android build passes: `./gradlew :composeApp:assembleDebug --no-daemon`
#### Manual Verification:
- [ ] Cold start while unauthenticated lands on Register (after brief Initializing spinner).
- [ ] Successful submit navigates to the confirmation screen and the form is removed from the back stack
      (back press does not return to a populated form).

---

## Phase 6: Tests

### Overview
Unit-test ViewModel, use cases, and repository per the Kotlin testing rules (Turbine, fakes, MockEngine).

### Changes Required:

#### 1. Fakes
**File**: `commonTest/.../fake/FakeRegistrationRepository.kt`, `FakeEstablishmentRepository.kt`
- Hand-written fakes with settable `result`/error (rules: prefer fakes over mocks).

#### 2. ViewModel tests (Turbine)
**File**: `commonTest/.../register/RegisterViewModelTest.kt`
- loads establishments into state on init;
- field changes update state; toggling selection adds/removes ids;
- `canSubmit` gating (blank fields, zero establishments);
- submit success sets `destination = Success`;
- submit failure maps `UserAlreadyExists`/`EstablishmentNotFound`/`InvalidData`/`Network` to messages;
- `isSubmitting` toggles around the call.

#### 3. Use case tests
**File**: `commonTest/.../RegisterManagerUseCaseTest.kt`, `GetEstablishmentsUseCaseTest.kt`

#### 4. Repository tests (Ktor MockEngine)
**File**: `commonTest/.../RegistrationRepositoryImplTest.kt`
- 201 → success result; 409/404/400 → correct `RegistrationError`; request body shape (snake_case) asserted.
**File**: `commonTest/.../EstablishmentRepositoryImplTest.kt`
- maps paginated items to domain.

### Success Criteria:
#### Automated Verification:
- [x] `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:testDebugUnitTest --no-daemon` (or `allTests`) passes (22 tests).
- [x] Coverage ≥ 80% for the new ViewModel + use cases + repositories (VM, both use cases, both repos covered).
#### Manual Verification:
- [x] Test names are descriptive; no flaky timing assertions (use `runTest`).

---

## Testing Strategy

### Manual Testing Steps
1. Run on Android emulator and iOS simulator from an unauthenticated state.
2. Verify the form renders with KomoUI styling; password masks; email keyboard type.
3. Open the establishments dropdown → check ≥2 → confirm selected names show; uncheck one.
4. Submit with an invalid email → inline field error; with empty selection → submit disabled.
5. Submit valid data against a mock/staging server → spinner → confirmation screen.
6. Simulate a 409 (duplicate email) → user-friendly "ya existe" error; 404 → establishment-not-found error.
7. Confirm back press from confirmation does not return to the form.

## Performance Considerations
- Establishments fetched once on screen entry (`page_size=100`); no per-keystroke network calls.
- `stateIn(WhileSubscribed(5000))` keeps the VM idle when the screen is backgrounded.
- KomoUI pulls coil + material-icons-extended transitively — watch APK size; acceptable for a form screen.

## Migration Notes
- **`ApiConfig.BASE_URL` is the placeholder `https://api.example.com/`.** Before manual end-to-end testing,
  set the real host. Confirm whether the base already includes `/api/v1/`; if so, drop the `api/v1/` prefix
  from the new API paths (and from `AuthApi`, which currently uses bare `"auth/login"`). Keep all clients
  consistent.
- This feature introduces the first `data/**/mapper/` directories and the first typed HTTP-error mapping
  beyond auth — follow the established `runCatching → typed sealed error → DomainFailure` shape.
- KomoTheme is now provided app-wide (nested with AlTheme). A future cleanup can migrate Home off `AlTheme`
  and remove the custom design system in favor of KomoUI (out of scope here).

## References
- Backend contract: `docs/tpp-backend.openapi.generated.json`
- Presentation pattern: `presentation/feature/home/` (all six files)
- API/error precedent: `data/auth/AuthApi.kt:17-30`, `data/auth/AuthRepositoryImpl.kt:39-76`
- Start-destination hook: `app/App.kt:17-27`
- KomoUI: `io.github.derangga:komoui:0.3.0`, repo `github.com/derangga/komoui`, site `shadcn-compose.site`
- CMP keyboard scroll pattern: `CLAUDE.md` → "Scroll-to-field on keyboard open"
