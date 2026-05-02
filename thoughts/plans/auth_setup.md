# Authentication Setup — Implementation Plan

## Overview

Wire end-to-end authentication plumbing into the Compose Multiplatform app: Ktor `Bearer` auth provider on the shared `HttpClient` with automatic refresh on 401, secure cross-platform token storage via platform-native `expect`/`actual` (AndroidKeyStore + AES/GCM on Android, Keychain Services on iOS — per official Google/Apple guidance, no third-party storage library), and a Supabase-style `SessionStatus` flow exposed by `AuthRepository` (and surfaced through `AppViewModel`) so future screens can react to authenticated / unauthenticated / refresh-failure transitions.

**Out of scope:** building the Login UI. This plan delivers the data + domain + DI layers and the `AppViewModel` wiring only. The first Login screen lands in a follow-up using the existing 6-file feature pattern (`presentation/feature/home/`).

## Current State Analysis

- `composeApp/build.gradle.kts:35-66` wires Ktor 3.4.2 (`core`, `content-negotiation`, `logging`, `serialization-json`) + `okhttp` (Android) + `darwin` (iOS), but **no `ktor-client-auth` dependency** is declared.
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/ClientsModule.kt:12-30` constructs a single `HttpClient(get())` with `ContentNegotiation` + `Logging(LogLevel.NONE)` only. No `defaultRequest { url(...) }`, no `Auth` plugin.
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/` exists as an empty directory — this auth feature is the first to populate the data layer.
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/{model,repository,usecase}/` are all empty package directories.
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/RepositoriesModule.kt:5-7` and `UseCasesModule.kt:5-7` are empty placeholders awaiting the first feature.
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/ViewModelsModule.kt:8-11` already wires `AppViewModel` and `HomeViewModel`.
- `composeApp/src/androidMain/kotlin/ar/com/ingenieriaalimentacion/AlApp.kt:10-12` calls `androidContext(this@AlApp)` so `android.content.Context` is resolvable from Koin via `get<Context>()` in any module.
- `composeApp/src/androidMain/kotlin/ar/com/ingenieriaalimentacion/di/Modules.android.kt:8-10` and `composeApp/src/iosMain/kotlin/ar/com/ingenieriaalimentacion/di/Modules.ios.kt:8-10` only expose `HttpClientEngine` today — these are the right `actual val platformModule` slots to provide the platform-specific `TokenStore` instances.
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/AppViewModel.kt:7-10` is a stub `ViewModel` exposing only `_initialized` — safe to extend with `sessionStatus` and `bootstrap()` without breaking callers.
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/App.kt:11-21` injects `AppViewModel` via `koinViewModel()` but does not collect any of its state; passes `Route.Home` as `startScreen` unconditionally.
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/navigation/Route.kt:6-11` has only `Route.Home`. **No `Route.Login` exists yet — and we deliberately do not add one in this plan.**

### Key Discoveries

