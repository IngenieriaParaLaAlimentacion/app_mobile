---
date: 2026-05-02T02:05:58Z
researcher: Juan Duzac
git_commit: 7fb0ba6a730b921af2b9e2965fd3e2778d15eb7f
branch: main
repository: app_mobile
topic: "Current auth implementation — how and where tokens are stored"
tags: [research, codebase, auth, tokens, ktor, keychain, android-keystore, kmp, supabase-shape]
status: complete
last_updated: 2026-05-01
last_updated_by: Juan Duzac
last_updated_note: "Added follow-up research on official Google/Apple documentation for auth token storage"
---

# Research: Current auth implementation — how and where tokens are stored

**Date**: 2026-05-02T02:05:58Z
**Researcher**: Juan Duzac
**Git Commit**: `7fb0ba6` (uncommitted auth work in working tree)
**Branch**: main
**Repository**: app_mobile (Compose Multiplatform — Android + iOS)

## Research Question

Research the current auth implementations, specifically how are tokens stored, where.

## Summary

A single `@Serializable` `UserSession` (access token + refresh token + `expires_in` + `token_type` + derived `expiresAt`) is the only token-bearing type. It is JSON-encoded by `TokenStoreSessionManager` and pushed through a one-slot platform-secure store called `TokenStore` (KMP `expect`/`actual`):

- **Android** — AES‑256/GCM with the symmetric key held in **AndroidKeyStore** (alias `ar.com.ingenieriaalimentacion.auth.session_v1`, no biometric/unlock binding). The IV‑prefixed ciphertext is Base64‑encoded and written to plain `SharedPreferences` (`ar.com.ingenieriaalimentacion.auth` / key `session`). Despite the previous session's intent, AndroidX `EncryptedSharedPreferences` / `MasterKey` are **not** used — the project rolls its own AES/GCM scheme per official Google guidance after the deprecation of `androidx.security:security-crypto`.
- **iOS** — Apple **Keychain Services**, item class `kSecClassGenericPassword`, service `ar.com.ingenieriaalimentacion.auth`, account `session`, accessibility `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` (background-readable post first unlock; not synced to iCloud Keychain or device-to-device backup). Plain UTF‑8 bytes; no application-layer encryption on top of Keychain.

The HTTP client is wired with Ktor's first-party `Auth` plugin using the `bearer` provider (no custom interceptor). It pulls tokens via `SessionManager.loadSessionOrNull()` (which re-decrypts every call), and Ktor caches the resulting `BearerTokens` in memory until a 401, sign-in, or sign-out invalidates it. A separate `named("refresh")` `HttpClient` with **no Auth plugin** is used for `auth/login` and `auth/refresh` to avoid recursing through the bearer plugin. A shared `MutableStateFlow<SessionStatus>` (Supabase-shape: `Initializing` / `Authenticated(session, source)` / `NotAuthenticated(isSignOut)` / `RefreshFailure(cause)`) is mutated from both `AuthRepositoryImpl` and the Ktor `refreshTokens { ... }` block, giving the UI a single read-only source of truth.

There is **no Login UI yet** — `AppViewModel` collects the status flow eagerly and `App.kt` hardcodes `Route.Home` as the start destination, with a TODO to branch on `SessionStatus`.

## Detailed Findings

### Storage: the `TokenStore` expect/actual

