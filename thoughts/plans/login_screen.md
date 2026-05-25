# Login Screen Implementation Plan

## Overview

Build a Login feature (email + password) wired to `POST /auth/login` per the mobile integration contract. Land the missing pieces of the contract along the way: a stable `device_id` (UUID v4) persisted in platform-secure storage, a typed `AuthUser` returned by the backend that survives token rotation, and a `403 PENDING_VALIDATION` error case. Reuse the existing `bearer { refreshTokens }` infrastructure for token refresh; do **not** build a separate 401 interceptor.

## Current State Analysis

The auth infrastructure is already largely in place from the password-reset work:

- **Data:** `data/auth/AuthApi.kt` exposes `signIn(email, password)` (no `device_id`), `data/auth/AuthRepositoryImpl.kt:39-47` wraps it in `runCatching` and maps `401 -> InvalidCredentials`, `data/auth/storage/TokenStore.{kt,android.kt,ios.kt}` provides AES-GCM/Keychain string storage, and `data/auth/storage/TokenStoreSessionManager.kt` serializes the whole `UserSession` to a single JSON blob.
- **Domain:** `domain/usecase/auth/SignInUseCase.kt:7-10` delegates to the repo; `domain/model/auth/Credentials.kt` is `data class Credentials(val email, val password)`; `domain/model/auth/UserSession.kt:9-16` holds only token fields (`access_token`, `refresh_token`, `expires_in`, `token_type`, `expiresAt`); `domain/model/auth/AuthError.kt:3-9` has `InvalidCredentials`, `InvalidOrExpiredToken`, `WeakPassword`, `Network`, `Unknown`.
- **HTTP:** `data/network/HttpClientFactory.kt` wires Ktor's `Auth` plugin with `bearer { loadTokens / refreshTokens }` (`HttpClientFactory.kt:60-86`) — refresh runs automatically on any 401 from the authed client, serialized by Ktor itself. `BASE_URL` is hardcoded in `data/network/ApiConfig.kt`.
- **DI:** `di/ClientsModule.kt:24-30` registers the no-auth `named("refresh")` client used by `AuthApi`; `di/RepositoriesModule.kt:21` binds `SessionManager`; `di/UseCasesModule.kt:15` already registers `SignInUseCase`.
- **Presentation:** `presentation/feature/auth/reset_password/` is the closest 6-file analogue. `presentation/feature/get_started/GetStartedState.kt:9` has `isLoginEnabled: Boolean = false` gating a disabled "Iniciar sesión" tap target; `GetStartedViewModel.kt:23` currently no-ops `LogInClick`.
- **Navigation:** `presentation/navigation/Route.kt` enumerates routes; `app/AppNavHost.kt` is the registration point.

What's **missing** versus the contract:

1. **No `device_id`** — nothing in `composeApp/src` mentions `UUID`, `deviceId`, `installationId`. `LoginRequest` carries only `email`/`password`, `RefreshRequest` only `refresh_token`.
2. **No `user` object** persisted — `UserSession` has no user fields, so the contract's `{ id, email, name, last_name, role }` payload is dropped on the floor today.
3. **No `403 PENDING_VALIDATION` handling** — the current `mapToAuthError` collapses everything that isn't 401 into `Network`.
4. **No Login screen** — `Route.Login` doesn't exist; `GetStarted` cannot navigate there.

## Desired End State

A user lands on `GetStartedScreen`, taps "Iniciar sesión", types email + password, taps the submit button, and:

- On 200: tokens + user persist via `TokenStore`, the authed `HttpClient` is pre-loaded with the new bearer token, `Route.Home` becomes the start of a clean back stack, and the next protected call refreshes automatically on 401.
- On 401: shows "Email o contraseña incorrectos." inline above the button. State stays on the screen.
- On 403: shows "Tu cuenta está pendiente de aprobación." Same.
- On network failure: shows "No pudimos conectarnos. Verificá tu conexión e intentá nuevamente." Same.

A persistent `device_id` (UUID v4) is generated once per install and sent on every `/auth/login` and `/auth/refresh` request. `kotlin.uuid.Uuid` is the source of randomness in commonMain.

### Key Discoveries