- **Every available KMP secure-storage library is dead, deprecated, or risky as of May 2026.**
  - `androidx.security:security-crypto` (the official Google `EncryptedSharedPreferences` library) was **deprecated at 1.1.0-alpha07 in April 2025**. Source: [Security release notes](https://developer.android.com/jetpack/androidx/releases/security).
  - `com.liftric:kvault` last commit Oct 2023 (~31 months stale), last release Oct 2023.
  - `com.russhwolf:multiplatform-settings` last release Nov 2024, last commit Jun 2025 (~11 months stale), bumped to Kotlin 2.1.20 — this project is on Kotlin 2.3.20.
  - `io.github.ed-george:encrypted-shared-preferences` (community fork of JetSec) is alive but tiny (60 stars).
- **Google's official post-deprecation guidance** is to use Android KeyStore directly via `KeyGenParameterSpec` + `Cipher("AES/GCM/NoPadding")`, with the encrypted blob written to plain `SharedPreferences`. The key never leaves the hardware-backed KeyStore (and is StrongBox-eligible at minSdk 26). No new AndroidX wrapper has shipped to replace `security-crypto`. Source: [Android KeyStore system](https://developer.android.com/privacy-and-security/keystore).
- **Apple's canonical Keychain Services pattern** for a generic password item: `kSecClass=kSecClassGenericPassword`, `kSecAttrService`, `kSecAttrAccount`, `kSecValueData`, with `kSecAttrAccessible=kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` (background-readable after first unlock, device-bound so the item is not migrated by iCloud Keychain or device backup). Add path: `SecItemAdd` → on `errSecDuplicateItem` use `SecItemUpdate`. Read path: `SecItemCopyMatching` returns a +1-retained `CFDataRef` which we own (`CFBridgingRelease`). Source: [Keychain Services documentation](https://developer.apple.com/documentation/security/keychain-services).
- **supabase-kt itself does not encrypt at rest.** Its default `SettingsSessionManager` (`Auth/src/settingsMain/kotlin/io/github/jan/supabase/auth/SettingsSessionManager.kt`) calls `multiplatform-settings`'s no-arg `Settings()`, which on Android resolves to plain `SharedPreferences` and on iOS to `NSUserDefaults` — neither is encrypted. The library does not depend on `androidx.security:security-crypto` at all. The implicit rationale is "TLS-in-transit + app sandbox + server-side rotation is enough." We adopt supabase-kt's **API shape** (`SessionStatus`, `SessionManager`, `UserSession`, `SessionSource`) but **go further on at-rest protection** by writing platform-native encrypted storage per official Google/Apple guidance.
- **Supabase's session-status model is well-shaped and worth mirroring.** `SessionStatus` (sealed: `Initializing` / `Authenticated(session, source)` / `NotAuthenticated(isSignOut)` / `RefreshFailure(cause)`) plus `SessionManager` (`saveSession` / `loadSessionOrNull` / `deleteSession`) gives an idiomatic API the team is likely to recognize. Source: [supabase-kt SessionStatus.kt](https://github.com/supabase-community/supabase-kt/blob/master/Auth/src/commonMain/kotlin/io/github/jan/supabase/auth/status/SessionStatus.kt).
- **Ktor's `Bearer` auth provider handles concurrent 401s.** Internal mutex serializes refresh calls when multiple in-flight requests hit 401 simultaneously — we don't need to write single-flight logic ourselves.
- **A separate refresh `HttpClient` (without the `Auth` plugin) is required** to call `POST /auth/refresh` from inside the `refreshTokens { }` callback; otherwise a failed refresh would re-enter the same plugin and loop. This is the standard pattern with Ktor's `Bearer` auth.
- **After a successful sign-in, the cached `BearerTokens` in the auth provider are stale.** Without intervention, the very next request would either send no `Authorization` header (since `loadTokens` was already called once and returned `null`) or send the previous user's token. The fix: call `httpClient.authProviders.filterIsInstance<BearerAuthProvider>().firstOrNull()?.clearToken()` after `sessionManager.saveSession(...)` so Ktor re-invokes `loadTokens` on the next request and picks up the freshly-stored session.

## Desired End State

A buildable shared module where:

- `HttpClient` (the one injected into future repositories) automatically attaches `Authorization: Bearer <accessToken>` to every request, automatically refreshes on 401 via `POST /auth/refresh`, and signals `SessionStatus.RefreshFailure` followed by `NotAuthenticated(isSignOut = false)` when the refresh itself fails.
- A separate `refreshClient` (named Koin qualifier `"refresh"`) is available for the few endpoints that must not carry `Authorization` (login, refresh) — and is not exposed broadly to feature code.
- `AuthRepository` exposes `sessionStatus: StateFlow<SessionStatus>`, `signIn(Credentials): Result<UserSession>`, `signOut()`, and `bootstrap()`.
- `AppViewModel` exposes `sessionStatus: StateFlow<SessionStatus>` (collected from the repository) and calls `InitializeAuthUseCase` (which calls `bootstrap()`) inside `init { }` so the app resolves `Initializing → Authenticated|NotAuthenticated` before the first feature screen makes a network call.
- All wired through Koin (`clientsModule`, `repositoriesModule`, `useCasesModule`, plus per-platform additions in `Modules.android.kt` / `Modules.ios.kt`).

### Verification

- `./scripts/compile.sh` exits 0 with no warnings (commonMain type-check via `:composeApp:compileCommonMainKotlinMetadata`).
- `./gradlew :composeApp:compileDebugKotlinAndroid --no-daemon` exits 0 (Android-side compile).
- `./gradlew :composeApp:compileKotlinIosSimulatorArm64 --no-daemon` exits 0 (iOS-side compile).
- `./gradlew :composeApp:assembleDebug --no-daemon` produces an APK with no new warnings related to auth wiring.
- Manual: app still launches and renders `HomeScreen` (no behavior regression — auth state is observed but not yet used to gate navigation).

## What We're NOT Doing

- ❌ No Login / Sign-up / Forgot-password UI — `AppViewModel` exposes `sessionStatus` but `App.kt` keeps `Route.Home` as `startScreen` for now. Feature lands later.
- ❌ No `/me` endpoint or `UserInfo` fetch — `UserSession.user` field stays `null`.
- ❌ No OAuth / social / anonymous sign-in — `Credentials(email, password)` only.
- ❌ No discrete `AuthEvent` stream (Supabase has one in addition to `SessionStatus`); `SessionStatus.RefreshFailure` is sufficient signal for now.
- ❌ No `BuildConfig` / build-time injection of `BASE_URL` — `data/network/ApiConfig.kt` ships a placeholder constant with a `TODO` for later swap.
- ❌ **No third-party storage library** (no `androidx.security:security-crypto`, no `kvault`, no `multiplatform-settings`). All three are deprecated, stale, or both as of May 2026 — see Key Discoveries. We use platform-native APIs directly via `expect`/`actual` per official Google/Apple guidance.
- ❌ No certificate pinning, no `network_security_config.xml` cleartext lockdown — both belong in a hardening pass after the API host is finalised.
- ❌ No tests in this plan — added per feature in a follow-up using `kotlin-test` + Turbine + `kotlinx-coroutines-test` (per `~/.claude/rules/kotlin/testing.md`). The interfaces (`SessionManager`, `AuthRepository`, `TokenStore`) are designed for fakes (`MemorySessionManager` ships from the start to enable that).
- ❌ No automatic token-expiry pre-emptive refresh — we rely on the 401 → refresh round-trip. `UserSession.expiresAt` is computed and stored but not yet consulted.
- ❌ No deep-link handling for OAuth callbacks.
- ❌ No `setUserAuthenticationRequired(true)` (biometric gating) on the Android KeyStore key. The session token must be readable on app launch without a prompt; biometric gating belongs to a separate "sensitive operation" flow if/when that arrives.

## Implementation Approach

Top-down API surface, bottom-up wiring, in five compileable phases. After each phase the project must type-check via `./scripts/compile.sh`. Each phase has a single concern so a failure is easy to localize.

**Storage strategy:** a small `expect class TokenStore { suspend fun get/set/clear }` in commonMain, with platform `actual` implementations using:
- **Android** — `AndroidKeyStore`-backed AES-256/GCM key + ciphertext stored as Base64 in plain `SharedPreferences`. Per official Google guidance after the `androidx.security:security-crypto` deprecation. ~70 lines.
- **iOS** — Keychain Services (`SecItemAdd` / `SecItemCopyMatching` / `SecItemUpdate` / `SecItemDelete`) with `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`. Per official Apple guidance. ~80 lines.

Total platform code: ~150 LOC owned by us, zero external storage dependencies. Only one dependency added: `ktor-client-auth`.

The Supabase-shaped naming (`UserSession` / `SessionStatus` / `SessionManager` / `signIn` / `signOut` / `bootstrap`) is intentional: the team can lift the patterns straight from `supabase-kt` documentation when extending (e.g., adding `AuthEvent`, OAuth providers, or `/me` retrieval) later.

---

## Phase 1: Dependencies + Domain Layer

### Overview
Add the single new dependency (`ktor-client-auth`), declare `expect class TokenStore` in commonMain, and create the full domain surface (data classes, sealed interfaces, repository interfaces) so subsequent phases compile against stable types. No third-party storage library — platform actuals come in Phase 2.

### Changes Required

#### 1.1 Version catalog

**File**: `gradle/libs.versions.toml`

Add under `[libraries]` (in the Ktor block):
```toml
ktor-client-auth = { module = "io.ktor:ktor-client-auth", version.ref = "ktor" }
```

> No new `[versions]` entries. Storage uses only platform-bundled APIs (Android KeyStore + iOS Keychain Services).

#### 1.2 commonMain dependencies

**File**: `composeApp/build.gradle.kts`

In `commonMain.dependencies { ... }` (around line 39–66) add:
```kotlin
implementation(libs.ktor.client.auth)
```

#### 1.3 `expect class TokenStore`

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStore.kt`
```kotlin
package ar.com.ingenieriaalimentacion.data.auth.storage

/**
 * Platform-backed secure single-string store.
 *
 * - Android `actual`: AES-256/GCM key in `AndroidKeyStore`; ciphertext written to plain
 *   `SharedPreferences`. Per [Android KeyStore guidance](https://developer.android.com/privacy-and-security/keystore).
 * - iOS `actual`: Keychain Services `kSecClassGenericPassword` item with
 *   `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`. Per [Apple Keychain Services](https://developer.apple.com/documentation/security/keychain-services).
 *
 * Used by [SessionManager] to persist a JSON-serialized [UserSession]. One key per platform store.
 */
expect class TokenStore {
    suspend fun get(): String?
    suspend fun set(value: String)
    suspend fun clear()
}
```

#### 1.4 Domain models

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/model/auth/UserSession.kt`
```kotlin
package ar.com.ingenieriaalimentacion.domain.model.auth

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

@Serializable
data class UserSession(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 0L,
    @SerialName("token_type") val tokenType: String = "Bearer",
    val expiresAt: Instant = Clock.System.now() + expiresIn.seconds,
)
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/model/auth/SessionSource.kt`
```kotlin
package ar.com.ingenieriaalimentacion.domain.model.auth

sealed interface SessionSource {
    data object Storage : SessionSource
    data object SignIn : SessionSource
    data class Refresh(val oldSession: UserSession) : SessionSource
    data object Unknown : SessionSource
}
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/model/auth/SessionStatus.kt`
```kotlin
package ar.com.ingenieriaalimentacion.domain.model.auth

sealed interface SessionStatus {
    data object Initializing : SessionStatus
    data class NotAuthenticated(val isSignOut: Boolean = false) : SessionStatus
    data class RefreshFailure(val cause: String) : SessionStatus
    data class Authenticated(
        val session: UserSession,
        val source: SessionSource = SessionSource.Unknown,
    ) : SessionStatus
}
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/model/auth/Credentials.kt`
```kotlin
package ar.com.ingenieriaalimentacion.domain.model.auth

data class Credentials(val email: String, val password: String)
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/model/auth/AuthError.kt`
```kotlin
package ar.com.ingenieriaalimentacion.domain.model.auth

sealed interface AuthError {
    data object InvalidCredentials : AuthError
    data object Network : AuthError
    data class Unknown(val cause: Throwable? = null) : AuthError
}
```

#### 1.5 Domain repository interfaces

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/repository/SessionManager.kt`
```kotlin
package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession

interface SessionManager {
    suspend fun saveSession(session: UserSession)
    suspend fun loadSessionOrNull(): UserSession?
    suspend fun deleteSession()
}
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/repository/AuthRepository.kt`
```kotlin
package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.auth.AuthError
import ar.com.ingenieriaalimentacion.domain.model.auth.Credentials
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val sessionStatus: StateFlow<SessionStatus>
    suspend fun bootstrap()
    suspend fun signIn(credentials: Credentials): Result<UserSession>
    suspend fun signOut()
}