#### Common `expect` (single-slot opaque string store)
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStore.kt:14-18` — `expect class TokenStore` with `suspend fun get(): String?`, `suspend fun set(value: String)`, `suspend fun clear()`. No key parameter; one entry per instance. KDoc (lines 3-13) documents that `TokenStoreSessionManager` writes JSON-serialized `UserSession` through this API.

#### Android actual — AndroidKeyStore + AES/GCM, ciphertext in `SharedPreferences`
- `composeApp/src/androidMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStore.android.kt:26` — `actual class TokenStore(context: Context)`.
- `:28` — Plain `SharedPreferences` file `ar.com.ingenieriaalimentacion.auth` (`PREFS_NAME`, `:84`).
- `:84-87` — Constants: `PREF_KEY = "session"`, `KEYSTORE_PROVIDER = "AndroidKeyStore"`, `KEY_ALIAS = "ar.com.ingenieriaalimentacion.auth.session_v1"` (the `_v1` suffix anticipates rotation).
- `:88-90` — Crypto constants: `TRANSFORMATION = "AES/GCM/NoPadding"`, `IV_BYTES = 12`, `GCM_TAG_BITS = 128`.
- `:66-81` — `loadOrCreateKey()` lazily creates a 256-bit AES key in AndroidKeyStore via `KeyGenParameterSpec` with `PURPOSE_ENCRYPT or PURPOSE_DECRYPT`, `BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE`, `setKeySize(256)`. **No `setUserAuthenticationRequired(true)`, no `setUnlockedDeviceRequired(true)`, no StrongBox attestation flag.** The key is reachable any time the app process can talk to KeyStore. This is intentional — see `thoughts/plans/auth_setup.md:67-68`: biometric gating is reserved for a future "sensitive operation" flow.
- `:45-55` — `set()`: lets `Cipher` generate a fresh random 12-byte IV, stores `Base64(iv || AES-GCM(plaintext), NO_WRAP)`.
- `:30-43` — `get()`: Base64-decode → split first 12 bytes as IV → `Cipher.DECRYPT_MODE` with `GCMParameterSpec(128, iv)`. Wrapped in `runCatching { ... }.getOrNull()`, so any decryption / GCM tag failure silently yields `null` and the caller cannot distinguish "absent" from "corrupted".
- All three operations hop to `withContext(Dispatchers.IO)`.
- ⚠ Quality note: `runCatching` here also swallows `CancellationException`, contradicting the project-wide Kotlin guideline to rethrow it (Kotlin coroutines best practice).

#### iOS actual — Keychain Services, generic password
- `composeApp/src/iosMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStore.ios.kt:56-57` — `@OptIn(ExperimentalForeignApi::class) actual class TokenStore` (no constructor parameters). KDoc (lines 53-54) credits the CF bridging pattern to Liftric KVault (MIT) — no actual library dependency, only the bridging idiom was adopted.
- `:145-146` — `SERVICE = "ar.com.ingenieriaalimentacion.auth"` (`kSecAttrService`), `ACCOUNT = "session"` (`kSecAttrAccount`).
- Item class: `kSecClassGenericPassword` (`:64, :97, :106, :127`).
- `:101` — `kSecAttrAccessible = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` set on `SecItemAdd`. Background-readable post first unlock; `ThisDeviceOnly` excludes from iCloud Keychain sync and device migration. KDoc on `:48-50` justifies this as appropriate for a session token.
- `:59-87` `get()` — `SecItemCopyMatching` with `kSecReturnData=true`, `kSecMatchLimit=kSecMatchLimitOne`. Bridges `CFTypeRef` back via `CFBridgingRelease` and decodes UTF‑8 via `NSString.create(data, NSUTF8StringEncoding)`.
- `:89-120` `set()` — `SecItemAdd`; on `errSecDuplicateItem`, falls back to `SecItemUpdate(matchQuery, attributesToUpdate)` with only `kSecValueData` (so the original `kSecAttrAccessible` is retained on overwrite).
- `:122-136` `clear()` — `SecItemDelete`; result not inspected; `errSecItemNotFound` is effectively a no‑op.
- All three operations are declared `suspend` to match the `expect` contract but are **synchronous Keychain calls on the calling dispatcher** — no `Dispatchers.Default` hop. Unlike Android, threading is the caller's responsibility.
- ⚠ Quality note: non-success `OSStatus` other than the explicitly handled ones is silently ignored on all three operations (no log, no throw).

#### Persisted shape
Both stores hold one opaque UTF‑8 string: the JSON encoding of `UserSession`. The store has no schema awareness — encoding is the consumer's job.

### Persistence layer: `SessionManager` port + adapter

- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/repository/SessionManager.kt:5-9` — interface `SessionManager` with `saveSession`, `loadSessionOrNull`, `deleteSession`. **It is not an in-memory state holder** — it is a persistence port. (Naming is Supabase-style; do not confuse with a "session manager that holds reactive state".)
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStoreSessionManager.kt:13-30` — concrete adapter. Constructor `(tokenStore: TokenStore, json: Json)`.
  - `:19` `saveSession` → `tokenStore.set(json.encodeToString(UserSession.serializer(), session))`
  - `:23-24` `loadSessionOrNull` → `tokenStore.get()`, then `json.decodeFromString` wrapped in `runCatching` (corrupt JSON yields `null`, not throw)
  - `:28` `deleteSession` → `tokenStore.clear()`
- **No in-memory cache** inside `TokenStoreSessionManager` — every `loadSessionOrNull()` call hits the platform store and re-decrypts. Caching is delegated to Ktor's bearer provider (see HTTP layer below).

### Token shape: a single `UserSession` (no separate `AuthTokens`)

- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/model/auth/UserSession.kt:9-16` — single `@Serializable data class`:
  - `accessToken: String` (`@SerialName("access_token")`)
  - `refreshToken: String` (`@SerialName("refresh_token")`)
  - `expiresIn: Long = 0L` (`@SerialName("expires_in")`)
  - `tokenType: String = "Bearer"` (`@SerialName("token_type")`)
  - `expiresAt: Instant = Clock.System.now() + expiresIn.seconds` — defaulted at instance construction. Because Ktor's `ContentNegotiation` calls the constructor during deserialization, `expiresAt` is effectively re‑anchored to "now" each fresh decode. There is no separate "issued at" field on the wire.

The wire DTO, the on-disk record, and the domain model are the same type. There is no DTO ↔ domain mapping for the session; `data/auth/mapper/` does not exist. The plan called this out (`thoughts/plans/auth_setup.md:31-32`) — supabase-kt's API shape was adopted, not its lack of at-rest encryption.

### Reactive state: a single shared `MutableStateFlow<SessionStatus>`

- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/model/auth/SessionStatus.kt:3-11` — sealed: `Initializing`, `NotAuthenticated(isSignOut)`, `RefreshFailure(cause)`, `Authenticated(session, source)`. Mirrors supabase-kt.
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/model/auth/SessionSource.kt:3-8` — `Storage` / `SignIn` / `Refresh(oldSession)` / `Unknown` — provenance tag on `Authenticated`.
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/ClientsModule.kt:18` — `single<MutableStateFlow<SessionStatus>> { MutableStateFlow(SessionStatus.Initializing) }`. **One singleton**, injected as `statusSink` into both `AuthRepositoryImpl` (`RepositoriesModule.kt:21`) and the authed `HttpClient` (`ClientsModule.kt:34`).
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/AuthRepositoryImpl.kt:27` — `sessionStatus: StateFlow<SessionStatus> = statusSink.asStateFlow()` exposes it read‑only.
- This is the seam by which the **network layer** (Ktor refresh hook) drives `RefreshFailure` and `NotAuthenticated` transitions without callbacks back into the repository.

### Token lifecycle

#### Cold start (`bootstrap`)
- `AuthRepositoryImpl.kt:29-37` — guarded by `if (statusSink.value !is Initializing) return` so it runs once. Calls `sessionManager.loadSessionOrNull()` once, then sets `statusSink` to `Authenticated(stored, SessionSource.Storage)` or `NotAuthenticated(isSignOut = false)`.
- Triggered by `AppViewModel.init { viewModelScope.launch { initializeAuth() } }` (`AppViewModel.kt:25-27`). `InitializeAuthUseCase` is a `factory` (`UseCasesModule.kt:10`).

#### Sign-in
- `AuthRepositoryImpl.kt:39-47` — `api.signIn(...)` → returns `UserSession` → `sessionManager.saveSession(session)` (`:41`) → `clearBearerCache()` (`:42`) → `statusSink.value = Authenticated(session, SessionSource.SignIn)` (`:43`). Errors funneled through `mapToAuthError` and rethrown as `AuthFailure` (`:46`).
- `AuthApi.signIn` (`composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/AuthApi.kt:19-23`) POSTs `LoginRequest(email, password)` to `auth/login` on the **no-auth** `refreshClient` — so the login itself does not carry a stale bearer.

#### Authed request (every call)
- Ktor `Auth` + `bearer` plugin installed inline in `HttpClientFactory.createAuthedClient` (`composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/network/HttpClientFactory.kt:56-82`). Imports confirm first-party plugin (`io.ktor.client.plugins.auth.{Auth,providers.{BearerTokens,bearer}}`).
- `:58-60` `loadTokens { sessionManager.loadSessionOrNull()?.let { BearerTokens(it.accessToken, it.refreshToken) } }` — the plugin caches the returned `BearerTokens` in memory after the first call.
- `:80` `sendWithoutRequest { true }` — bearer is attached pre-emptively on every request, not just after a 401 challenge.
- After sign-in / sign-out, `AuthRepositoryImpl.clearBearerCache()` (`:57-62`) reaches into `httpClient.authProviders`, finds `BearerAuthProvider`, and calls `clearToken()` so the next request re-invokes `loadTokens` and re-reads the store.

#### 401 → refresh
- `HttpClientFactory.kt:61-79` — `refreshTokens { ... }` reads the current session from `TokenStore` (`:62`), calls `authApi.refresh(current.refreshToken)` on the **no-auth `refreshClient`** (`:66`), and:
  - On success (`:67-71`): `sessionManager.saveSession(fresh)`, sets `statusSink = Authenticated(fresh, SessionSource.Refresh(current))`, returns new `BearerTokens` for the in-flight retry.
  - On failure (`:72-77`): `sessionManager.deleteSession()`, then `statusSink = RefreshFailure(...)` immediately followed by `NotAuthenticated(isSignOut = false)`.
- Ktor's bearer provider serializes concurrent 401s with an internal mutex (single-flight refresh — we don't write that ourselves).
- ⚠ There is **no proactive expiry check** using `UserSession.expiresAt`; refresh is purely 401-reactive.

#### Sign-out
- `AuthRepositoryImpl.kt:49-55`:
  1. `runCatching { httpClient.post("auth/logout") }` — best-effort, swallowed on failure (`:51`). Note: this is on the **authed** client, so logout DOES carry a bearer.
  2. `sessionManager.deleteSession()` (`:52`) — clears the platform secure store.
  3. `clearBearerCache()` (`:53`) — drops Ktor's in-memory `BearerTokens`.
  4. `statusSink.value = NotAuthenticated(isSignOut = true)` (`:54`) — flips the shared StateFlow. The `isSignOut = true` flag distinguishes a deliberate sign-out from an unauth state caused by failed refresh or cold-start with no token.
- `AuthApi` does **not** expose `signOut`/`logout` — the call lives in the repository.

### HTTP client topology — two clients by qualifier

`composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/network/HttpClientFactory.kt`:

- **Refresh client** (`:32-40`, `createRefreshClient`) — `ContentNegotiation` + `Logging` + `defaultRequest { url(ApiConfig.BASE_URL) }`. **No `Auth` plugin.** Used exclusively by `AuthApi` for `auth/login` and `auth/refresh`.
- **Authed client** (`:43-83`, `createAuthedClient`) — same plus the `Auth { bearer { ... } }` block above. This is what every other repository should inject.

