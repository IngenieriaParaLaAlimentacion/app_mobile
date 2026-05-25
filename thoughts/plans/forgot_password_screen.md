# Forgot Password Screen Implementation Plan

## Overview

Add a `ForgotPassword` feature so a user can request a password-recovery email by entering their address. The screen calls `POST /auth/password-recovery/request`, then renders an inline "check your email" confirmation in place of the form (no separate success route). The plan also fixes three contract mismatches in the existing `ResetPassword` flow so both sides of the recovery flow line up with the backend.

The full password-recovery flow once this lands:

1. **(this plan)** User opens `ForgotPasswordScreen`, enters email, submits → `POST /auth/password-recovery/request` → 202 → inline confirmation rendered.
2. Backend emails a deeplink `…/reset-password?token=<raw>`. (Host not yet defined backend-side; the mobile deeplink scheme `ingenieriaalimentacion://reset-password?token=…` is already wired in `ResetPasswordNavigation.kt:17`.)
3. **(already implemented)** Tapping the link opens `ResetPasswordScreen` (`Route.ResetPassword(token)`), user sets a new password, submits → `POST /auth/password-recovery/confirm` → 204 → `ResetPasswordSuccess` → `GetStarted`.

No entry point to `ForgotPasswordScreen` is wired in this plan — `GetStartedScreen`'s "Iniciar sesión" is still disabled and no Login screen exists. The entry-point link will be added alongside the future Login screen.

## Current State Analysis

- `presentation/feature/auth/reset_password/` is fully implemented (six MVVM files + success screen) and consumes the token via deeplink. Reference for the new feature's shape.
- `AuthApi.kt:32-37` posts to `auth/reset-password`; the backend exposes the equivalent at `auth/password-recovery/confirm`. **Mismatch.**
- `ResetPasswordRequest.kt:6-9` serializes the new password under the key `password`; the backend expects `new_password`. **Mismatch.**
- `AuthRepositoryImpl.kt:80-90` (`mapResetError`) maps `400 → WeakPassword` and `404/410 → InvalidOrExpiredToken`. The backend confirm endpoint documents only `204` on success and `401 InvalidTokenError` on missing/invalid/expired token. The `400/404/410` branches are unreachable in production. **Over-broad mapping.**
- No `requestPasswordRecovery` exists on `AuthRepository`, `AuthApi`, or as a use case.
- `Route.kt:7-39` is a sealed `Route` class — new routes must be `@Serializable` + `@SerialName`. No `ForgotPassword` route exists.
- `AppNavHost.kt:19-29` lists every feature explicitly — new feature must be wired here.
- `GetStartedScreen.kt:84-94` has a disabled "Iniciar sesión" label (`isLoginEnabled = false`). No "¿Olvidaste tu contraseña?" affordance anywhere.

### Key Discoveries

- The two unauthenticated `auth/*` calls (`signIn`, `resetPassword`) both go through the no-auth `refreshClient` (`AuthApi.kt:18`). The new `requestPasswordRecovery` must do the same.
- `AuthFailure(error: AuthError, cause)` (`AuthRepositoryImpl.kt:97`) is the canonical FE-facing wrapper. Extend it by reusing existing `AuthError` variants rather than adding new ones — backend silent-on-not-found means the request endpoint has no caller-visible failure modes other than `Network` / `Unknown`.
- `reset_password/` lives under `presentation/feature/auth/` — the new `forgot_password/` folder goes there too for symmetry.
- The `reset_password` screen uses komoui (`com.komoui.components.Button`, `Input`, `themes.styles`). New screens like `get_started/` use the in-house `AlTheme` / `AlButton`. Since `ForgotPassword` is part of the same password-recovery flow as `ResetPassword`, this plan matches komoui to keep the flow visually consistent.
- The `feature-screen-generator` skill exists and produces the six MVVM files. It does **not** know how to render inline-success-in-place-of-form, so the `State` / `Screen` files will be customized after scaffolding.

## Desired End State

1. `POST /auth/password-recovery/confirm` is the URL the existing reset flow calls, with `{ token, new_password }` as the body.
2. A new `ForgotPasswordScreen` is reachable via `navController.navigate(Route.ForgotPassword)` and renders:
   - **Form mode** (default): title + subtitle + email input + "Enviar enlace" submit button.
   - **Sent mode** (after a successful 202): replaces the form with a "Revisá tu email" confirmation + "Volver" button that pops the back stack.