sealed class AuthException(message: String) : Exception(message) {
    data object InvalidCredentials : AuthException("Invalid credentials")
    data object Network : AuthException("Network error")
    data class Unknown(val original: Throwable) : AuthException(original.message ?: "Unknown auth error")
}

fun AuthException.toError(): AuthError = when (this) {
    AuthException.InvalidCredentials -> AuthError.InvalidCredentials
    AuthException.Network -> AuthError.Network
    is AuthException.Unknown -> AuthError.Unknown(original)
}
```

### Success Criteria

#### Automated Verification
- [ ] `./scripts/compile.sh` exits 0
- [ ] `gradle/libs.versions.toml` declares `ktor-client-auth` (no `kvault`, no `multiplatform-settings`, no `androidx.security`)
- [ ] All files under `domain/model/auth/` and `domain/repository/` exist as listed
- [ ] `expect class TokenStore` exists at `data/auth/storage/TokenStore.kt`

#### Manual Verification
- [ ] `grep -r "ktor-client-auth" composeApp/build.gradle.kts` returns the new dependency line
- [ ] No new warnings printed by `./scripts/compile.sh`
- [ ] Compilation will fail at this phase end (no `actual` for `TokenStore` yet) — that's expected; Phase 2 lands the actuals. Run only `./scripts/compile.sh` (commonMain metadata) here, not the platform compiles.

---

## Phase 2: Platform-Native Token Storage + `SessionManager`

### Overview
Land the two `actual class TokenStore` implementations (Android KeyStore + AES/GCM, iOS Keychain Services) per official Google/Apple guidance. Implement `TokenStoreSessionManager` in commonMain that JSON-encodes `UserSession` and delegates to `TokenStore`. Provide a `MemorySessionManager` for tests (mirrors Supabase's `MemorySessionManager`). Bind via Koin.

### Changes Required

#### 2.1 Android `actual class TokenStore` — AndroidKeyStore + AES/GCM

**File**: `composeApp/src/androidMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStore.android.kt`
```kotlin
package ar.com.ingenieriaalimentacion.data.auth.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android implementation following the official Android KeyStore guidance:
 * https://developer.android.com/privacy-and-security/keystore
 *
 * AES-256/GCM/NoPadding. The key never leaves the AndroidKeyStore (hardware-backed where
 * available; StrongBox-eligible at minSdk 26+). Per-encryption random 12-byte IV is
 * generated by the `Cipher` and prepended to the ciphertext before storage.
 *
 * The encrypted blob is stored as Base64 in plain `SharedPreferences` — safe because the
 * key it was encrypted with is never exposed to userland.
 */