- `AuthRepositoryImpl.kt:39-47` already maps 401 to `AuthError.InvalidCredentials` via `mapToAuthError`. We extend the same function to add 403 → `AccountPendingValidation`.
- `HttpClientFactory.kt:65-83` is the **only** place that needs to know the new `device_id` for refresh; the bearer plugin already serializes concurrent refreshes by design (see Ktor `BearerAuthProvider` — single in-flight refresh, queued retries), so the contract's "Serialize concurrent refreshes" requirement is satisfied implicitly.
- `TokenStoreSessionManager.kt:23-31` self-heals on `SerializationException` by clearing the store and returning `null`. Adding `user: AuthUser?` to `UserSession` preserves this behavior (existing installs without the new field will just self-heal and the user re-logs in).
- `ResetPasswordViewModel.kt:93-105` is the canonical `Throwable.toUserMessage()` pattern — unwrap `AuthFailure.error`, exhaustive `when` over `AuthError`, hardcoded Spanish strings. Login follows it 1:1.
- `ResetPasswordScreen.kt` is the load-bearing reference for KomoUI layout (pinned-bottom button, `weight(1f).verticalScroll(scrollState)`, IME-aware scroll-to-focused-field).

## What We're NOT Doing

- **No logout / sign-out work.** `AuthRepository.signOut()` already exists; we don't touch it. The contract's logout section is out of scope per the user.
- **No password recovery.** `/auth/password-recovery/{request,confirm}` are out of scope. The login screen renders an "¿Olvidaste tu contraseña?" text that is visible but inert until the reset-password screen is finished.
- **No manual 401 interceptor.** Ktor's `Auth` plugin already does refresh-on-401, retry-once, and serializes concurrent refreshes. We only update the `refreshTokens { ... }` lambda to include `device_id`.
- **No `/me` bootstrap call.** We persist the `user` object inside `UserSession` and rehydrate it from `TokenStore` on app start.
- **No backend-side `expires_in` change.** Keep the existing `expires_in: Long = 0L` default; do not add JWT decoding.
- **No `last_name` → `lastName` renaming on the wire.** Use `@SerialName("last_name")` only.
- **No role enum.** `role` stays a `String` — we don't yet know the full set.
- **No `Route.LoginSuccess` intermediate screen.** Login goes straight to `Route.Home`.
- **No new HTTP client.** Reuse the existing `named("refresh")` client for the login call.

## Implementation Approach

Three phases, each independently compilable:

1. **Plumbing the contract gap** — `device_id` infra, `AuthUser` model, 403 mapping, request/response DTO updates, refresh wiring. No UI yet.
2. **Login feature scaffold** — the 6-file MVVM bundle plus the success path that wipes the back stack and lands on `Route.Home`.
3. **Wiring entry points** — `Route.Login`, `AppNavHost`, `GetStarted` link enablement, Koin VM registration.

Each phase compiles cleanly via `./scripts/compile.sh`. Manual verification happens at the end of phase 3.

---

## Phase 1: Device ID, AuthUser, 403 mapping, contract-aligned requests

### Overview

Add the cross-cutting bits the contract requires that don't depend on the Login UI. Specifically: a persisted UUID v4 device id, an `AuthUser` model that rides inside `UserSession` and is preserved across refresh, a new `AuthError.AccountPendingValidation` case, and `device_id` fields in `LoginRequest` + `RefreshRequest`. Update `HttpClientFactory.refreshTokens` to pass `device_id` and merge the existing `user` into the refreshed session.

### Changes Required

#### 1. DeviceIdStore (expect/actual) + DeviceIdProvider (common)

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/device/DeviceIdStore.kt` (new)
**Changes**: Mirror `TokenStore.kt` shape — single-string `expect class`.

```kotlin
package ar.com.ingenieriaalimentacion.data.device

expect class DeviceIdStore {
    suspend fun get(): String?
    suspend fun set(value: String)
}
```

**File**: `composeApp/src/androidMain/kotlin/ar/com/ingenieriaalimentacion/data/device/DeviceIdStore.android.kt` (new)
**Changes**: Use `EncryptedSharedPreferences`-style AES-GCM/AndroidKeyStore pattern, copied structurally from `TokenStore.android.kt`. Different `PREFS_NAME` and `KEY_ALIAS` so the two stores don't collide. The `device_id` is not strictly secret (it's installation identity) but the contract specifies secure storage, and reusing the same recipe is lower risk than introducing a second persistence path.

```kotlin
package ar.com.ingenieriaalimentacion.data.device

