# ResetPassword Screen Implementation Plan

## Overview

Add a `ResetPassword` feature that receives a one-time `token` from a magic link, lets the user set a new password (with confirmation), submits it to the backend, and routes the user to a success screen. The screen is reachable via the custom-scheme deeplink `ingenieriaalimentacion://reset-password?token={token}` so that magic-link emails can land directly in the app on Android.

## Current State Analysis

- No `Login`, `ForgotPassword`, or `ResetPassword` screen exists. The closest reference is `presentation/feature/register/` (`RegisterScreen.kt`, etc.), which has the same field/IME/validation patterns we need.
- No deeplink configuration exists anywhere — `AndroidManifest.xml` only declares the default `MAIN`/`LAUNCHER` intent-filter; no `Route` uses `navDeepLink`; `MainActivity` has no `onNewIntent` override; iOS has no `CFBundleURLTypes` or `.onOpenURL` wiring.
- The backend OpenAPI contract (`docs/backend.openapi.generated.json`) currently exposes only `auth/login`, `auth/refresh`, `auth/register/establishment-manager`, plus admin / establishment / subject endpoints. **There is no `POST /auth/reset-password` endpoint yet** — this is a prerequisite that must land before the FE can be exercised end-to-end.
- `Route.kt` uses type-safe `@Serializable` destinations. `RegisterViewModel`, `AuthRepositoryImpl`, and `AuthApi` are the patterns to mirror for the data/domain layer.
- The Compose `AppNavHost` `startDestination` is gated by `SessionStatus` in `App.kt` — but Navigation Compose's deeplink handler bypasses `startDestination` when a matching deeplink fires on activity launch.

### Key Discoveries
- Feature scaffold pattern documented in `.claude/skills/feature-screen-generator/SKILL.md` — six files per feature, plus three integration files (`Route.kt`, `ViewModelsModule.kt`, `AppNavHost.kt`).
- `RegisterScreen.kt:80-212` — reference for title/subtitle + form fields + scroll-into-view-on-focus pattern (uses `ScrollState`, not `BringIntoViewRequester`, per `CLAUDE.md`).
- `RegisterViewModel.kt:68-112` — reference for submit + validation + error-to-message mapping.
- `AuthApi.kt:17-30` — reference for unauthenticated `auth/*` endpoints; reset password is unauthenticated and must use the `"refresh"` Koin-qualified client (no `Authorization` header).
- `AuthRepositoryImpl.kt:39-69` — `AuthFailure(error: AuthError, cause)` is the canonical FE-facing error wrapper; we will extend `AuthError` rather than introduce a parallel `ResetPasswordError`.
- `AndroidManifest.xml:12-21` — `MainActivity` already has `launchMode="singleTask"`, which is what we want for deeplinks (single instance, `onNewIntent` fires for warm-start deeplinks).

## Desired End State

A user receives a magic-link email containing `ingenieriaalimentacion://reset-password?token=<jwt>`. On Android, tapping the link opens the app directly into the `ResetPassword` screen. The user enters a new password (twice), submits, and lands on a `ResetPasswordSuccess` screen with a button back to `GetStarted`. Any existing session is invalidated on success so the user must sign in with the new password.

**Verification**: cold-starting the activity with `adb shell am start -W -a android.intent.action.VIEW -d "ingenieriaalimentacion://reset-password?token=test123" ar.com.ingenieriaalimentacion` lands directly on the ResetPassword screen. Submitting the form calls `POST /api/v1/auth/reset-password` with `{ token, password }`.

## What We're NOT Doing

- **iOS deeplink wiring.** No `CFBundleURLTypes`, no `.onOpenURL`, no Universal Links / Associated Domains. The screen itself will work on iOS via in-app navigation (`navController.navigate(Route.ResetPassword(token))`) but the OS-level magic-link redirect is Android-only for this plan.
- **HTTPS App Links / Universal Links.** Custom scheme only — no `assetlinks.json`, no domain ownership setup.
- **Forgot-password request flow.** This plan covers only step 2 (consuming the token); step 1 (the user enters their email, backend emails the magic link) is out of scope.
- **Backend implementation of `POST /api/v1/auth/reset-password`.** The FE will be wired against the endpoint signature documented below, but adding the endpoint to FastAPI is a separate backend ticket.
- **Login screen.** Already absent and noted in `thoughts/plans/auth_setup.md` as deferred. Success routes back to `GetStarted` (which itself will eventually route to a Login flow when it exists).
- **Password-strength meter, hide/show toggle, "remember device" affordances.** Keep parity with `RegisterScreen`'s plain `PasswordVisualTransformation` field.