actual class TokenStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    actual suspend fun get(): String? = withContext(Dispatchers.IO) {
        val combined = prefs.getString(PREF_KEY, null) ?: return@withContext null
        val bytes = runCatching { Base64.decode(combined, Base64.NO_WRAP) }.getOrNull()
            ?: return@withContext null
        if (bytes.size < IV_BYTES + 1) return@withContext null
        val iv = bytes.copyOfRange(0, IV_BYTES)
        val ciphertext = bytes.copyOfRange(IV_BYTES, bytes.size)
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, loadKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            }
            cipher.doFinal(ciphertext).decodeToString()
        }.getOrNull()
    }

    actual suspend fun set(value: String): Unit = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, loadOrCreateKey())
        }
        val iv = cipher.iv  // GCM emits a fresh 12-byte IV per init
        val ciphertext = cipher.doFinal(value.encodeToByteArray())
        val combined = iv + ciphertext
        prefs.edit()
            .putString(PREF_KEY, Base64.encodeToString(combined, Base64.NO_WRAP))
            .apply()
    }

    actual suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        prefs.edit().remove(PREF_KEY).apply()
    }

    private fun loadKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        return ks.getKey(KEY_ALIAS, null) as SecretKey
    }

    private fun loadOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        ks.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "ar.com.ingenieriaalimentacion.auth"
        const val PREF_KEY = "session"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "ar.com.ingenieriaalimentacion.auth.session_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12        // GCM standard
        const val GCM_TAG_BITS = 128
    }
}
```

#### 2.2 iOS `actual class TokenStore` — Keychain Services

**File**: `composeApp/src/iosMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStore.ios.kt`
```kotlin
package ar.com.ingenieriaalimentacion.data.auth.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFAutorelease
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * iOS implementation following Apple's official Keychain Services guidance:
 * https://developer.apple.com/documentation/security/keychain-services
 *
 * - Item class: `kSecClassGenericPassword`
 * - Identity: (`kSecAttrService`, `kSecAttrAccount`) tuple
 * - Accessibility: `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` — readable in
 *   background after first unlock; bound to this device (excluded from iCloud Keychain
 *   and device-to-device backup migration), which is correct for a session token.
 * - Add path: `SecItemAdd`; on `errSecDuplicateItem` fall back to `SecItemUpdate`.
 *
 * CF bridging pattern adapted from Liftric KVault (MIT-licensed):
 * https://github.com/Liftric/KVault/blob/master/src/iosMain/kotlin/com/liftric/kvault/impl/KVaultImpl.kt
 */
@OptIn(ExperimentalForeignApi::class)
actual class TokenStore {

    actual suspend fun get(): String? {
        val service = CFBridgingRetain(SERVICE)
        val account = CFBridgingRetain(ACCOUNT)
        try {
            val query = createDict(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to service,
                kSecAttrAccount to account,
                kSecReturnData to kCFBooleanTrue,
                kSecMatchLimit to kSecMatchLimitOne,
            )
            return memScoped {
                val result = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(query, result.ptr)
                when (status) {
                    errSecSuccess -> {
                        // CFBridgingRelease takes ownership of the +1 retained CFDataRef.
                        (CFBridgingRelease(result.value) as? NSData)?.let {
                            NSString.create(it, NSUTF8StringEncoding) as String?
                        }
                    }
                    errSecItemNotFound -> null
                    else -> null  // Surfacing the OSStatus would require a logger; log-line TODO.
                }
            }
        } finally {
            CFBridgingRelease(service)
            CFBridgingRelease(account)
        }
    }

    actual suspend fun set(value: String) {
        val service = CFBridgingRetain(SERVICE)
        val account = CFBridgingRetain(ACCOUNT)
        val data = CFBridgingRetain(
            NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding),
        )
        try {
            val addQuery = createDict(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to service,
                kSecAttrAccount to account,
                kSecValueData to data,
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            )
            val addStatus = SecItemAdd(addQuery, null)
            if (addStatus == errSecDuplicateItem) {
                // Item exists — update its value.
                val matchQuery = createDict(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrService to service,
                    kSecAttrAccount to account,
                )
                val attributesToUpdate = createDict(
                    kSecValueData to data,
                )
                SecItemUpdate(matchQuery, attributesToUpdate)
            }
        } finally {
            CFBridgingRelease(service)
            CFBridgingRelease(account)
            CFBridgingRelease(data)
        }
    }

    actual suspend fun clear() {
        val service = CFBridgingRetain(SERVICE)
        val account = CFBridgingRetain(ACCOUNT)
        try {
            val query = createDict(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to service,
                kSecAttrAccount to account,
            )
            SecItemDelete(query)
        } finally {
            CFBridgingRelease(service)
            CFBridgingRelease(account)
        }
    }

    private fun createDict(vararg pairs: Pair<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
        val dict = CFDictionaryCreateMutable(null, pairs.size.convert(), null, null)
        pairs.forEach { (k, v) -> CFDictionaryAddValue(dict, k, v) }
        return dict?.also { CFAutorelease(it) }
    }

    private companion object {
        const val SERVICE = "ar.com.ingenieriaalimentacion.auth"
        const val ACCOUNT = "session"
    }
}
```

#### 2.3 Common `TokenStoreSessionManager`

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStoreSessionManager.kt`
```kotlin
package ar.com.ingenieriaalimentacion.data.auth.storage

import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession
import ar.com.ingenieriaalimentacion.domain.repository.SessionManager
import kotlinx.serialization.json.Json

/**
 * `SessionManager` that JSON-serializes [UserSession] into a single-string [TokenStore].
 *
 * The platform `TokenStore` provides at-rest encryption (AndroidKeyStore on Android,
 * Keychain on iOS), so this class is purely a serialization adapter.
 */
class TokenStoreSessionManager(
    private val tokenStore: TokenStore,
    private val json: Json,
) : SessionManager {

    override suspend fun saveSession(session: UserSession) {
        tokenStore.set(json.encodeToString(UserSession.serializer(), session))
    }

    override suspend fun loadSessionOrNull(): UserSession? {
        val raw = tokenStore.get() ?: return null
        return runCatching { json.decodeFromString(UserSession.serializer(), raw) }.getOrNull()
    }

    override suspend fun deleteSession() {
        tokenStore.clear()
    }
}
```