import android.content.Context
// ... same cipher/keystore imports as TokenStore.android.kt

actual class DeviceIdStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    actual suspend fun get(): String? = withContext(Dispatchers.IO) { /* decrypt */ }
    actual suspend fun set(value: String) = withContext(Dispatchers.IO) { /* encrypt + write */ }

    private companion object {
        const val PREFS_NAME = "ar.com.ingenieriaalimentacion.device"
        const val PREF_KEY = "device_id"
        const val KEY_ALIAS = "ar.com.ingenieriaalimentacion.device.id_v1"
    }
}
```

**File**: `composeApp/src/iosMain/kotlin/ar/com/ingenieriaalimentacion/data/device/DeviceIdStore.ios.kt` (new)
**Changes**: Keychain-backed, copied structurally from `TokenStore.ios.kt`. Distinct `SERVICE` so the keychain item is separate.

```kotlin
@OptIn(ExperimentalForeignApi::class)
actual class DeviceIdStore {
    actual suspend fun get(): String? = withContext(Dispatchers.Default) { /* SecItemCopyMatching */ }
    actual suspend fun set(value: String) = withContext(Dispatchers.Default) { /* SecItemAdd / SecItemUpdate */ }

    private companion object {
        const val SERVICE = "ar.com.ingenieriaalimentacion.device"
        const val ACCOUNT = "device_id"
    }
}
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/device/DeviceIdProvider.kt` (new)
**Changes**: Get-or-create wrapper using `kotlin.uuid.Uuid.random()`. Mutex-guarded so two concurrent first-launch callers cannot generate two different ids.

```kotlin
package ar.com.ingenieriaalimentacion.data.device

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalUuidApi::class)
class DeviceIdProvider(private val store: DeviceIdStore) {
    private val mutex = Mutex()
    @Volatile private var cached: String? = null

    suspend fun getOrCreate(): String {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return it }
            val existing = store.get()
            if (existing != null) {
                cached = existing
                return existing
            }
            val fresh = Uuid.random().toString()
            store.set(fresh)
            cached = fresh
            fresh
        }
    }
}
```

#### 2. AuthUser domain model

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/model/auth/AuthUser.kt` (new)
**Changes**: Mirrors the contract payload. `role` stays a `String` (we don't yet know the full enum domain).

```kotlin
package ar.com.ingenieriaalimentacion.domain.model.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthUser(
    val id: String,
    val email: String,
    val name: String,
    @SerialName("last_name") val lastName: String,
    val role: String,
)
```

#### 3. UserSession gains `user: AuthUser?`

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/model/auth/UserSession.kt`
**Changes**: Add the optional `user` field at the end so refresh responses (no user) still deserialize.

```kotlin
@Serializable
data class UserSession(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 0L,
    @SerialName("token_type") val tokenType: String = "Bearer",
    val expiresAt: Instant = Clock.System.now() + expiresIn.seconds,
    val user: AuthUser? = null,
)
```

Self-healing in `TokenStoreSessionManager.kt:23-31` handles already-stored sessions that lack `user` cleanly via `ignoreUnknownKeys`/default value.

#### 4. AuthError gains AccountPendingValidation

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/model/auth/AuthError.kt`
**Changes**: Add a new sealed case.

```kotlin
sealed interface AuthError {
    data object InvalidCredentials : AuthError
    data object AccountPendingValidation : AuthError   // <-- NEW (403 on /auth/login)
    data object InvalidOrExpiredToken : AuthError
    data object WeakPassword : AuthError
    data object Network : AuthError
    data class Unknown(val cause: Throwable? = null) : AuthError
}
```

Existing exhaustive `when` over `AuthError` in `ResetPasswordViewModel.toUserMessage()` (lines 95-105) must add the new branch — collapse it under "unexpected" so the reset-password copy doesn't change. Concretely: change the `is AuthError.Unknown, null` arm to `AuthError.AccountPendingValidation, AuthError.InvalidCredentials, is AuthError.Unknown, null -> ...`.

#### 5. LoginRequest carries device_id

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/dto/LoginRequest.kt`
**Changes**: Add `@SerialName("device_id") val deviceId: String`.

```kotlin
@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    @SerialName("device_id") val deviceId: String,
)
```

#### 6. RefreshRequest carries device_id

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/dto/RefreshRequest.kt`
**Changes**:

```kotlin
@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("device_id") val deviceId: String,
)
```

#### 7. AuthApi signatures take device_id

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/AuthApi.kt`
**Changes**: Update both `signIn` and `refresh` to accept `deviceId: String`. The DI graph already injects only the no-auth client into `AuthApi`; no Koin change here.

```kotlin
suspend fun signIn(email: String, password: String, deviceId: String): UserSession =
    refreshClient.post("auth/login") {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(email, password, deviceId))
    }.body()

suspend fun refresh(refreshToken: String, deviceId: String): UserSession =
    refreshClient.post("auth/refresh") {
        contentType(ContentType.Application.Json)
        setBody(RefreshRequest(refreshToken, deviceId))
    }.body()
```

#### 8. AuthRepositoryImpl signs in with device_id, maps 403

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/AuthRepositoryImpl.kt`
**Changes**: Inject `DeviceIdProvider`. Pass `deviceId` into `signIn`. Extend `mapToAuthError` for 403.

```kotlin
class AuthRepositoryImpl(
    private val api: AuthApi,
    private val sessionManager: SessionManager,
    private val httpClient: HttpClient,
    private val statusSink: MutableStateFlow<SessionStatus>,
    private val deviceIdProvider: DeviceIdProvider,   // <-- NEW
) : AuthRepository {

    override suspend fun signIn(credentials: Credentials): Result<UserSession> = runCatching {
        val deviceId = deviceIdProvider.getOrCreate()
        val session = api.signIn(credentials.email, credentials.password, deviceId)
        sessionManager.saveSession(session)
        clearBearerCache()
        statusSink.value = SessionStatus.Authenticated(session, SessionSource.SignIn)
        session
    }.recoverCatching { throwable ->
        throw AuthFailure(mapToAuthError(throwable), throwable)
    }

    private fun mapToAuthError(throwable: Throwable): AuthError = when {
        throwable is ResponseException && throwable.response.status == HttpStatusCode.Unauthorized ->
            AuthError.InvalidCredentials
        throwable is ResponseException && throwable.response.status == HttpStatusCode.Forbidden ->
            AuthError.AccountPendingValidation       // <-- NEW
        throwable is ResponseException -> AuthError.Network
        else -> AuthError.Unknown(throwable)
    }
}
```

#### 9. HttpClientFactory.refreshTokens passes device_id and preserves user

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/network/HttpClientFactory.kt`
**Changes**: `createAuthedClient` takes `deviceIdProvider`. The `refreshTokens` lambda awaits the id and merges the old session's `user` into the refreshed session (refresh response has no `user`).

```kotlin
fun createAuthedClient(
    engine: HttpClientEngine,
    json: Json,
    sessionManager: SessionManager,
    authApi: AuthApi,
    statusSink: MutableStateFlow<SessionStatus>,
    deviceIdProvider: DeviceIdProvider,    // <-- NEW
): HttpClient = HttpClient(engine) {
    // ... unchanged ...
    install(Auth) {
        bearer {
            loadTokens { /* unchanged */ }
            refreshTokens {
                val current = sessionManager.loadSessionOrNull() ?: run {
                    statusSink.value = SessionStatus.NotAuthenticated(isSignOut = false)
                    return@refreshTokens null
                }
                val deviceId = deviceIdProvider.getOrCreate()
                runCatching { authApi.refresh(current.refreshToken, deviceId) }.fold(
                    onSuccess = { fresh: UserSession ->
                        val merged = fresh.copy(user = fresh.user ?: current.user)
                        sessionManager.saveSession(merged)
                        statusSink.value = SessionStatus.Authenticated(merged, SessionSource.Refresh(current))
                        BearerTokens(merged.accessToken, merged.refreshToken)
                    },
                    // ... onFailure unchanged ...
                )
            }
            sendWithoutRequest { true }
        }
    }
}
```

#### 10. Koin wiring

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/ClientsModule.kt`
**Changes**: Pass `deviceIdProvider` into `createAuthedClient`.