## Implementation Approach

Five phases, each independently verifiable. We work bottom-up from domain → data → presentation → DI/nav → Android deeplink config, mirroring the layering used for the Register feature. The `feature-screen-generator` skill is used in Phase 3 to scaffold the six presentation files; the other phases are hand-written because they touch shared infrastructure the skill does not modify.

---

## Phase 1: Domain layer

### Overview
Add the domain model, error variant, repository contract extension, and use case for resetting a password with a token.

### Changes Required:

#### 1. Extend `AuthError` with reset-password-specific variants
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/model/auth/AuthError.kt`
**Changes**: Add `InvalidOrExpiredToken` and `WeakPassword` variants. Keep `Network` and `Unknown` for shared use.

```kotlin
sealed interface AuthError {
    data object InvalidCredentials : AuthError
    data object InvalidOrExpiredToken : AuthError
    data object WeakPassword : AuthError
    data object Network : AuthError
    data class Unknown(val cause: Throwable? = null) : AuthError
}
```

#### 2. Extend `AuthRepository` with the reset method
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/repository/AuthRepository.kt`
**Changes**: Add `suspend fun resetPassword(token: String, newPassword: String): Result<Unit>`.

```kotlin
interface AuthRepository {
    val sessionStatus: StateFlow<SessionStatus>
    suspend fun bootstrap()
    suspend fun signIn(credentials: Credentials): Result<UserSession>
    suspend fun signOut()
    suspend fun resetPassword(token: String, newPassword: String): Result<Unit>
}
```

#### 3. Add `ResetPasswordUseCase`
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/usecase/auth/ResetPasswordUseCase.kt`
**Changes**: New file. Trivial wrapper around the repo; no business logic.

```kotlin
package ar.com.ingenieriaalimentacion.domain.usecase.auth

import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository

class ResetPasswordUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(token: String, newPassword: String): Result<Unit> =
        repository.resetPassword(token, newPassword)
}
```

### Success Criteria:

#### Automated Verification:
- [x] Kotlin compilation passes: `./scripts/compile.sh`

#### Manual Verification:
- [ ] N/A (no runtime changes yet)

---

## Phase 2: Data layer

### Overview
Wire the DTO, the unauthenticated API call (against the refresh `HttpClient`), and the repository implementation. Map server failures into the typed `AuthError` variants from Phase 1.

### Changes Required:

#### 1. Add `ResetPasswordRequest` DTO
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/dto/ResetPasswordRequest.kt`
**Changes**: New file mirroring `LoginRequest.kt`.

```kotlin
package ar.com.ingenieriaalimentacion.data.auth.dto

import kotlinx.serialization.Serializable

@Serializable
data class ResetPasswordRequest(
    val token: String,
    val password: String,
)
```

#### 2. Add `resetPassword` to `AuthApi`
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/AuthApi.kt`
**Changes**: New method on the existing class, posting to `auth/reset-password` on the no-auth `refreshClient`. Expects HTTP 204 / empty body on success.

```kotlin
suspend fun resetPassword(token: String, newPassword: String) {
    refreshClient.post("auth/reset-password") {
        contentType(ContentType.Application.Json)
        setBody(ResetPasswordRequest(token = token, password = newPassword))
    }
}
```

Note: backend prefix `/api/v1/` is configured on the client's `defaultRequest` (per `HttpClientFactory`); we use the relative path `auth/reset-password` to match how `auth/login` is called.

#### 3. Implement `resetPassword` in `AuthRepositoryImpl`
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/AuthRepositoryImpl.kt`
**Changes**: New override that delegates to `api.resetPassword`, maps failures, and on success calls `signOut()` so any stale session is purged (the user must re-authenticate with the new password).