#### 2.4 In-memory `SessionManager` (for tests; mirrors supabase-kt's `MemorySessionManager`)

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/MemorySessionManager.kt`
```kotlin
package ar.com.ingenieriaalimentacion.data.auth.storage

import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession
import ar.com.ingenieriaalimentacion.domain.repository.SessionManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MemorySessionManager(initial: UserSession? = null) : SessionManager {
    private val mutex = Mutex()
    private var session: UserSession? = initial

    override suspend fun saveSession(session: UserSession) = mutex.withLock { this.session = session }
    override suspend fun loadSessionOrNull(): UserSession? = mutex.withLock { session }
    override suspend fun deleteSession() = mutex.withLock { session = null }
}
```

#### 2.5 Android `TokenStore` provider via Koin

**File**: `composeApp/src/androidMain/kotlin/ar/com/ingenieriaalimentacion/di/Modules.android.kt` (modify)
```kotlin
package ar.com.ingenieriaalimentacion.di

import ar.com.ingenieriaalimentacion.data.auth.storage.TokenStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<HttpClientEngine> { OkHttp.create() }
    single { TokenStore(context = androidContext()) }
}
```

#### 2.6 iOS `TokenStore` provider via Koin

**File**: `composeApp/src/iosMain/kotlin/ar/com/ingenieriaalimentacion/di/Modules.ios.kt` (modify)
```kotlin
package ar.com.ingenieriaalimentacion.di

import ar.com.ingenieriaalimentacion.data.auth.storage.TokenStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<HttpClientEngine> { Darwin.create() }
    single { TokenStore() }
}
```

#### 2.7 Bind `SessionManager` in Koin

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/RepositoriesModule.kt` (modify)
```kotlin
package ar.com.ingenieriaalimentacion.di

import ar.com.ingenieriaalimentacion.data.auth.storage.TokenStoreSessionManager
import ar.com.ingenieriaalimentacion.domain.repository.SessionManager
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module

val repositoriesModule = module {
    single<Json>(named("auth")) {
        Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    }
    single<SessionManager> { TokenStoreSessionManager(tokenStore = get(), json = get(named("auth"))) }
}
```

### Success Criteria

#### Automated Verification
- [ ] `./scripts/compile.sh` exits 0
- [ ] `./gradlew :composeApp:compileDebugKotlinAndroid --no-daemon` exits 0
- [ ] `./gradlew :composeApp:compileKotlinIosSimulatorArm64 --no-daemon` exits 0
- [ ] `TokenStore` has exactly two `actual class` declarations (Android + iOS); `grep -r "actual class TokenStore" composeApp/src` returns 2 matches
- [ ] No `androidx.security`, `kvault`, or `multiplatform-settings` imports anywhere; `grep -r -E "(androidx\.security|liftric|russhwolf)" composeApp/src` returns 0 matches