```kotlin
single<HttpClient> {
    HttpClientFactory.createAuthedClient(
        engine = get<HttpClientEngine>(),
        json = get(),
        sessionManager = get<SessionManager>(),
        authApi = get(),
        statusSink = get(),
        deviceIdProvider = get(),     // <-- NEW
    )
}
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/RepositoriesModule.kt`
**Changes**: Add `single { DeviceIdProvider(store = get()) }` and append `deviceIdProvider = get()` to the `AuthRepositoryImpl` builder.

**File**: `composeApp/src/androidMain/kotlin/ar/com/ingenieriaalimentacion/di/Modules.android.kt`
**Changes**: Add `single { DeviceIdStore(context = androidContext()) }` next to the existing `TokenStore` registration.

**File**: `composeApp/src/iosMain/kotlin/ar/com/ingenieriaalimentacion/di/Modules.ios.kt`
**Changes**: Add `single { DeviceIdStore() }`.

### Success Criteria

#### Automated Verification

- [ ] Kotlin compilation passes: `./scripts/compile.sh`
- [ ] Android assembly passes: `./gradlew :composeApp:assembleDebug --no-daemon`
- [ ] No new `!!` usages introduced (style)
- [ ] `grep -R "device_id\|deviceId" composeApp/src/commonMain` returns hits in `LoginRequest`, `RefreshRequest`, `AuthApi`, `AuthRepositoryImpl`, `HttpClientFactory`, `DeviceIdProvider`

#### Manual Verification

- [ ] Reset-password flow still works end-to-end (no regression from `UserSession.user` addition)
- [ ] Cold-start app, run an authed request (any existing screen that hits a protected endpoint), uninstall, reinstall, and verify `device_id` is regenerated only on first launch and survives across app restarts

---

## Phase 2: Login feature (6 files, KomoUI styling, Home as success destination)

### Overview

The standard MVVM bundle under `presentation/feature/auth/login/`, copied structurally from `presentation/feature/auth/reset_password/`. Two fields (email, password), pinned-bottom submit button, IME-aware scroll-to-focused-field, hardcoded Spanish error copy, navigation to `Route.Home` with a clean back stack on success. An inert "¿Olvidaste tu contraseña?" link sits below the submit button, gated by `state.isForgotPasswordEnabled = false` until the recovery feature lands.

### Changes Required

#### 1. LoginAction.kt

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/auth/login/LoginAction.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.auth.login

sealed interface LoginAction {
    data class EmailChanged(val value: String) : LoginAction
    data class PasswordChanged(val value: String) : LoginAction
    data object Submit : LoginAction
    data object DismissError : LoginAction
}
```

#### 2. LoginDestination.kt

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/auth/login/LoginDestination.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.auth.login

sealed class LoginDestination {
    data object Home : LoginDestination()
}
```

#### 3. LoginState.kt

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/auth/login/LoginState.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.auth.login

data class LoginState(
    val destination: LoginDestination? = null,
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val fieldErrors: LoginFieldErrors = LoginFieldErrors(),
    val submitError: String? = null,
    val isForgotPasswordEnabled: Boolean = false,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && !isSubmitting
}

data class LoginFieldErrors(
    val email: String? = null,
    val password: String? = null,
)
```

#### 4. LoginViewModel.kt

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/auth/login/LoginViewModel.kt` (new)

Mirror `ResetPasswordViewModel` shape: `_state + stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), _state.value)`, exhaustive `onAction` reducer that clears per-field errors as the user types, `submit()` that validates, sets `isSubmitting`, calls `signIn(Credentials(email.trim(), password))`, and folds the `Result`. `toUserMessage()` maps `AuthError` to hardcoded Spanish strings.