Endpoint paths are inline string literals (no central registry):
- `auth/login` — `AuthApi.kt:20`
- `auth/refresh` — `AuthApi.kt:26`
- `auth/logout` — `AuthRepositoryImpl.kt:51` (on the authed client)

Base URL: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/network/ApiConfig.kt:5` — `const val BASE_URL = "https://api.example.com/"` with a `TODO` to swap to `BuildConfig` when the production host is finalized.

### DI graph

`composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/ClientsModule.kt`:
- `:15` `single<Json> { HttpClientFactory.json() }` (default).
- `:18` `single<MutableStateFlow<SessionStatus>>` (the shared sink).
- `:21-23` `single<HttpClient>(named("refresh"))` → `createRefreshClient(get<HttpClientEngine>(), get())`.
- `:25` `single { AuthApi(refreshClient = get(named("refresh"))) }` — uses the qualifier to break the `AuthApi → HttpClient → AuthApi` cycle that would otherwise exist via the auth plugin.
- `:28-36` unqualified `single<HttpClient>` → `createAuthedClient(engine, json, sessionManager, authApi, statusSink)` — this is what `get<HttpClient>()` resolves to anywhere else.

`composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/RepositoriesModule.kt`:
- `:12` `single<Json>(named("auth"))` with `ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true` — separate Json instance used by storage only.
- `:15` `single<SessionManager> { TokenStoreSessionManager(tokenStore = get(), json = get(named("auth"))) }`.
- `:16-23` `single<AuthRepository> { AuthRepositoryImpl(api = get(), sessionManager = get(), httpClient = get(), statusSink = get()) }`.

`composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/UseCasesModule.kt:9-14` — all four use cases as `factory`: `InitializeAuthUseCase`, `SignInUseCase`, `SignOutUseCase`, `ObserveSessionStatusUseCase`.

Platform modules:
- `composeApp/src/androidMain/kotlin/ar/com/ingenieriaalimentacion/di/Modules.android.kt:11` — `single<HttpClientEngine> { OkHttp.create() }`; `:12` — `single { TokenStore(context = androidContext()) }`.
- `composeApp/src/iosMain/kotlin/ar/com/ingenieriaalimentacion/di/Modules.ios.kt:10` — `single<HttpClientEngine> { Darwin.create() }`; `:11` — `single { TokenStore() }`.

Both `TokenStore` bindings are singletons.

### App-level wiring

- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/AppViewModel.kt:13` — `AppViewModel(initializeAuth: InitializeAuthUseCase, observeSessionStatus: ObserveSessionStatusUseCase)`.
- `:18-23` — `sessionStatus: StateFlow<SessionStatus> = observeSessionStatus().stateIn(viewModelScope, SharingStarted.Eagerly, SessionStatus.Initializing)`. `Eagerly` keeps the upstream hot for the VM's lifetime.
- `:25-27` — `init { viewModelScope.launch { initializeAuth() } }`. No `runCatching` around it — any throw goes to the coroutine's uncaught handler.
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/App.kt:14` — `appViewModel: AppViewModel = koinViewModel()` triggers construction (and thus `init`) on first composition.
- `:19-20` — `val sessionStatus by appViewModel.sessionStatus.collectAsStateWithLifecycle()` is `@Suppress("UNUSED_VARIABLE")` and exists only to keep the flow hot. The `// Future Login screen will branch start destination on this value` comment (lines 17-18) marks the TODO.
- `:25` — start destination is hardcoded to `Route.Home` regardless of `SessionStatus`.

## Code References

- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStore.kt:14-18` — the single-slot suspend store contract
- `composeApp/src/androidMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStore.android.kt:30-90` — Android AES-256/GCM encrypt/decrypt + key creation
- `composeApp/src/iosMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStore.ios.kt:59-136` — iOS Keychain `SecItem*` operations
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStoreSessionManager.kt:13-30` — JSON-encode/decode bridge
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/AuthRepositoryImpl.kt:20-76` — bootstrap / signIn / signOut / mapToAuthError / clearBearerCache
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/AuthApi.kt:17-30` — `signIn` and `refresh` over the no-auth client
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/network/HttpClientFactory.kt:32-83` — the two-client factory and the `bearer { loadTokens / refreshTokens / sendWithoutRequest }` block
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/network/ApiConfig.kt:5` — placeholder `BASE_URL` with `TODO`
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/model/auth/UserSession.kt:9-16` — the only token-bearing type
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/model/auth/SessionStatus.kt:3-11` — Supabase-shaped sealed status
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/ClientsModule.kt:15-36` — Json, status sink, both HttpClients, AuthApi
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/RepositoriesModule.kt:12-23` — `"auth"` Json, SessionManager, AuthRepository
- `composeApp/src/androidMain/kotlin/ar/com/ingenieriaalimentacion/di/Modules.android.kt:11-12` — Android engine + TokenStore
- `composeApp/src/iosMain/kotlin/ar/com/ingenieriaalimentacion/di/Modules.ios.kt:10-11` — iOS engine + TokenStore
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/AppViewModel.kt:13-27` — eager status collection + `initializeAuth()` on init

## Architecture Insights

1. **One persistence port, one secure slot per platform.** `TokenStore` is deliberately schema-agnostic — a single opaque `String?` slot — so the secure storage primitive stays trivial to audit per platform (~60 LOC Android, ~80 LOC iOS) and the JSON shape can evolve without touching the cinterop / KeyStore code.
2. **Domain model = wire DTO = on-disk record.** `UserSession` carries `@SerialName` annotations; there is no DTO ↔ domain mapper. Trade-off: less code, less indirection, but any change to the wire contract changes the on-disk format too. (Mitigated by the `_v1` `KEY_ALIAS` suffix on Android — rotation is anticipated. iOS would also need a new `service`/`account` if the schema breaks.)
3. **`SessionManager` ≠ in-memory state holder.** Despite the name, it is purely a persistence port. The reactive state lives in a single `MutableStateFlow<SessionStatus>` injected as `statusSink` into both the repository and the network layer. This is the seam that lets the Ktor `refreshTokens { ... }` block drive `RefreshFailure` / `NotAuthenticated` transitions without calling back into the repository.
4. **Two HttpClients by Koin qualifier, not by URL allowlist.** Login and refresh go through `named("refresh")` — a client with no `Auth` plugin — so the refresh request itself can never re-enter the bearer chain. Everything else uses the default authed client. `auth/logout`, however, intentionally goes through the authed client (it needs the bearer to invalidate server-side).
5. **Bearer cache invalidation is explicit.** Ktor caches `BearerTokens` in-memory after the first `loadTokens` call. After sign-in / sign-out, `AuthRepositoryImpl.clearBearerCache()` reaches into `httpClient.authProviders.filterIsInstance<BearerAuthProvider>().firstOrNull()?.clearToken()` to force a re-read on the next request. Without this, the next request after sign-in would either send the previous user's token or none.
6. **Refresh is reactive only.** `UserSession.expiresAt` is computed and persisted but never consulted. The Ktor `Auth` plugin will not pre-empt; it waits for a 401. This is documented as a deliberate omission in `thoughts/plans/auth_setup.md:66`.
7. **Android: the AndroidX deprecation drove a hand-rolled scheme.** `androidx.security:security-crypto` was deprecated April 2025 (per `thoughts/plans/auth_setup.md:26`). The chosen replacement is the official Google guidance — KeyGenParameterSpec → AES/GCM `Cipher` → ciphertext in plain prefs — with the AES key never leaving AndroidKeyStore (hardware-backed, StrongBox-eligible at minSdk 26+). No biometric / unlock binding so the session is readable on launch without prompts.
8. **iOS accessibility class is `AfterFirstUnlockThisDeviceOnly`, not `WhenUnlockedThisDeviceOnly`.** Background-readable post first unlock, device-bound (not iCloud-synced, not migration-restored). Suitable for a session token; would need to harden if storing biometric-gated material.

## Quality Findings (worth noting in a follow-up review)

- **`runCatching` swallows `CancellationException`** in `TokenStore.android.kt:38-42` (`get`) and `TokenStoreSessionManager.kt:23-24` (`loadSessionOrNull`). Project Kotlin guidelines require rethrowing it. Replace with `try { ... } catch (e: GeneralSecurityException) { null }` and similar on iOS for typed failure modes.
- **`get()` cannot distinguish "absent" from "corrupted"** on either platform — both return `null`. A corrupted entry will silently be treated as "no session", leading to a sign-in prompt instead of a recoverable error.
- **iOS `TokenStore` does not hop dispatchers** even though its `expect` is `suspend`. Callers run Keychain syscalls on whatever thread they're on. Add a `withContext(Dispatchers.Default)` (or expose this asymmetry in the `expect` KDoc).
- **iOS `set()` ignores non-success `OSStatus`** other than `errSecDuplicateItem`. A failure to write is invisible to the caller.
- **No proactive token-expiry refresh.** Every first authed call after token expiry incurs a 401 round-trip. Cheap to add via `loadTokens { ... if (Clock.System.now() > session.expiresAt - 30.seconds) refresh(...) ... }`.
- **`AppViewModel.init { launch { initializeAuth() } }` lacks error handling.** Any throw goes uncaught.
- **Sign-out is not atomic.** If `httpClient.post("auth/logout")` 401s and triggers a refresh that also fails, the `statusSink` will transition through `RefreshFailure → NotAuthenticated(isSignOut=false)` before `signOut()` itself sets `NotAuthenticated(isSignOut=true)` — the final state is correct but the intermediate `isSignOut=false` is observable.
- **`App.kt` does not gate navigation on `SessionStatus`.** `Route.Home` is hardcoded as start destination; `sessionStatus` is collected only to keep the flow hot. TODO is marked in code.

## Historical Context (from thoughts/)

- `thoughts/plans/auth_setup.md` — the original plan for this implementation. Particularly load-bearing sections:
  - Lines 25-32 — "every available KMP secure-storage library is dead, deprecated, or risky as of May 2026" (`androidx.security:security-crypto` deprecated April 2025, `kvault` ~31 months stale, `multiplatform-settings` ~11 months stale) → drove the decision to roll platform-native `expect`/`actual` per official Google/Apple guidance.
  - Lines 32-33 — the conscious decision to mirror supabase-kt's API shape (`SessionStatus`, `SessionManager`, `UserSession`, `SessionSource`) but **not** its lack of at-rest encryption (supabase-kt's default `SettingsSessionManager` writes to plain SharedPreferences/NSUserDefaults).
  - Lines 34-36 — Ktor `Bearer` provider serializes concurrent 401s; a separate refresh client without `Auth` is required to break recursion; bearer cache must be cleared after sign-in.
  - Lines 56-68 — the explicit "what we are NOT doing" list (no Login UI yet, no proactive refresh, no biometric gating, no third-party storage lib, no `BuildConfig` BASE_URL).

## Open Questions

- Where is the Koin `startKoin { modules(...) }` aggregation? `clientsModule`/`repositoriesModule`/`useCasesModule`/`platformModule` are declared but their composition was not seen — likely in an `AppModule.kt` plus per-platform entry points (`AlApp.kt` on Android, a `KoinHelper` on iOS), but worth verifying.
- Where is `AppViewModel` registered in Koin? `App.kt:14` calls `koinViewModel<AppViewModel>()` but no `viewModel { ... }`/`viewModelOf(::AppViewModel)` was observed in the modules read here. The original plan (`thoughts/plans/auth_setup.md:16`) says `ViewModelsModule.kt` already wires it, so this is probably not a gap, just outside the read set for this research.
- When the Login screen lands, should `sessionStatus` be collected at the `App` composable and used to swap the start destination, or should the navigation graph itself be rebuilt? The plan's TODO marker (`App.kt:17-18`) leaves this open.

---

## Follow-up Research 2026-05-02T02:17:29Z — Official Google & Apple guidance

**Method**: WebSearch + WebFetch on `developer.android.com` and `developer.apple.com`, plus Context7 (`/websites/developer_android_training`, `/websites/developer_apple`, `/openid/appauth-android`).

### TL;DR — does the current implementation match the official guidance?

| Concern | Official guidance | Current impl | Match? |
|---|---|---|---|
| **Android: replace `EncryptedSharedPreferences`** | "Deprecated all APIs in favour of existing platform APIs and direct use of Android Keystore" — AndroidX Security 1.1.0-alpha07 (Apr 2025) → 1.1.0 stable (Jul 2025) | Hand-rolled AES-256/GCM with key in AndroidKeyStore, ciphertext in plain `SharedPreferences` | ✅ Matches the post-deprecation guidance exactly |
| **Android: symmetric algorithm** | AES-256/GCM/NoPadding | AES-256/GCM/NoPadding (`TokenStore.android.kt:88`) | ✅ |
| **Android: hardware backing** | "Key material can be bound to the secure hardware of the Android device, such as the Trusted Execution Environment (TEE) or Secure Element (SE)" — automatic via AndroidKeyStore | Uses AndroidKeyStore — automatically TEE-backed where available | ✅ |
| **Android: `setUserAuthenticationRequired(true)`** | Recommended for "sensitive tokens" / credentials per the Keystore page | Not set — intentional, see plan `:67-68` (session must load on launch without prompts) | ⚠ Conscious deviation |
| **Android: `setIsStrongBoxBacked(true)`** | Recommended on supported devices (API 28+) for "applications requiring the highest level of security" | Not set | ⚠ Cheap upgrade — try with `setIsStrongBoxBacked(true)` and fall back if `StrongBoxUnavailableException` |
| **Android: `setUnlockedDeviceRequired(true)`** | Recommended for credential keys | Not set | ⚠ Conscious deviation (would break boot-completed background reads) |
| **Android: off main thread** | "apps should avoid using `AndroidKeyStore` on their main thread" | All ops use `withContext(Dispatchers.IO)` (`TokenStore.android.kt:30, 45, 57`) | ✅ |
| **iOS: item class** | `kSecClassGenericPassword` for tokens (Apple's CryptoKit sample) | `kSecClassGenericPassword` (`TokenStore.ios.kt:64`) | ✅ |
| **iOS: `kSecAttrAccessible`** | Apple's reference sample uses `kSecAttrAccessibleWhenUnlocked` | `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` | ⚠ More permissive on time (post first-unlock vs unlocked-only) but more restrictive on sync (`ThisDeviceOnly`). Justified for a session token; documented in code KDoc and plan `:31` |
| **iOS: `kSecUseDataProtectionKeychain: true`** | Apple's reference sample sets it explicitly | Not set | ⚠ No-op on iOS-only (default `true`), but if Compose Multiplatform later targets macOS this becomes load-bearing — add it now |
| **iOS: dispatcher hop** | Not explicitly required, but Keychain ops can block | None — `suspend` ops run on caller's thread (`TokenStore.ios.kt:59,89,122`) | ⚠ Add `withContext(Dispatchers.Default)` for parity with Android |

### Android — official guidance verbatim

#### Deprecation evidence
From [`Security` Jetpack release notes](https://developer.android.com/jetpack/androidx/releases/security):

> **Version 1.1.0-alpha07 (April 9, 2025)**
> Deprecated all APIs in favour of existing platform APIs and direct use of Android Keystore.

> **Version 1.1.0-beta01 (June 4, 2025)**
> Deprecated all APIs in favour of existing platform APIs and direct use of Android Keystore.

The deprecation carried through to **1.1.0 stable** (July 30, 2025). All APIs in `androidx.security:security-crypto` are deprecated with no successor library — Google's official replacement is the AndroidKeyStore platform API itself.

#### Recommended primitives
From [Android Keystore system](https://developer.android.com/privacy-and-security/keystore):

> "Key material never enters the application process. When an app performs cryptographic operations using an Android Keystore key, behind the scenes plaintext, ciphertext, and messages to be signed or verified are fed to a system process that carries out the cryptographic operations."

> "Key material can be bound to the secure hardware of the Android device, such as the Trusted Execution Environment (TEE) or Secure Element (SE). When this feature is enabled for a key, its key material is never exposed outside of secure hardware."

Recommended primitives for storing app secrets like tokens:
- **Symmetric**: AES-256, `BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE` (GCM is authenticated — no padding needed).
- **Asymmetric** (for signing JWTs etc.): EC P-256 or RSA 2048+, `SHA256withECDSA` / `SHA512withECDSA`.
- **`KeyGenParameterSpec` flags worth considering for credentials**:
  - `setUserAuthenticationRequired(true)` — gates use behind biometric / device credential.
  - `setUnlockedDeviceRequired(true)` — usable only when device is unlocked.
  - `setIsStrongBoxBacked(true)` — uses the dedicated secure element on supported devices (API 28+).

> "StrongBox is appropriate for applications requiring the highest level of security, particularly those at risk of physical tampering or side-channel attacks. However, it is slower, more resource-constrained, and supports fewer concurrent operations."

> "Because cryptographic operations may be time-consuming, apps should avoid using `AndroidKeyStore` on their main thread to ensure that the app's UI remains responsive."

#### Reference: AppAuth-Android (OpenID Foundation)
The OpenID Foundation's reference OAuth library for Android takes a notably **simpler** position — it serializes its `AuthState` to JSON and writes it to **plain** `SharedPreferences` via `AuthStateManager` (`ReentrantLock`-guarded singleton). No application-level encryption. The implicit rationale is that OAuth tokens are short-lived, refresh is server-controlled, and TLS + app sandboxing is the primary defense.

> "AuthState is designed for easy persistence as a JSON string using mechanisms like SharedPreferences or sqlite."

This is the same posture supabase-kt's `SettingsSessionManager` takes (plain SharedPreferences/NSUserDefaults). The current implementation is **stricter** than both reference libraries by adding the AndroidKeyStore-AES/GCM layer on Android and full Keychain on iOS — this is a deliberate quality choice, not a deviation from a stricter standard.

### iOS — official guidance verbatim

WebFetch couldn't render Apple's documentation pages (their docs are SPAs and the static HTML is empty), but Context7's `/websites/developer_apple` index returned the canonical Apple Swift sample code from `developer.apple.com/documentation/cryptokit/storing_cryptokit_keys_in_the_keychain`:

#### Apple's canonical "store a generic password" pattern
```swift
let query = [kSecClass: kSecClassGenericPassword,
             kSecAttrAccount: account,
             kSecAttrAccessible: kSecAttrAccessibleWhenUnlocked,
             kSecUseDataProtectionKeychain: true,
             kSecValueData: key.rawRepresentation] as [String: Any]