#### Manual Verification
- [ ] Save → relaunch app → load returns the same JSON string (verify by injecting `TokenStore` into a temporary debug button; confirm round-trip on both Android emulator and iOS simulator)
- [ ] On Android, calling `set` twice with different values then `get` returns the second value (proves IV isn't being reused)
- [ ] On iOS, after `clear()`, `get()` returns `null` (verifies `SecItemDelete` actually removed the item)
- [ ] On Android, app uninstall + reinstall returns `null` from `get()` (KeyStore key is purged with the app)
- [ ] On iOS, the Keychain item is bound to this device only — appears in Keychain Access if you peek with the simulator's Keychain (manual sanity check)

---

## Phase 3: Network Layer (`ApiConfig`, `HttpClientFactory`, refresh client + authed client)

### Overview
Replace the single `HttpClient` factory in `ClientsModule.kt` with two clients: a no-auth `refreshClient` (named `"refresh"`) used only by `AuthApi`, and the main authed `HttpClient` (default singleton) carrying the `Auth` plugin with `Bearer` provider. Token I/O delegates to `SessionManager`.

### Changes Required

#### 3.1 Base URL placeholder

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/network/ApiConfig.kt`
```kotlin
package ar.com.ingenieriaalimentacion.data.network

object ApiConfig {
    // TODO: swap to BuildConfig / generated constant once the production host is finalised.
    const val BASE_URL = "https://api.example.com/"
}
```

#### 3.2 Auth DTOs

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/dto/LoginRequest.kt`
```kotlin
package ar.com.ingenieriaalimentacion.data.auth.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/dto/RefreshRequest.kt`
```kotlin
package ar.com.ingenieriaalimentacion.data.auth.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)
```

`UserSession` itself doubles as the response DTO (already `@Serializable` with `@SerialName` snake_case fields), so no separate `TokenResponse` is needed.

#### 3.3 `AuthApi` (uses `refreshClient` only)

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/AuthApi.kt`
```kotlin
package ar.com.ingenieriaalimentacion.data.auth

import ar.com.ingenieriaalimentacion.data.auth.dto.LoginRequest
import ar.com.ingenieriaalimentacion.data.auth.dto.RefreshRequest
import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class AuthApi(private val refreshClient: HttpClient) {

    suspend fun signIn(email: String, password: String): UserSession =
        refreshClient.post("auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }.body()

    suspend fun refresh(refreshToken: String): UserSession =
        refreshClient.post("auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken))
        }.body()
}
```

#### 3.4 `HttpClientFactory` (builds both clients)

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/network/HttpClientFactory.kt`
```kotlin
package ar.com.ingenieriaalimentacion.data.network

import ar.com.ingenieriaalimentacion.data.auth.AuthApi
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionSource
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession
import ar.com.ingenieriaalimentacion.domain.repository.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json

object HttpClientFactory {

    fun json(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /** No `Auth` plugin — used by [AuthApi] for /auth/login and /auth/refresh. */
    fun createRefreshClient(engine: HttpClientEngine, json: Json): HttpClient =
        HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
            install(Logging) {
                logger = object : Logger { override fun log(message: String) = println(message) }
                level = LogLevel.NONE
            }
            defaultRequest { url(ApiConfig.BASE_URL) }
        }

    /** Main client with Bearer auth + automatic refresh. */
    fun createAuthedClient(
        engine: HttpClientEngine,
        json: Json,
        sessionManager: SessionManager,
        authApi: AuthApi,
        statusSink: MutableStateFlow<SessionStatus>,
    ): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
        install(Logging) {
            logger = object : Logger { override fun log(message: String) = println(message) }
            level = LogLevel.NONE
        }
        defaultRequest { url(ApiConfig.BASE_URL) }
        install(Auth) {
            bearer {
                loadTokens {
                    sessionManager.loadSessionOrNull()?.let { BearerTokens(it.accessToken, it.refreshToken) }
                }
                refreshTokens {
                    val current = sessionManager.loadSessionOrNull() ?: run {
                        statusSink.value = SessionStatus.NotAuthenticated(isSignOut = false)
                        return@refreshTokens null
                    }
                    runCatching { authApi.refresh(current.refreshToken) }.fold(
                        onSuccess = { fresh: UserSession ->
                            sessionManager.saveSession(fresh)
                            statusSink.value = SessionStatus.Authenticated(fresh, SessionSource.Refresh(current))
                            BearerTokens(fresh.accessToken, fresh.refreshToken)
                        },
                        onFailure = { cause ->
                            sessionManager.deleteSession()
                            statusSink.value = SessionStatus.RefreshFailure(cause.message ?: "refresh failed")
                            statusSink.value = SessionStatus.NotAuthenticated(isSignOut = false)
                            null
                        },
                    )
                }
                sendWithoutRequest { true }
            }
        }
    }
}
```

#### 3.5 Refactor `ClientsModule.kt`

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/ClientsModule.kt` (rewrite)
```kotlin
package ar.com.ingenieriaalimentacion.di

import ar.com.ingenieriaalimentacion.data.auth.AuthApi
import ar.com.ingenieriaalimentacion.data.network.HttpClientFactory
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.repository.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module

val clientsModule = module {
    single<Json> { HttpClientFactory.json() }

    // Internal mutable status sink shared by HttpClient (refresh path) and AuthRepository.
    single<MutableStateFlow<SessionStatus>> { MutableStateFlow(SessionStatus.Initializing) }

    // Refresh client (no Auth plugin) — used by AuthApi only.
    single<HttpClient>(named("refresh")) {
        HttpClientFactory.createRefreshClient(get<HttpClientEngine>(), get())
    }

    single { AuthApi(refreshClient = get(named("refresh"))) }

    // Main authed client.
    single<HttpClient> {
        HttpClientFactory.createAuthedClient(
            engine = get<HttpClientEngine>(),
            json = get(),
            sessionManager = get<SessionManager>(),
            authApi = get(),
            statusSink = get(),
        )
    }
}
```

> **Module ordering note:** `clientsModule` depends on `SessionManager` (declared in `repositoriesModule`). `InitKoin.kt:13-20` already loads `repositoriesModule` after `clientsModule` — Koin resolves cross-module bindings lazily, so this works. No reordering needed.

### Success Criteria

#### Automated Verification
- [ ] `./scripts/compile.sh` exits 0
- [ ] `./gradlew :composeApp:compileDebugKotlinAndroid --no-daemon` exits 0
- [ ] `./gradlew :composeApp:compileKotlinIosSimulatorArm64 --no-daemon` exits 0
- [ ] `grep -n "ApiConfig.BASE_URL" composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/network/HttpClientFactory.kt` returns two matches (one per client)

#### Manual Verification
- [ ] App still launches on Android emulator (no Koin resolution failures at startup)
- [ ] No `Authorization` header appears in `Logging` output for `/auth/login` or `/auth/refresh` calls (verifiable by flipping `LogLevel.NONE` → `LogLevel.HEADERS` temporarily)

---

## Phase 4: `AuthRepository` Implementation + Use Cases

### Overview
Implement `AuthRepositoryImpl` against `AuthApi`, `SessionManager`, the shared `MutableStateFlow<SessionStatus>`, and the authed `HttpClient` (used for sign-out + cache invalidation). Add four use cases. Wire everything into Koin.

### Changes Required

#### 4.1 `AuthRepositoryImpl`

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/AuthRepositoryImpl.kt`
```kotlin
package ar.com.ingenieriaalimentacion.data.auth

import ar.com.ingenieriaalimentacion.domain.model.auth.AuthError
import ar.com.ingenieriaalimentacion.domain.model.auth.Credentials
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionSource
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession
import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository
import ar.com.ingenieriaalimentacion.domain.repository.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.plugin
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepositoryImpl(
    private val api: AuthApi,
    private val sessionManager: SessionManager,
    private val httpClient: HttpClient,
    private val statusSink: MutableStateFlow<SessionStatus>,
) : AuthRepository {

    override val sessionStatus: StateFlow<SessionStatus> = statusSink.asStateFlow()

    override suspend fun bootstrap() {
        if (statusSink.value !is SessionStatus.Initializing) return
        val stored = sessionManager.loadSessionOrNull()
        statusSink.value = if (stored != null) {
            SessionStatus.Authenticated(stored, SessionSource.Storage)
        } else {
            SessionStatus.NotAuthenticated(isSignOut = false)
        }
    }

    override suspend fun signIn(credentials: Credentials): Result<UserSession> = runCatching {
        val session = api.signIn(credentials.email, credentials.password)
        sessionManager.saveSession(session)
        clearBearerCache()
        statusSink.value = SessionStatus.Authenticated(session, SessionSource.SignIn)
        session
    }.recoverCatching { throwable ->
        throw mapToAuthError(throwable)
    }

    override suspend fun signOut() {
        // Best-effort server-side invalidation; always clear local state.
        runCatching { httpClient.post("auth/logout") }
        sessionManager.deleteSession()
        clearBearerCache()
        statusSink.value = SessionStatus.NotAuthenticated(isSignOut = true)
    }

    private fun clearBearerCache() {
        httpClient.plugin(Auth).providers
            .filterIsInstance<BearerAuthProvider>()
            .firstOrNull()
            ?.clearToken()
    }

    private fun mapToAuthError(throwable: Throwable): Throwable = when {
        throwable is ResponseException && throwable.response.status == HttpStatusCode.Unauthorized ->
            IllegalStateException(AuthError.InvalidCredentials.toString(), throwable)
        throwable is ResponseException ->
            IllegalStateException(AuthError.Network.toString(), throwable)
        else -> IllegalStateException(AuthError.Unknown(throwable).toString(), throwable)
    }
}
```

> **Note on error mapping:** the production-grade refactor maps to a sealed `AuthException` in `domain/repository/AuthRepository.kt`; the `IllegalStateException` wrap above is a placeholder so use cases can still return `Result.failure(...)` with a typed cause. The follow-up Login feature will type-narrow this.

#### 4.2 Use cases

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/usecase/auth/InitializeAuthUseCase.kt`
```kotlin
package ar.com.ingenieriaalimentacion.domain.usecase.auth

import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository

class InitializeAuthUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke() = repository.bootstrap()
}
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/usecase/auth/SignInUseCase.kt`
```kotlin
package ar.com.ingenieriaalimentacion.domain.usecase.auth

import ar.com.ingenieriaalimentacion.domain.model.auth.Credentials
import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession
import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository

class SignInUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(credentials: Credentials): Result<UserSession> =
        repository.signIn(credentials)
}
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/usecase/auth/SignOutUseCase.kt`
```kotlin
package ar.com.ingenieriaalimentacion.domain.usecase.auth