```kotlin
class LoginViewModel(
    private val signIn: SignInUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    fun onAction(action: LoginAction) {
        when (action) {
            is LoginAction.EmailChanged ->
                _state.update {
                    it.copy(email = action.value, fieldErrors = it.fieldErrors.copy(email = null))
                }
            is LoginAction.PasswordChanged ->
                _state.update {
                    it.copy(password = action.value, fieldErrors = it.fieldErrors.copy(password = null))
                }
            LoginAction.Submit -> submit()
            LoginAction.DismissError -> _state.update { it.copy(submitError = null) }
        }
    }

    fun clearDestination() { _state.update { it.copy(destination = null) } }

    private fun submit() {
        val current = _state.value
        val errors = validate(current)
        if (errors != LoginFieldErrors()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }
        _state.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            signIn(Credentials(email = current.email.trim(), password = current.password)).fold(
                onSuccess = {
                    _state.update {
                        it.copy(isSubmitting = false, destination = LoginDestination.Home)
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(isSubmitting = false, submitError = throwable.toUserMessage())
                    }
                },
            )
        }
    }

    private fun validate(state: LoginState): LoginFieldErrors = LoginFieldErrors(
        email = when {
            state.email.isBlank() -> "Ingresá tu email."
            !EMAIL_REGEX.matches(state.email.trim()) -> "El email no es válido."
            else -> null
        },
        password = when {
            state.password.isBlank() -> "Ingresá tu contraseña."
            else -> null
        },
    )

    private fun Throwable.toUserMessage(): String {
        val error = (this as? AuthFailure)?.error
        return when (error) {
            AuthError.InvalidCredentials ->
                "Email o contraseña incorrectos."
            AuthError.AccountPendingValidation ->
                "Tu cuenta está pendiente de aprobación."
            AuthError.Network ->
                "No pudimos conectarnos. Verificá tu conexión e intentá nuevamente."
            AuthError.InvalidOrExpiredToken, AuthError.WeakPassword,
            is AuthError.Unknown, null ->
                "Ocurrió un error inesperado. Intentá nuevamente."
        }
    }

    private companion object {
        val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
```

The `EMAIL_REGEX` is intentionally the same shape as `RegisterViewModel.kt:127`; both validations stay client-side and forgiving — the server is the source of truth.

#### 5. LoginNavigation.kt

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/auth/login/LoginNavigation.kt` (new)

```kotlin
fun NavController.navigateToLogin(navOptions: NavOptions? = null) =
    navigate(Route.Login, navOptions)