let status = SecItemAdd(query as CFDictionary, nil)
```

```swift
let query = [kSecClass: kSecClassGenericPassword,
             kSecAttrAccount: account,
             kSecUseDataProtectionKeychain: true,
             kSecReturnData: true] as [String: Any]
var item: CFTypeRef?
switch SecItemCopyMatching(query as CFDictionary, &item) {
case errSecSuccess: ...
case errSecItemNotFound: return nil
case let status: throw KeyStoreError("Keychain read failed: \(status.message)")
}
```

Key takeaways from the Apple sample:
1. **Item class is `kSecClassGenericPassword`** for opaque secrets like tokens — not `kSecClassInternetPassword`. Current impl is correct.
2. **`kSecUseDataProtectionKeychain: true` is set explicitly** in Apple's reference. On iOS this is the default, but on macOS the legacy file-based keychain is still the default — explicit `true` makes the same code work on both. Worth adding now if there's any chance the KMP project later targets macOS.
3. **Apple's sample uses `kSecAttrAccessibleWhenUnlocked`** — the most restrictive constant. Items become unreadable as soon as the device is locked. The current impl uses `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`, which is a deliberate trade:
   - `WhenUnlocked` would prevent the app from reading the token in any background task while the device is locked (push handlers, background refresh, BGTask).
   - `AfterFirstUnlock` keeps it readable in the background after the device's first unlock following boot.
   - `ThisDeviceOnly` excludes the item from iCloud Keychain sync and from device-to-device migration, which is appropriate for a session token tied to a specific install.
4. **Apple does not include `kSecAttrService`** in this particular sample — they identify the item by `account` alone. The current impl uses both (`SERVICE = "ar.com.ingenieriaalimentacion.auth"`, `ACCOUNT = "session"`), which is fine and conventional, but means anyone reading the codebase to compare against Apple's sample should expect the discrepancy.

#### Accessibility constants — Apple's documented spectrum
From Apple's [`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`](https://developer.apple.com/documentation/security/ksecattraccessibleafterfirstunlockthisdeviceonly) reference (per WebSearch index, full doc text not retrievable via WebFetch):

> "The data in the keychain item cannot be accessed after a restart until the device has been unlocked once by the user."

The four constants relevant to a session token:

| Constant | Locked device? | After first unlock post-boot? | iCloud sync? | Backup migration? |
|---|---|---|---|---|
| `kSecAttrAccessibleWhenUnlocked` | ❌ | ✅ | ✅ | ✅ |
| `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` | ❌ | ✅ | ❌ | ❌ |
| `kSecAttrAccessibleAfterFirstUnlock` | ✅ | ✅ | ✅ | ✅ |
| `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` ← current | ✅ | ✅ | ❌ | ❌ |

Caveat from forum reports surfaced by the WebSearch: even with `AfterFirstUnlock*`, background reads can intermittently fail with `errSecInteractionNotAllowed (-25308)` during specific background-execution windows (very early after boot, certain BGTask scenarios). If background reads matter, instrument for that error and refresh the cached token from in-memory state on retry.

### Concrete recommendations against the current code

These are minor — the current implementation is already aligned with both vendors' post-2025 guidance. Worth a small follow-up PR:

1. **iOS: add `kSecUseDataProtectionKeychain: kCFBooleanTrue`** to all three queries in `TokenStore.ios.kt`. No-op on iOS today; future-proofs against a KMP macOS target.
2. **iOS: hop dispatcher.** Wrap `get`/`set`/`clear` bodies in `withContext(Dispatchers.Default)` so callers don't pay Keychain syscall latency on whatever thread they happen to be on (Android already does this).
3. **Android: try `setIsStrongBoxBacked(true)`** in `loadOrCreateKey()` (`TokenStore.android.kt:66-81`), wrapped in `try { ... } catch (StrongBoxUnavailableException) { /* retry without */ }`. Free upgrade on devices that support it.
4. **Both: stop swallowing `CancellationException`.** Replace `runCatching { ... }.getOrNull()` in `TokenStore.android.kt:36` and `TokenStoreSessionManager.kt:23` with typed catches (`GeneralSecurityException` / `SerializationException`).
5. **Both: distinguish "absent" from "corrupted".** Today `get()` returns `null` for both. A corrupted entry should at minimum log + clear itself so the user gets a clean re-auth instead of silent re-prompts.

### Sources

- [Security | Jetpack | Android Developers](https://developer.android.com/jetpack/androidx/releases/security) — verbatim deprecation notice for `androidx.security:security-crypto` 1.1.0-alpha07/beta01
- [Android Keystore system | Android Developers](https://developer.android.com/privacy-and-security/keystore) — official post-deprecation guidance, primitives, and `KeyGenParameterSpec` flags
- [Cryptography | Security | Android Developers](https://developer.android.com/privacy-and-security/cryptography) — referenced as the canonical migration page
- [EncryptedSharedPreferences | API reference](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences) — the deprecated API
- [Sign in with biometrics | Android Training](https://developer.android.com/training/sign-in/biometric-auth) — `KeyGenParameterSpec` patterns for credential keys (via Context7 `/websites/developer_android_training`)
- [AppAuth for Android — README + AuthStateManager](https://github.com/openid/appauth-android) — OpenID Foundation reference; plain SharedPreferences for `AuthState` (via Context7 `/openid/appauth-android`)
- [Storing CryptoKit Keys in the Keychain | Apple Developer](https://developer.apple.com/documentation/cryptokit/storing_cryptokit_keys_in_the_keychain) — Apple's canonical Swift sample for `kSecClassGenericPassword` (via Context7 `/websites/developer_apple`)
- [Keychain services | Apple Developer Documentation](https://developer.apple.com/documentation/security/keychain-services)
- [Using the keychain to manage user secrets | Apple Developer](https://developer.apple.com/documentation/security/keychain_services/keychain_items/using_the_keychain_to_manage_user_secrets)
- [kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly | Apple Developer](https://developer.apple.com/documentation/security/ksecattraccessibleafterfirstunlockthisdeviceonly)
- [kSecAttrAccessibleAfterFirstUnlock | Apple Developer](https://developer.apple.com/documentation/security/ksecattraccessibleafterfirstunlock)
- [kSecAttrAccessibleWhenUnlocked | Apple Developer](https://developer.apple.com/documentation/security/ksecattraccessiblewhenunlocked)
- [kSecAttrAccessibleWhenUnlockedThisDeviceOnly | Apple Developer](https://developer.apple.com/documentation/security/ksecattraccessiblewhenunlockedthisdeviceonly)