import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository

class SignOutUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke() = repository.signOut()
}
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/usecase/auth/ObserveSessionStatusUseCase.kt`
```kotlin
package ar.com.ingenieriaalimentacion.domain.usecase.auth

import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository
import kotlinx.coroutines.flow.StateFlow

class ObserveSessionStatusUseCase(private val repository: AuthRepository) {
    operator fun invoke(): StateFlow<SessionStatus> = repository.sessionStatus
}
```

#### 4.3 DI wiring

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/RepositoriesModule.kt` (extend Phase 2)
```kotlin
package ar.com.ingenieriaalimentacion.di

import ar.com.ingenieriaalimentacion.data.auth.AuthRepositoryImpl
import ar.com.ingenieriaalimentacion.data.auth.storage.TokenStoreSessionManager
import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository
import ar.com.ingenieriaalimentacion.domain.repository.SessionManager
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module

val repositoriesModule = module {
    single<Json>(named("auth")) {
        Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    }
    single<SessionManager> { TokenStoreSessionManager(tokenStore = get(), json = get(named("auth"))) }
    single<AuthRepository> {
        AuthRepositoryImpl(
            api = get(),
            sessionManager = get(),
            httpClient = get(),
            statusSink = get(),
        )
    }
}
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/UseCasesModule.kt` (rewrite)
```kotlin
package ar.com.ingenieriaalimentacion.di

import ar.com.ingenieriaalimentacion.domain.usecase.auth.InitializeAuthUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.ObserveSessionStatusUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.SignInUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.SignOutUseCase
import org.koin.dsl.module

val useCasesModule = module {
    factory { InitializeAuthUseCase(get()) }
    factory { SignInUseCase(get()) }
    factory { SignOutUseCase(get()) }
    factory { ObserveSessionStatusUseCase(get()) }
}
```

### Success Criteria

#### Automated Verification
- [ ] `./scripts/compile.sh` exits 0
- [ ] `./gradlew :composeApp:compileDebugKotlinAndroid --no-daemon` exits 0
- [ ] `./gradlew :composeApp:compileKotlinIosSimulatorArm64 --no-daemon` exits 0
- [ ] `AuthRepository`, `SessionManager`, `AuthApi`, and the four use cases all resolve from Koin (no `NoBeanDefFoundException` thrown by `getKoinInstance().get<...>()` smoke calls)

#### Manual Verification
- [ ] Calling `signIn` against a stub backend updates `sessionStatus` to `Authenticated`
- [ ] Calling `signOut` updates `sessionStatus` to `NotAuthenticated(isSignOut = true)`
- [ ] Killing + relaunching the app preserves session and `bootstrap()` resolves to `Authenticated`

---

## Phase 5: `AppViewModel` + `App` Wiring

### Overview
Surface `sessionStatus` from `AppViewModel` and trigger `bootstrap()` once on construction. `App.kt` collects the flow but does not yet alter routing — the future Login feature will branch on it. This is the smallest change that puts auth state in front of UI code.

### Changes Required

#### 5.1 `AppViewModel`

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/AppViewModel.kt` (rewrite)
```kotlin
package ar.com.ingenieriaalimentacion.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.usecase.auth.InitializeAuthUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.ObserveSessionStatusUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(
    initializeAuth: InitializeAuthUseCase,
    observeSessionStatus: ObserveSessionStatusUseCase,
) : ViewModel() {

    val sessionStatus: StateFlow<SessionStatus> = observeSessionStatus()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SessionStatus.Initializing,
        )

    init {
        viewModelScope.launch { initializeAuth() }
    }
}
```

#### 5.2 `App.kt`

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/App.kt` (modify)
```kotlin
package ar.com.ingenieriaalimentacion.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App(
    appViewModel: AppViewModel = koinViewModel(),
    navController: NavHostController = rememberNavController(),
) {
    // Eagerly collect so the SessionStatus flow stays subscribed for the lifetime of App.
    // Future Login screen will branch start destination on this value.
    val sessionStatus by appViewModel.sessionStatus.collectAsStateWithLifecycle()
    @Suppress("UNUSED_VARIABLE") val _readToKeepSubscribed = sessionStatus

    AlTheme {
        AppNavHost(
            navController = navController,
            startScreen = Route.Home,
        )
    }
}
```