fun NavGraphBuilder.loginScreen(navController: NavController) {
    composable<Route.Login> {
        LoginScreenRoot(
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(destination: LoginDestination, navController: NavController) {
    when (destination) {
        LoginDestination.Home ->
            navController.navigate(Route.Home) {
                popUpTo(0) { inclusive = true }
            }
    }
}
```

`popUpTo(0) { inclusive = true }` wipes GetStarted and Login from the back stack — same pattern as `ResetPasswordSuccessScreen`'s "Volver al inicio" handler (`ResetPasswordNavigation.kt:29-33`).

#### 6. LoginScreen.kt

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/auth/login/LoginScreen.kt` (new)

Structurally identical to `ResetPasswordScreen.kt`:
- `LoginScreenRoot` resolves the ViewModel via `koinViewModel()` (no route param), runs `LaunchedEffect(state.destination) { ... clearDestination() }`, and forwards to stateless `LoginScreen(state, onAction)`.
- `LoginScreen` body: outer `Column.fillMaxSize().background(styles.background).safeDrawingPadding().imePadding().padding(horizontal = 24.dp)`. Inner scrollable `Column.weight(1f).verticalScroll(scrollState).padding(top = 24.dp, bottom = 16.dp)` with `spacedBy(16.dp)` holds a headline ("Iniciar sesión"), a subtitle ("Ingresá tu email y contraseña para continuar."), and two `LoginField`s. Sibling bottom `Column.padding(top = 8.dp, bottom = 24.dp)` with `spacedBy(12.dp)` holds the optional `submitError` `Text`, the submit `Button(loading = state.isSubmitting, enabled = state.canSubmit, fullWidth = true) { Text("Iniciar sesión") }`, and the disabled "¿Olvidaste tu contraseña?" `Text` centered, styled with `MaterialTheme.typography.bodyMedium` + `styles.mutedForeground`, **not** tappable (no `clickable` modifier until `state.isForgotPasswordEnabled` flips true).
- Private `LoginField` composable parameterized by `keyboardType` and `visualTransformation` — one private composable rather than two, matching `RegisterField` (`RegisterScreen.kt:231-280`). Email field: `KeyboardType.Email`, no visual transformation. Password field: `KeyboardType.Password`, `PasswordVisualTransformation()`. Both wire `MutableInteractionSource` → `collectIsFocusedAsState` → `LaunchedEffect(isFocused, isImeVisible) { scrollState.animateScrollTo(fieldScrollOffset) }`.

Hardcoded Spanish copy lives directly in the composable.

#### 7. Route.Login registration

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/navigation/Route.kt`
**Changes**: Add a `Login` data object after `Register`.

```kotlin
@Serializable
@SerialName("login")
data object Login : Route()
```

### Success Criteria

#### Automated Verification

- [ ] Kotlin compilation passes: `./scripts/compile.sh`
- [ ] Android assembly passes: `./gradlew :composeApp:assembleDebug --no-daemon`
- [ ] All six files exist under `presentation/feature/auth/login/`

#### Manual Verification

- [ ] Field validation: empty email shows "Ingresá tu email.", malformed email shows "El email no es válido.", empty password shows "Ingresá tu contraseña."
- [ ] Submit button disabled when either field is blank or while submitting
- [ ] Keyboard opens → focused field scrolls into view (both fields, both platforms)
- [ ] Password field obscures input
- [ ] "¿Olvidaste tu contraseña?" renders muted and is not tappable

---

## Phase 3: Wire entry points (Koin VM, AppNavHost, GetStarted link)

### Overview

Register `LoginViewModel` in Koin, mount the screen in `AppNavHost`, and flip `GetStarted` to actually navigate to it.

### Changes Required

#### 1. Koin ViewModel registration

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/ViewModelsModule.kt`
**Changes**: Add `viewModelOf(::LoginViewModel)` next to the `RegisterViewModel` line.

```kotlin
viewModelOf(::AppViewModel)
viewModelOf(::GetStartedViewModel)
viewModelOf(::HomeViewModel)
viewModelOf(::RegisterViewModel)
viewModelOf(::LoginViewModel)              // <-- NEW
viewModelOf(::CreateSubjectViewModel)
```

#### 2. AppNavHost mounts loginScreen

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/AppNavHost.kt`
**Changes**: Import `loginScreen` and add `loginScreen(navController)` next to `registerScreen(navController)`.

#### 3. GetStartedDestination gains NavigateToLogin

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/get_started/GetStartedDestination.kt`
**Changes**:

```kotlin
sealed class GetStartedDestination {
    data object NavigateToRegister : GetStartedDestination()
    data object NavigateToLogin : GetStartedDestination()   // <-- NEW
}
```

#### 4. GetStartedNavigation routes the new destination

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/get_started/GetStartedNavigation.kt`
**Changes**: Import `navigateToLogin`, extend the exhaustive `when`.

```kotlin
when (destination) {
    GetStartedDestination.NavigateToRegister -> navController.navigateToRegister()
    GetStartedDestination.NavigateToLogin -> navController.navigateToLogin()
}
```

#### 5. GetStartedViewModel emits the destination on LogInClick

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/get_started/GetStartedViewModel.kt`
**Changes**: Replace the no-op `LogInClick -> Unit` (line 23):

```kotlin
GetStartedAction.LogInClick ->
    _state.update { it.copy(destination = GetStartedDestination.NavigateToLogin) }
```

#### 6. Flip the GetStarted login link enablement

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/get_started/GetStartedState.kt`
**Changes**: `val isLoginEnabled: Boolean = true` (line 9).

### Success Criteria

#### Automated Verification

- [ ] Kotlin compilation passes: `./scripts/compile.sh`
- [ ] Android assembly passes: `./gradlew :composeApp:assembleDebug --no-daemon`
- [ ] `grep -R "Route.Login\b" composeApp/src/commonMain` returns hits in `Route.kt`, `LoginNavigation.kt`, `AppNavHost.kt` (via the screen registration)

#### Manual Verification

- [ ] GetStarted "Iniciar sesión" link is now tappable and navigates to the Login screen
- [ ] System back from Login returns to GetStarted (back stack preserved before submit)
- [ ] **Happy path:** enter valid credentials → 200 → land on Home with the back stack wiped (system back exits the app)
- [ ] **401 path:** enter wrong credentials → inline "Email o contraseña incorrectos." → stays on Login
- [ ] **403 path:** enter PENDING_VALIDATION account credentials → inline "Tu cuenta está pendiente de aprobación." → stays on Login
- [ ] **Network failure:** kill connectivity → submit → "No pudimos conectarnos. Verificá tu conexión e intentá nuevamente."
- [ ] After successful login, any protected call that returns 401 triggers refresh automatically (use a known protected endpoint from `SubjectApi` if available) and succeeds on retry without UI flicker
- [ ] iOS Keychain and Android Keystore both persist the device_id across cold restarts (re-login twice from the same install — second login uses the same device_id)
- [ ] User object survives a refresh — confirm by triggering a refresh and observing the persisted `UserSession.user` is non-null (manual check in `TokenStoreSessionManager` via debug log, or by inspecting `SessionStatus.Authenticated.session.user` from a downstream consumer)

---

## Testing Strategy

### Manual Testing Steps

The Login flow is end-to-end network-dependent and visual; manual verification on both platforms is mandatory.

1. **Cold install on Android.** Tap "Iniciar sesión" → see screen → enter wrong credentials → see error → enter right credentials → land on Home.
2. **Cold install on iOS.** Same flow.
3. **Refresh path.** After login, leave the app open for >15 min (or shorten the backend's access token TTL in a test environment), then trigger any protected request — confirm no UI prompt, the refresh runs silently, and the request succeeds.
4. **Device id stability.** Note the device_id (briefly log it in `DeviceIdProvider.getOrCreate()` during testing, remove before merge). Log out, log back in, restart the app — device_id should never change. Uninstall and reinstall — device_id should change exactly once.
5. **Account pending validation.** Use a backend-side account in `PENDING_VALIDATION` state (coordinate with backend) to verify the 403 mapping.

### Optional Unit Tests

Following `kotlin/testing.md` (ViewModel + UseCase minimum):

- `LoginViewModelTest` (commonTest) with `runTest` + Turbine: covers field validation (blank email, malformed email, blank password), `canSubmit` gating, `submitError` for each `AuthError` branch, and `destination = Home` on success. Use a hand-written `FakeSignInUseCase` (`SignInUseCase` is `class`, not interface — write a `FakeAuthRepository : AuthRepository` and inject a real `SignInUseCase(fakeRepo)`).
- These tests are nice-to-have for this plan and can land in a follow-up.

## Performance Considerations

- `DeviceIdProvider` reads the secure store at most once per process; the `@Volatile cached` field is fine — there is no contention path that warrants a `Mutex` upgrade beyond the initial create.
- Adding `user: AuthUser?` to `UserSession` increases the persisted JSON blob by ~150 bytes — negligible for `SharedPreferences` and Keychain.
- The login network call is on `viewModelScope` (Dispatchers.Main); Ktor itself jumps to IO. No additional dispatcher work required.

## Migration Notes

- Existing installs (from the reset-password work) have a `UserSession` JSON in `TokenStore` without the `user` field. `kotlinx.serialization` with the default-valued `user: AuthUser? = null` deserializes it cleanly — no migration needed.
- Existing installs have no `device_id` in `DeviceIdStore`. `DeviceIdProvider.getOrCreate()` mints one on the next login or refresh, which is exactly the desired behavior — there is no historical id to preserve. The first authenticated request after upgrade may trigger a refresh that mints the id (because the next refresh call needs `device_id`); since the backend tolerates a first-time `(user_id, device_id)` tuple, this is safe.

## References

- Contract: provided inline in the task brief (sections 1–8: device_id, /auth/login, token storage, protected endpoints, refresh, logout, quick reference, gotchas; logout + recovery are explicitly out of scope per the user)
- Closest existing analogue: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/auth/reset_password/`
- Similar form layout: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/register/RegisterScreen.kt`
- Ktor bearer refresh wiring: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/network/HttpClientFactory.kt:60-86`
- Secure storage recipe to copy: `composeApp/src/{androidMain,iosMain}/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStore.{android,ios}.kt`
- Error mapping conventions: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/AuthRepositoryImpl.kt:73-90`
- Previous plan in the same surface area: `thoughts/plans/reset_password_screen.md`