3. Submitting the form with an empty / invalid email shows a field-level error and does not call the backend.
4. Submitting with a valid email always transitions to sent mode on a 2xx response (the backend is silent on not-found by design).
5. Network failures show an inline submit error and keep the user in form mode so they can retry.

### Verification

- `./scripts/compile.sh` succeeds.
- Manual: navigating to `Route.ForgotPassword` from a debug entry point shows the form. Submitting with `bogus@example.com` against a stub returning 202 swaps the form for the inline confirmation. Tapping "Volver" pops back. With the network unplugged, submitting shows the "no pudimos conectarnos" message and keeps the form visible.
- Network log shows `POST /api/v1/auth/password-recovery/request` body `{"email":"bogus@example.com"}` for the request flow and `POST /api/v1/auth/password-recovery/confirm` body `{"token":"…","new_password":"…"}` for the (already-implemented) reset flow.

## What We're NOT Doing

- **Entry point wiring.** No link from `GetStartedScreen` or anywhere else. The screen exists, the route is registered, the DI graph resolves, but only in-app navigation reaches it. The link is the Login screen's responsibility (separate ticket).
- **Login screen.** Out of scope.
- **Backend host for the recovery deeplink.** Backend hasn't decided. The mobile-side scheme/host (`ingenieriaalimentacion://reset-password?token=…`) is already declared in `ResetPasswordNavigation.kt:17` and `AndroidManifest.xml`; we are not touching it.
- **iOS deeplink for the reset link.** Same scope deferral as the original `reset_password_screen.md` plan.
- **A separate `ForgotPasswordSuccess` screen / route.** Inline state swap per product decision — no extra route, no extra navigation, no extra file.
- **A `ForgotPasswordDestination` for `Back`.** Use `navController.popBackStack()` from the `composable` block directly. Destination would be one-of-one and adds no value.
- **Rate-limiting UI / "resend in 30s" timer.** Backend handles enumeration safety via silent 202; client-side rate-limiting copy is out of scope until product asks for it.
- **Removing `AuthError.WeakPassword`.** The variant is no longer mapped from a response code (see Phase 1) but stays in the sealed interface so the `when` branch in `ResetPasswordViewModel.toUserMessage` (`ResetPasswordViewModel.kt:98-99`) stays valid without a behavior change. Worth a follow-up cleanup once a Login screen confirms no other consumer needs it.

## Implementation Approach

Five phases, working backend-contract-first → domain → data → presentation → DI/nav:

- **Phase 1** realigns the existing `ResetPassword` data path with the backend contract. Small, low-risk, unblocks the rest of the plan (and end-to-end test of either flow).
- **Phase 2** adds the new domain primitives.
- **Phase 3** adds the new data path.
- **Phase 4** adds the new presentation feature using the `feature-screen-generator` scaffolding, then customizes for inline success.
- **Phase 5** wires Route, DI, and the nav graph.

---

## Phase 1: Realign existing ResetPassword with backend contract

### Overview

Three small edits to the data layer of the existing `ResetPassword` flow so it matches the backend's `POST /auth/password-recovery/confirm` contract. No presentation changes.

### Changes Required