> **Why we don't gate routing yet:** there is no `Route.Login` and no Login screen. Adding a placeholder route now would create dead navigation states. The hook is in place; the next plan adds the Login feature and switches `startScreen` based on `sessionStatus`.

### Success Criteria

#### Automated Verification
- [ ] `./scripts/compile.sh` exits 0
- [ ] `./gradlew :composeApp:compileDebugKotlinAndroid --no-daemon` exits 0
- [ ] `./gradlew :composeApp:compileKotlinIosSimulatorArm64 --no-daemon` exits 0
- [ ] `./gradlew :composeApp:assembleDebug --no-daemon` produces an APK

#### Manual Verification
- [ ] App launches on Android emulator and renders `HomeScreen` (no behavior regression)
- [ ] App launches on iOS simulator (via Xcode) and renders `HomeScreen`
- [ ] Adding a temporary `println(sessionStatus)` in `App.kt` shows `Initializing` → `NotAuthenticated(isSignOut=false)` shortly after launch on a fresh install

---

## Testing Strategy

This plan does not ship tests, but every interface is designed to enable them in the follow-up plan:

- `MemorySessionManager` is provided so `AuthRepositoryImpl` can be tested without touching `TokenStore` (i.e. without AndroidKeyStore or Keychain).
- `AuthApi` takes an injected `HttpClient`, so it can be tested with `MockEngine`.
- `AuthRepository` exposes a `StateFlow<SessionStatus>` — testable via Turbine.

### Manual Smoke Test (post-implementation, requires a backend)

1. Stand up a stub backend exposing `POST /auth/login` (returns valid `UserSession` JSON), `POST /auth/refresh` (returns rotated `UserSession`), `POST /auth/logout` (returns 204).
2. From a temporary debug button, call `koinInject<SignInUseCase>().invoke(Credentials("a@b.com", "pw"))`.
3. Confirm `sessionStatus` becomes `Authenticated(session, SignIn)`.
4. Force-close + relaunch app → confirm `sessionStatus` becomes `Authenticated(session, Storage)` after `bootstrap()`.
5. Manually invalidate the access token server-side, then call any authed endpoint → confirm Ktor automatically refreshes (visible via `Logging.HEADERS` flip) and the request succeeds.
6. Manually revoke the refresh token, then call any authed endpoint → confirm `sessionStatus` becomes `RefreshFailure` then `NotAuthenticated(isSignOut = false)`.
7. Call `SignOutUseCase` → confirm `sessionStatus` becomes `NotAuthenticated(isSignOut = true)` and storage is cleared.

## Performance Considerations

- The Android `TokenStore.android.kt` already uses `withContext(Dispatchers.IO)` for `get`/`set`/`clear` since `Cipher.doFinal` and SharedPreferences `apply()` should not run on the main thread. The iOS `TokenStore.ios.kt` does not — Keychain calls are non-blocking and fast enough to run on the caller's dispatcher. If future profiling on iOS shows jank, wrap with `withContext(Dispatchers.Default)`.
- The `Auth` plugin caches `BearerTokens` in-memory and only re-invokes `loadTokens` after `clearToken()`. Cost of `loadTokens` is one storage read per cache invalidation — negligible.

## Migration Notes

This is a greenfield wiring — no existing auth state to migrate. After Phase 5 ships, any future feature that needs an authed `HttpClient` simply injects `HttpClient` (no qualifier) — the `Auth` plugin attaches the bearer token automatically. Features must **not** depend on the `named("refresh")` client.

## References

- Source skeleton plan: `thoughts/plans/cmp_skeleton_setup.md`
- Project overview: `CLAUDE.md`
- **Supabase-style session model (API surface mirrored):**
  - [supabase-kt SessionStatus.kt](https://github.com/supabase-community/supabase-kt/blob/master/Auth/src/commonMain/kotlin/io/github/jan/supabase/auth/status/SessionStatus.kt)
  - [supabase-kt SessionManager.kt](https://github.com/supabase-community/supabase-kt/blob/master/Auth/src/commonMain/kotlin/io/github/jan/supabase/auth/SessionManager.kt)
  - [supabase-kt UserSession.kt](https://github.com/supabase-community/supabase-kt/blob/master/Auth/src/commonMain/kotlin/io/github/jan/supabase/auth/user/UserSession.kt)
  - [supabase-kt SessionSource.kt](https://github.com/supabase-community/supabase-kt/blob/master/Auth/src/commonMain/kotlin/io/github/jan/supabase/auth/status/SessionSource.kt)
  - [supabase-kt SettingsSessionManager.kt](https://github.com/supabase-community/supabase-kt/blob/master/Auth/src/settingsMain/kotlin/io/github/jan/supabase/auth/SettingsSessionManager.kt) — note: this default uses unencrypted SharedPreferences (Android) and NSUserDefaults (iOS); we deliberately do NOT mirror the storage choice.
- **Official platform storage guidance (followed):**
  - Android: [Android KeyStore system](https://developer.android.com/privacy-and-security/keystore)
  - Android: [androidx.security release notes — deprecation of `security-crypto`](https://developer.android.com/jetpack/androidx/releases/security)
  - iOS: [Apple Keychain Services](https://developer.apple.com/documentation/security/keychain-services)
- **iOS Keychain CF bridging pattern reference (MIT-licensed):**
  - [Liftric KVault — KVaultImpl.kt](https://github.com/Liftric/KVault/blob/master/src/iosMain/kotlin/com/liftric/kvault/impl/KVaultImpl.kt)
- Existing files this plan modifies:
  - `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/ClientsModule.kt:12-30`
  - `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/RepositoriesModule.kt:5-7`
  - `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/UseCasesModule.kt:5-7`
  - `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/AppViewModel.kt:7-10`
  - `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/App.kt:11-21`
  - `composeApp/src/androidMain/kotlin/ar/com/ingenieriaalimentacion/di/Modules.android.kt:8-10`
  - `composeApp/src/iosMain/kotlin/ar/com/ingenieriaalimentacion/di/Modules.ios.kt:8-10`
  - `gradle/libs.versions.toml`
  - `composeApp/build.gradle.kts:35-66`