```kotlin
override suspend fun resetPassword(token: String, newPassword: String): Result<Unit> = runCatching {
    api.resetPassword(token, newPassword)
    // Force a clean re-login with the new password — and clear any stale session
    // (this is safe whether or not a session exists).
    signOut()
}.recoverCatching { throwable ->
    throw AuthFailure(mapResetError(throwable), throwable)
}

private fun mapResetError(throwable: Throwable): AuthError = when {
    throwable is ResponseException && throwable.response.status == HttpStatusCode.BadRequest ->
        AuthError.WeakPassword
    throwable is ResponseException &&
        (throwable.response.status == HttpStatusCode.Unauthorized ||
         throwable.response.status == HttpStatusCode.Gone ||
         throwable.response.status == HttpStatusCode.NotFound) ->
        AuthError.InvalidOrExpiredToken
    throwable is ResponseException -> AuthError.Network
    else -> AuthError.Unknown(throwable)
}
```

**Backend contract assumed** (documented here so the backend ticket can match it):
- `POST /api/v1/auth/reset-password`
- Body: `{ "token": string, "password": string }`
- 204 No Content on success
- 400 → invalid password (e.g., too short, fails policy)
- 401 / 404 / 410 → invalid or expired token
- 5xx → network/unknown

### Success Criteria:

#### Automated Verification:
- [x] Kotlin compilation passes: `./scripts/compile.sh`

#### Manual Verification:
- [ ] N/A until Phase 3 wires the screen

---

## Phase 3: Presentation feature (via `feature-screen-generator`)

### Overview
Scaffold the six MVVM files for the `ResetPassword` feature using the project's `feature-screen-generator` skill, then customize them with the title/subtitle, two password inputs, validation, submit handler, and success destination.

### Scaffold parameters
- **Feature Name**: `ResetPassword`
- **Feature path**: `presentation/feature/auth/reset_password/`
  - Use the `auth/` group folder (created in this phase) so future `Login`, `ForgotPassword`, etc. can sit next to it. This is a small departure from `register/` and `get_started/` living at the feature root — flag it during review if reviewer prefers flat structure.
- **Has Navigation Parameters?**: Yes — `token: String` (received from the deeplink query string).
- **Key User Actions**: `NewPasswordChanged`, `ConfirmPasswordChanged`, `TogglePasswordVisibility`, `Submit`, `DismissError`.
- **Navigation Destinations**: `Success` (→ `ResetPasswordSuccess` screen), `Back` (currently a no-op on cold-start deeplink; reserved for in-app navigation cases).

### Changes Required:

#### 1. Run the skill
Invoke `feature-screen-generator` with the parameters above. It will create the six files under `presentation/feature/auth/reset_password/` and propose edits to `Route.kt` / `ViewModelsModule.kt` / `AppNavHost.kt`. Apply the file generation; Phase 4 covers the integration-file edits in detail (we will reconcile the skill's proposed edits with the specifics below).

#### 2. `ResetPasswordAction.kt` — customize the generated stub

```kotlin
sealed interface ResetPasswordAction {
    data class NewPasswordChanged(val value: String) : ResetPasswordAction
    data class ConfirmPasswordChanged(val value: String) : ResetPasswordAction
    data object TogglePasswordVisibility : ResetPasswordAction
    data object Submit : ResetPasswordAction
    data object DismissError : ResetPasswordAction
}
```

#### 3. `ResetPasswordDestination.kt`

```kotlin
sealed class ResetPasswordDestination {
    data object Success : ResetPasswordDestination()
}
```

#### 4. `ResetPasswordState.kt`

```kotlin
data class ResetPasswordState(
    val destination: ResetPasswordDestination? = null,
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val fieldErrors: ResetPasswordFieldErrors = ResetPasswordFieldErrors(),
    val submitError: String? = null,
) {
    val canSubmit: Boolean
        get() = newPassword.isNotBlank() && confirmPassword.isNotBlank() && !isSubmitting
}

data class ResetPasswordFieldErrors(
    val newPassword: String? = null,
    val confirmPassword: String? = null,
)
```

#### 5. `ResetPasswordViewModel.kt`
Mirror `RegisterViewModel.kt` exactly for the StateFlow / `onAction` / validation / error-mapping shape.

```kotlin
class ResetPasswordViewModel(
    private val route: Route.ResetPassword,
    private val resetPassword: ResetPasswordUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ResetPasswordState())
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    fun onAction(action: ResetPasswordAction) { /* dispatch as in RegisterViewModel */ }
    fun clearDestination() { _state.update { it.copy(destination = null) } }

    private fun submit() {
        val current = _state.value
        val errors = validate(current)
        if (errors != ResetPasswordFieldErrors()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }
        _state.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            resetPassword(token = route.token, newPassword = current.newPassword).fold(
                onSuccess = {
                    _state.update { it.copy(isSubmitting = false, destination = ResetPasswordDestination.Success) }
                },
                onFailure = { throwable ->
                    _state.update { it.copy(isSubmitting = false, submitError = throwable.toUserMessage()) }
                },
            )
        }
    }

    private fun validate(s: ResetPasswordState): ResetPasswordFieldErrors = ResetPasswordFieldErrors(
        newPassword = when {
            s.newPassword.isBlank() -> "Ingresá una contraseña."
            s.newPassword.length < MIN_PASSWORD_LENGTH -> "La contraseña debe tener al menos $MIN_PASSWORD_LENGTH caracteres."
            else -> null
        },
        confirmPassword = when {
            s.confirmPassword.isBlank() -> "Repetí la contraseña."
            s.confirmPassword != s.newPassword -> "Las contraseñas no coinciden."
            else -> null
        },
    )

    private fun Throwable.toUserMessage(): String {
        val error = (this as? AuthFailure)?.error
        return when (error) {
            AuthError.InvalidOrExpiredToken -> "El enlace expiró o ya fue utilizado. Pedí uno nuevo."
            AuthError.WeakPassword -> "La contraseña no cumple los requisitos mínimos."
            AuthError.Network -> "No pudimos conectarnos. Verificá tu conexión e intentá nuevamente."
            is AuthError.Unknown, null -> "Ocurrió un error inesperado. Intentá nuevamente."
            AuthError.InvalidCredentials -> "Ocurrió un error inesperado. Intentá nuevamente."
        }
    }

    private companion object { const val MIN_PASSWORD_LENGTH = 8 }
}
```

#### 6. `ResetPasswordScreen.kt`
Two-composable pattern from `RegisterScreen`: `ResetPasswordScreenRoot` (collects state, observes destination, delegates navigation) + stateless `ResetPasswordScreen`. UI is title + subtitle + two `Input` fields (`PasswordVisualTransformation`) + submit `Button`, all wrapped in the same `verticalScroll(scrollState)` + IME-aware focus-scroll pattern from `RegisterScreen.kt:80-212`. Reuse the private `RegisterField` shape — extract a shared helper later if it grows.

Copy text:
- Title: `"Nueva contraseña"` (`headlineMedium`)
- Subtitle: `"Ingresá una nueva contraseña para tu cuenta. Una vez confirmada, vas a poder ingresar con ella."` (`bodyMedium`, muted)
- Field 1 label: `"Nueva contraseña"`, placeholder `"Mínimo 8 caracteres"`
- Field 2 label: `"Repetir contraseña"`, placeholder `"Volvé a ingresarla"`
- Submit button: `"Guardar contraseña"`

#### 7. `ResetPasswordSuccessScreen.kt`
Mirror `RegisterSuccessScreen.kt:18-41` exactly, with:
- Heading: `"¡Contraseña actualizada!"`
- Body: `"Tu contraseña fue actualizada. Iniciá sesión con tus nuevas credenciales."`
- Plus an `AlButton` "Volver al inicio" that calls back to the navigator. (RegisterSuccessScreen has no button today; we add one here because there's a clear next step.)

#### 8. `ResetPasswordNavigation.kt`
Defines the `composable<Route.ResetPassword>` block, attaches the `navDeepLink` for the custom-scheme URI, and renders both screens.

```kotlin
fun NavController.navigateToResetPassword(token: String, navOptions: NavOptions? = null) =
    navigate(Route.ResetPassword(token), navOptions)

fun NavGraphBuilder.resetPasswordScreen(navController: NavController) {
    composable<Route.ResetPassword>(
        deepLinks = listOf(
            navDeepLink { uriPattern = "ingenieriaalimentacion://reset-password?token={token}" }
        ),
    ) {
        ResetPasswordScreenRoot(
            navigate = { destination -> navigate(destination, navController) },
        )
    }
    composable<Route.ResetPasswordSuccess> {
        ResetPasswordSuccessScreen(
            onContinue = {
                navController.navigate(Route.GetStarted) {
                    popUpTo(0) { inclusive = true } // clear back stack
                }
            },
        )
    }
}

internal fun navigate(destination: ResetPasswordDestination, navController: NavController) {
    when (destination) {
        ResetPasswordDestination.Success -> navController.navigate(Route.ResetPasswordSuccess) {
            popUpTo(Route.ResetPassword::class) { inclusive = true }
        }
    }
}
```

### Success Criteria:

#### Automated Verification:
- [x] All six new files compile: `./scripts/compile.sh`
- [x] No unresolved references to `AuthFailure`, `AuthError`, `ResetPasswordUseCase`, `Route.ResetPassword`, `Route.ResetPasswordSuccess`

#### Manual Verification:
- [ ] In-app navigation `navController.navigate(Route.ResetPassword(token = "test"))` from a debug entry point renders the screen with both inputs and the title/subtitle
- [ ] Form-level validation triggers correctly (empty / short / mismatched passwords)
- [ ] Field error messages clear when the user edits the offending field
- [ ] Pressing Submit with valid inputs shows the loading state on the button
- [ ] On a 401/404/410 response, the screen shows `"El enlace expiró o ya fue utilizado…"`
- [ ] After success the user lands on `ResetPasswordSuccess`, then tapping "Volver al inicio" returns to `GetStarted` with an empty back stack

---

## Phase 4: Navigation, route, and DI wiring

### Overview
Register the two new routes, the ViewModel binding, the use case, and the screen in the nav graph.

### Changes Required:

#### 1. `Route.kt`
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/navigation/Route.kt`

```kotlin
@Serializable
@SerialName("reset_password")
data class ResetPassword(val token: String) : Route()

@Serializable
@SerialName("reset_password_success")
data object ResetPasswordSuccess : Route()
```

#### 2. `UseCasesModule.kt`
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/UseCasesModule.kt`

```kotlin
factory { ResetPasswordUseCase(get()) }
```

#### 3. `ViewModelsModule.kt`
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/ViewModelsModule.kt`

```kotlin
viewModel { (route: Route.ResetPassword) ->
    ResetPasswordViewModel(route = route, resetPassword = get())
}
```

(Per the feature-screen-generator skill convention: pass the `Route` object directly, not individual params.)

#### 4. `AppNavHost.kt`
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/AppNavHost.kt`

```kotlin
import ar.com.ingenieriaalimentacion.presentation.feature.auth.reset_password.resetPasswordScreen
// ...
resetPasswordScreen(navController)
```

### Success Criteria:

#### Automated Verification:
- [x] Kotlin compilation passes: `./scripts/compile.sh`
- [x] Android assemble passes: `./gradlew :composeApp:assembleDebug --no-daemon`

#### Manual Verification:
- [ ] DI graph resolves at startup (no Koin definition-missing crashes)
- [ ] The screen can be reached by manual in-app navigation before any deeplink work is wired

---

## Phase 5: Android deeplink — manifest + activity bridge

### Overview
Configure the Android side so that `ingenieriaalimentacion://reset-password?token=<jwt>` (a) routes to `MainActivity` instead of opening a browser, and (b) hands the intent to the `NavController` for both cold and warm starts.

### Changes Required:

#### 1. Add the intent-filter to `AndroidManifest.xml`
**File**: `composeApp/src/androidMain/AndroidManifest.xml`
**Changes**: Add a second `<intent-filter>` inside the existing `<activity>` block. Keep `singleTask` launchMode (already set).

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data
        android:scheme="ingenieriaalimentacion"
        android:host="reset-password" />
</intent-filter>
```

Notes:
- The `<data>` block uses `host="reset-password"` (not `path`) because the URI form is `ingenieriaalimentacion://reset-password?token=…` — the path component is empty and the resource name lives in the host. This matches the `uriPattern` declared in `ResetPasswordNavigation.kt`.
- No `autoVerify` flag — that is for App Links over HTTPS, not custom schemes.

#### 2. Forward warm-start deeplinks from `MainActivity`
**File**: `composeApp/src/androidMain/kotlin/ar/com/ingenieriaalimentacion/MainActivity.kt`
**Changes**: Lift the `NavHostController` reference into the activity so `onNewIntent` can call `handleDeepLink` for it. Cold-start deeplinks are handled automatically by `NavHost` when the controller is first composed.

```kotlin
class MainActivity : ComponentActivity() {

    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val controller = rememberNavController()
            SideEffect { navController = controller }
            App(navController = controller)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        navController?.handleDeepLink(intent)
    }
}
```

`App.kt` already accepts `navController: NavHostController = rememberNavController()` as a parameter (`App.kt:25`) — no changes needed there. The default still works for tests / iOS.

### Success Criteria:

#### Automated Verification:
- [x] Android assemble passes: `./gradlew :composeApp:assembleDebug --no-daemon`
- [x] Manifest merger does not warn about duplicate / malformed intent-filters

#### Manual Verification:
- [ ] **Cold start** — with the app killed, run
  `adb shell am start -W -a android.intent.action.VIEW -d "ingenieriaalimentacion://reset-password?token=test-cold" ar.com.ingenieriaalimentacion`
  → app launches directly into ResetPassword screen, `route.token == "test-cold"`.
- [ ] **Warm start** — with the app open on `Home` or `GetStarted`, run the same `adb` command → app moves to ResetPassword without restarting.
- [ ] **Already on ResetPassword** — re-firing the deeplink with a different token updates the token (or re-creates the destination cleanly; either behavior is acceptable for v1).
- [ ] Tapping an `ingenieriaalimentacion://reset-password?token=…` link in a system email/notes app opens the app (not the browser).
- [ ] The link does **not** match if a different scheme or host is used (e.g., `https://...` or `ingenieriaalimentacion://something-else`).

---

## Testing Strategy

### Unit tests (commonTest)
Following the project's pattern from `RegisterViewModelTest.kt`, add `ResetPasswordViewModelTest`:
- Submit with empty / short / mismatched passwords → emits `fieldErrors`, no use-case call.
- Submit with valid inputs → use case invoked with the route's token, success transitions destination to `Success`.
- Use case returns `AuthFailure(InvalidOrExpiredToken)` → `submitError` set to the expired-link message.
- Use case returns `AuthFailure(WeakPassword)` → `submitError` set to the weak-password message.
- `DismissError` clears `submitError`.

Use a `FakeResetPasswordUseCase` (or `FakeAuthRepository`) per the project's fakes-over-mocks convention (`rules/kotlin/testing.md`).

### Manual deeplink testing
See Phase 5 manual checks. Capture an `adb logcat` snippet for the cold-start case as evidence in the PR description.

## Performance Considerations

None of note — single form screen, single suspending network call, no streaming or pagination. The deeplink path adds one extra intent-filter to the manifest, which has negligible cold-start cost.

## Migration Notes

Backend prerequisite: `POST /api/v1/auth/reset-password` must be implemented and merged before this feature is enabled end-to-end. The FE will build and ship without it, but submissions will 404 until the backend lands. Coordinate the merge order with backend.

## References

- Closest reference feature (auth-style form with validation, IME handling): `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/register/`
- Closest reference for "success" screen: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/register/RegisterSuccessScreen.kt`
- Skill that scaffolds the six feature files: `.claude/skills/feature-screen-generator/SKILL.md`
- Earlier auth context and explicit deferral of forgot-password UI: `thoughts/plans/auth_setup.md:58`
- Backend contract (currently missing the reset-password endpoint): `docs/backend.openapi.generated.json`
- Project conventions (CMP, MVVM file split, IME scroll pattern): `CLAUDE.md`