#### 1. Point `AuthApi.resetPassword` at the correct path

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/AuthApi.kt`
**Changes**: Update the endpoint string in `resetPassword`.

```kotlin
suspend fun resetPassword(token: String, newPassword: String) {
    refreshClient.post("auth/password-recovery/confirm") {
        contentType(ContentType.Application.Json)
        setBody(ResetPasswordRequest(token = token, password = newPassword))
    }
}
```

#### 2. Rename the password field in the body

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/dto/ResetPasswordRequest.kt`
**Changes**: Keep the Kotlin property `password` (matches the parameter name everywhere it's constructed) but serialize it as `new_password` via `@SerialName`. This avoids a cascading rename through `AuthApi` / `AuthRepositoryImpl` / `ResetPasswordUseCase` / `ResetPasswordViewModel`.

```kotlin
package ar.com.ingenieriaalimentacion.data.auth.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResetPasswordRequest(
    val token: String,
    @SerialName("new_password") val password: String,
)
```

#### 3. Narrow `mapResetError` to the backend's documented status codes

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/AuthRepositoryImpl.kt`
**Changes**: Replace the over-broad `mapResetError`. Only `401` is documented; everything else is `Network` (for `ResponseException`s) or `Unknown` (everything else).

```kotlin
private fun mapResetError(throwable: Throwable): AuthError = when {
    throwable is ResponseException && throwable.response.status == HttpStatusCode.Unauthorized ->
        AuthError.InvalidOrExpiredToken
    throwable is ResponseException -> AuthError.Network
    else -> AuthError.Unknown(throwable)
}
```

`AuthError.WeakPassword` is no longer produced. It stays in the sealed interface so `ResetPasswordViewModel.toUserMessage`'s exhaustive `when` keeps compiling without edits. Flag for a follow-up cleanup once Login confirms no consumer relies on it.

### Success Criteria

#### Automated Verification

- [x] Kotlin compilation passes: `./scripts/compile.sh`

#### Manual Verification

- [ ] Hitting the reset flow against a backend that responds 204 lands on `ResetPasswordSuccess`.
- [ ] Hitting the reset flow with an invalid/expired token (backend returns 401) shows "El enlace expiró o ya fue utilizado…".
- [ ] Network log shows `POST /api/v1/auth/password-recovery/confirm` with body key `new_password` (not `password`).

---

## Phase 2: Domain layer for password-recovery request

### Overview

Add the repository contract method and the use case for requesting a recovery email.

### Changes Required

#### 1. Extend `AuthRepository`

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/repository/AuthRepository.kt`
**Changes**: Add `requestPasswordRecovery`. Returns `Result<Unit>` — there's no payload to return on success.

```kotlin
interface AuthRepository {
    val sessionStatus: StateFlow<SessionStatus>
    suspend fun bootstrap()
    suspend fun signIn(credentials: Credentials): Result<UserSession>
    suspend fun signOut()
    suspend fun resetPassword(token: String, newPassword: String): Result<Unit>
    suspend fun requestPasswordRecovery(email: String): Result<Unit>
}
```

#### 2. Add `RequestPasswordRecoveryUseCase`

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/usecase/auth/RequestPasswordRecoveryUseCase.kt`
**Changes**: New file. Trivial wrapper around the repository — no business logic.

```kotlin
package ar.com.ingenieriaalimentacion.domain.usecase.auth

import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository

class RequestPasswordRecoveryUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String): Result<Unit> =
        repository.requestPasswordRecovery(email)
}
```

### Success Criteria

#### Automated Verification

- [x] Kotlin compilation passes: `./scripts/compile.sh`
- [x] `AuthRepositoryImpl` fails to compile (expected — Phase 3 implements the new method)

#### Manual Verification

- [ ] N/A (no runtime changes yet)

---

## Phase 3: Data layer for password-recovery request

### Overview

DTO, API method against the no-auth `refreshClient`, and the repository implementation. Backend always returns 202 for this endpoint by design (silent on not-found to prevent user enumeration), so the only mapped failures are `Network` for `ResponseException` and `Unknown` for everything else.

### Changes Required

#### 1. Add `RequestPasswordRecoveryRequest` DTO

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/dto/RequestPasswordRecoveryRequest.kt`
**Changes**: New file mirroring `LoginRequest.kt`.

```kotlin
package ar.com.ingenieriaalimentacion.data.auth.dto

import kotlinx.serialization.Serializable

@Serializable
data class RequestPasswordRecoveryRequest(val email: String)
```

#### 2. Add `requestPasswordRecovery` to `AuthApi`

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/AuthApi.kt`
**Changes**: New method posting to `auth/password-recovery/request` on the no-auth `refreshClient`. No response body to decode — backend returns 202 with empty body.

```kotlin
suspend fun requestPasswordRecovery(email: String) {
    refreshClient.post("auth/password-recovery/request") {
        contentType(ContentType.Application.Json)
        setBody(RequestPasswordRecoveryRequest(email = email))
    }
}
```

#### 3. Implement `requestPasswordRecovery` in `AuthRepositoryImpl`

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/AuthRepositoryImpl.kt`
**Changes**: New override that delegates to the API and wraps unexpected throws as `AuthFailure`. Reuse the existing `mapToAuthError`-style helper but with a request-specific narrowing — only `Network` / `Unknown` are reachable, since 2xx is the documented success and there's no enumerable user error.

```kotlin
override suspend fun requestPasswordRecovery(email: String): Result<Unit> = runCatching {
    api.requestPasswordRecovery(email)
}.recoverCatching { throwable ->
    throw AuthFailure(mapRequestRecoveryError(throwable), throwable)
}

private fun mapRequestRecoveryError(throwable: Throwable): AuthError = when (throwable) {
    is ResponseException -> AuthError.Network
    else -> AuthError.Unknown(throwable)
}
```

### Success Criteria

#### Automated Verification

- [x] Kotlin compilation passes: `./scripts/compile.sh`

#### Manual Verification

- [ ] N/A until Phase 4 wires a UI to call this

---

## Phase 4: Presentation feature `ForgotPassword`

### Overview

Scaffold six MVVM files via the `feature-screen-generator` skill, then customize for a single email input and the inline-success state swap (no destination, no separate screen). The screen lives at `presentation/feature/auth/forgot_password/` next to `reset_password/`.

### Scaffold parameters

- **Feature Name**: `ForgotPassword`
- **Feature path**: `presentation/feature/auth/forgot_password/`
- **Has Navigation Parameters?**: No.
- **Key User Actions**: `EmailChanged`, `Submit`, `DismissError`, `Back`.
- **Navigation Destinations**: `Back` only — pops the back stack. (No `Success` destination; success is an in-state flag.)

### Changes Required

#### 1. Run the skill

Invoke `feature-screen-generator` with the parameters above. It will produce the six files under the target folder and propose edits to `Route.kt` / `ViewModelsModule.kt` / `AppNavHost.kt`. Apply the file generation; Phase 5 reconciles the integration edits.

#### 2. `ForgotPasswordAction.kt`

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.auth.forgot_password

sealed interface ForgotPasswordAction {
    data class EmailChanged(val value: String) : ForgotPasswordAction
    data object Submit : ForgotPasswordAction
    data object DismissError : ForgotPasswordAction
    data object Back : ForgotPasswordAction
}
```

#### 3. `ForgotPasswordDestination.kt`

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.auth.forgot_password

sealed class ForgotPasswordDestination {
    data object Back : ForgotPasswordDestination()
}
```

#### 4. `ForgotPasswordState.kt`

`isSent = true` is the inline "check your email" signal. `fieldError` is the email-level validation message. `submitError` is the inline network-error banner.

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.auth.forgot_password

data class ForgotPasswordState(
    val destination: ForgotPasswordDestination? = null,
    val email: String = "",
    val isSubmitting: Boolean = false,
    val isSent: Boolean = false,
    val fieldError: String? = null,
    val submitError: String? = null,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && !isSubmitting && !isSent
}
```

#### 5. `ForgotPasswordViewModel.kt`

Mirrors `ResetPasswordViewModel.kt`'s shape (StateFlow, `onAction`, validation, error-to-message mapping) but trims it to one field and adds the `isSent` transition on success.

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.auth.forgot_password

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.auth.AuthFailure
import ar.com.ingenieriaalimentacion.domain.model.auth.AuthError
import ar.com.ingenieriaalimentacion.domain.usecase.auth.RequestPasswordRecoveryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ForgotPasswordViewModel(
    private val requestRecovery: RequestPasswordRecoveryUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotPasswordState())
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    fun onAction(action: ForgotPasswordAction) {
        when (action) {
            is ForgotPasswordAction.EmailChanged ->
                _state.update { it.copy(email = action.value, fieldError = null) }

            ForgotPasswordAction.Submit -> submit()
            ForgotPasswordAction.DismissError -> _state.update { it.copy(submitError = null) }
            ForgotPasswordAction.Back ->
                _state.update { it.copy(destination = ForgotPasswordDestination.Back) }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    private fun submit() {
        val current = _state.value
        val emailError = validateEmail(current.email)
        if (emailError != null) {
            _state.update { it.copy(fieldError = emailError) }
            return
        }

        _state.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            requestRecovery(email = current.email.trim()).fold(
                onSuccess = {
                    _state.update { it.copy(isSubmitting = false, isSent = true) }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(isSubmitting = false, submitError = throwable.toUserMessage())
                    }
                },
            )
        }
    }

    private fun validateEmail(email: String): String? = when {
        email.isBlank() -> "Ingresá tu email."
        !EMAIL_REGEX.matches(email.trim()) -> "Ingresá un email válido."
        else -> null
    }

    private fun Throwable.toUserMessage(): String {
        val error = (this as? AuthFailure)?.error
        return when (error) {
            AuthError.Network ->
                "No pudimos conectarnos. Verificá tu conexión e intentá nuevamente."
            AuthError.InvalidCredentials,
            AuthError.InvalidOrExpiredToken,
            AuthError.WeakPassword,
            is AuthError.Unknown,
            null,
                -> "Ocurrió un error inesperado. Intentá nuevamente."
        }
    }

    private companion object {
        val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
```

#### 6. `ForgotPasswordScreen.kt`

Two-composable pattern: `ForgotPasswordScreenRoot` (collects state, observes `destination`, delegates navigation) + stateless `ForgotPasswordScreen`. The screen renders either the form column or the inline confirmation column based on `state.isSent`. Same scroll/IME pattern as `ResetPasswordScreen.kt:64-143`. Komoui `Button` and `Input` to stay visually consistent with the rest of the recovery flow.

Copy:
- Title (form): `"Recuperar contraseña"` (`headlineMedium`)
- Subtitle (form): `"Ingresá tu email y te enviaremos un enlace para recuperar tu contraseña."` (`bodyMedium`, muted)
- Email label: `"Email"`, placeholder `"tu@email.com"`, `KeyboardType.Email`
- Submit button: `"Enviar enlace"`
- Title (sent): `"Revisá tu email"`
- Body (sent): `"Si existe una cuenta con ese email, te enviamos un enlace para recuperar tu contraseña. Revisá tu bandeja de entrada y la carpeta de spam."`
- Back button (sent): `"Volver"`

Skeleton:

```kotlin
@Composable
fun ForgotPasswordScreenRoot(
    viewModel: ForgotPasswordViewModel = koinViewModel(),
    navigate: (ForgotPasswordDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }
    ForgotPasswordScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun ForgotPasswordScreen(state: ForgotPasswordState, onAction: (ForgotPasswordAction) -> Unit) {
    // Same outer Column / safeDrawingPadding / imePadding scaffolding as ResetPasswordScreen.
    // if (state.isSent) renders confirmation column + "Volver" button (-> onAction(Back))
    // else renders form column + submit button (-> onAction(Submit)) + inline submitError banner.
}
```

Re-use the private `ResetPasswordField` pattern by copying a minimal `ForgotPasswordField` into the same file (single field, no need to extract a shared helper yet — extract once a third feature needs it).

### Success Criteria

#### Automated Verification

- [x] All six new files compile: `./scripts/compile.sh`
- [x] No unresolved references to `AuthFailure`, `AuthError`, `RequestPasswordRecoveryUseCase`, `Route.ForgotPassword`

#### Manual Verification

- [ ] In-app navigation `navController.navigate(Route.ForgotPassword)` from a debug entry point renders the form with title, subtitle, email input, and submit button.
- [ ] Submitting with blank email shows `"Ingresá tu email."` and does not call the backend.
- [ ] Submitting with a malformed email (e.g. `foo@bar`) shows `"Ingresá un email válido."` and does not call the backend.
- [ ] Editing the email after a field error clears the error.
- [ ] Submitting with a valid email shows the loading state on the button, then swaps the form for the "Revisá tu email" confirmation.
- [ ] Tapping "Volver" in sent mode pops the back stack.
- [ ] With the network unplugged, submitting shows the `"No pudimos conectarnos…"` banner and keeps the form visible.

---

## Phase 5: Route, DI, and nav-graph wiring

### Overview

Register the new route, the use case, the ViewModel binding, and the `composable<Route.ForgotPassword>` block. Navigation is done inline in the `composable` lambda — no separate navigation helper, no destination switch other than `Back`.

### Changes Required

#### 1. `Route.kt`

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/navigation/Route.kt`
**Changes**: Add one route.

```kotlin
@Serializable
@SerialName("forgot_password")
data object ForgotPassword : Route()
```

#### 2. `UseCasesModule.kt`

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/UseCasesModule.kt`
**Changes**: Add one factory.

```kotlin
factory { RequestPasswordRecoveryUseCase(get()) }
```

#### 3. `ViewModelsModule.kt`

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/ViewModelsModule.kt`
**Changes**: Add one `viewModelOf` (no route params).

```kotlin
viewModelOf(::ForgotPasswordViewModel)
```

#### 4. `ForgotPasswordNavigation.kt`

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/auth/forgot_password/ForgotPasswordNavigation.kt`
**Changes**: New file. Single `composable<Route.ForgotPassword>` block + a `navigateToForgotPassword` helper for future callers (e.g. the Login screen).

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.auth.forgot_password

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToForgotPassword(navOptions: NavOptions? = null) =
    navigate(Route.ForgotPassword, navOptions)

fun NavGraphBuilder.forgotPasswordScreen(navController: NavController) {
    composable<Route.ForgotPassword> {
        ForgotPasswordScreenRoot(
            navigate = { destination ->
                when (destination) {
                    ForgotPasswordDestination.Back -> navController.popBackStack()
                }
            },
        )
    }
}
```

#### 5. `AppNavHost.kt`

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/AppNavHost.kt`
**Changes**: Wire the new screen.

```kotlin
import ar.com.ingenieriaalimentacion.presentation.feature.auth.forgot_password.forgotPasswordScreen
// ...
forgotPasswordScreen(navController)
```

### Success Criteria

#### Automated Verification

- [x] Kotlin compilation passes for plan-owned files: `./scripts/compile.sh` (one pre-existing error remains in unrelated in-flight `data/device/DeviceIdProvider.kt` — out of scope, see [implementation notes](#implementation-notes))
- [ ] Android assemble passes: `./gradlew :composeApp:assembleDebug --no-daemon` (blocked by the same pre-existing DeviceIdProvider error)

#### Manual Verification

- [ ] DI graph resolves at startup (no Koin definition-missing crashes).
- [ ] Manually navigating to `Route.ForgotPassword` from a debug entry point reaches the screen.

---

## Testing Strategy

### Unit tests (commonTest)

Following the project's `RegisterViewModelTest` / `ResetPasswordViewModelTest` pattern, add `ForgotPasswordViewModelTest`:

- Submit with empty email → `fieldError` set, use case NOT invoked.
- Submit with malformed email → `fieldError` set, use case NOT invoked.
- Submit with valid email → use case invoked with trimmed email, `isSent = true`, `isSubmitting = false`.
- Use case returns `AuthFailure(Network)` → `submitError` set to the network message, `isSent` stays false, form remains visible.
- `EmailChanged` after a `fieldError` clears the error.
- `Back` action sets `destination = Back`.

Use a `FakeRequestPasswordRecoveryUseCase` per the project's fakes-over-mocks convention.

### Manual testing

See per-phase manual checks above. Capture a screenshot of both modes (form + sent) for the PR description.

## Performance Considerations

Single form screen, single suspending call, single state swap. No performance concerns.

## Migration Notes

Backend prerequisite: `POST /api/v1/auth/password-recovery/request` and `POST /api/v1/auth/password-recovery/confirm` must be implemented and deployed. The FE builds and ships independently; submissions 404 until the backend lands.

## References

- Backend flow described in the ticket above (`/auth/password-recovery/request` + `/confirm`).
- Existing reset-password plan (sibling flow, same folder, same patterns): `thoughts/plans/reset_password_screen.md`.
- Closest reference feature (auth-style form + validation + IME handling): `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/auth/reset_password/`.
- Reference for single-field email-only form: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/register/RegisterScreen.kt:115-125`.
- Skill that scaffolds the six feature files: `.claude/skills/feature-screen-generator/SKILL.md`.
- Project conventions (CMP, MVVM file split, IME scroll pattern): `CLAUDE.md`.
