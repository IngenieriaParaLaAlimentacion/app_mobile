---
date: 2026-05-02
author: Juan Duzac
git_commit: 7fb0ba6
branch: main
repository: app_mobile
topic: "Align `TokenStore` and `TokenStoreSessionManager` with official Google/Apple post-2025 guidance"
tags: [plan, auth, tokens, keychain, android-keystore, kmp]
status: draft
based_on: thoughts/research/2026-05-01_23-05-58_auth_token_storage.md
---

# Auth Token Storage — Official Recommendation Alignment Plan

## Overview

Apply the five concrete recommendations from `thoughts/research/2026-05-01_23-05-58_auth_token_storage.md:343-349` so `TokenStore` (Android + iOS) and `TokenStoreSessionManager` match the post-2025 guidance from Google's [Android Keystore page](https://developer.android.com/privacy-and-security/keystore) and Apple's [Keychain Services / CryptoKit sample](https://developer.apple.com/documentation/cryptokit/storing_cryptokit_keys_in_the_keychain). The current implementation already aligns with the headline guidance (AES-256/GCM in AndroidKeyStore on Android, `kSecClassGenericPassword` Keychain item on iOS); this plan closes the residual gaps.

## Current State Analysis

The research document inventoried the auth path end-to-end. The persistence-layer files in scope here are:

- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStore.kt:14-18` — single-slot `expect class`.
- `composeApp/src/androidMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStore.android.kt:1-92` — AES-256/GCM via AndroidKeyStore; ciphertext in plain `SharedPreferences`. Already uses `Dispatchers.IO` per official guidance ("avoid `AndroidKeyStore` on the main thread").
- `composeApp/src/iosMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStore.ios.kt:1-148` — Keychain `kSecClassGenericPassword` item with `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`. CF bridging idiom from KVault. **No dispatcher hop**, **no `kSecUseDataProtectionKeychain`**.
- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStoreSessionManager.kt:13-30` — JSON serialization adapter. `loadSessionOrNull` uses `runCatching {}.getOrNull()`.

### Key Discoveries
- Android implementation is already aligned with the headline post-deprecation guidance (`androidx.security:security-crypto` 1.1.0-alpha07, Apr 2025) — see research lines 252-261 — and with the recommended primitives (AES-256, GCM, `BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE`) — research lines 270-272.
- iOS implementation matches Apple's canonical `kSecClassGenericPassword` sample on item class and accessibility tier choice; missing only `kSecUseDataProtectionKeychain: true` and dispatcher hop.
- `runCatching {}` swallows `CancellationException`, contradicting the project's Kotlin guideline (`rules/kotlin/coding-style.md` — "Never catch `CancellationException` — always rethrow it"). Two call sites: `TokenStore.android.kt:32, 37` and `TokenStoreSessionManager.kt:24`.
- `get()` cannot distinguish "no entry" from "corrupted ciphertext / corrupted JSON". Both return `null`. A corrupted blob silently triggers a sign-in prompt with no recovery hint.
- `setIsStrongBoxBacked(true)` is a free upgrade on supported devices (API 28+) and has a documented `StrongBoxUnavailableException` fallback path — research lines 273-278.
- Constants `kCFBooleanTrue` is already imported in `TokenStore.ios.kt:16`. `Dispatchers` and `withContext` are NOT yet imported in the iOS file. `kSecUseDataProtectionKeychain` is NOT yet imported.
- No test directories exist (`commonTest`, `androidUnitTest`, `iosTest` all absent). Verification will lean on the existing `scripts/compile.sh` typecheck plus the Android assemble; behavior verification is manual.

## Desired End State

After this plan is implemented:

1. iOS Keychain queries explicitly opt into the modern (data-protection) keychain on every operation, future-proofing a possible macOS target.
2. iOS `TokenStore` operations run on `Dispatchers.Default` so callers don't pay Keychain syscall latency on whatever thread they happen to be on (parity with Android's `Dispatchers.IO` hop).
3. Android key generation tries StrongBox first and gracefully falls back when unsupported.
4. Neither platform's `TokenStore` nor `TokenStoreSessionManager` swallows `CancellationException`. Failure modes use typed catches: `GeneralSecurityException`, `IllegalArgumentException`, `SerializationException`.
5. A corrupted token blob (decryption tag failure, malformed JSON, non-UTF-8) triggers a self-clear of the slot and surfaces no user-visible difference from "no session" — but logs a single warning so operators can detect it. The next sign-in starts clean.

### Verification

- `./scripts/compile.sh` exits 0.
- `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:assembleDebug --no-daemon` succeeds.
- iOS app builds via `iosApp/iosApp.xcodeproj`.
- Manual smoke: sign-in persists across app restart on Android + iOS; sign-out clears state; corrupted-blob simulation (manually overwrite the SharedPreferences value or Keychain item) results in a clean re-auth, not a crash, with the slot cleared.

## What We're NOT Doing

- **Not** adding `setUserAuthenticationRequired(true)` on Android — the existing plan (`thoughts/plans/auth_setup.md:67-68`) reserves biometric gating for a future "sensitive operation" flow. Session token must remain readable on cold launch without prompts.
- **Not** adding `setUnlockedDeviceRequired(true)` on Android — would break boot-completed background reads, which the project may need later.
- **Not** changing iOS accessibility from `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` to `kSecAttrAccessibleWhenUnlocked`. The current choice is a deliberate background-readability vs. unlocked-only trade documented in `TokenStore.ios.kt:48-50` and research lines 319-323.
- **Not** introducing proactive expiry refresh (the 401-reactive Ktor flow stays as-is).
- **Not** introducing tests in this PR — there are no test source sets yet; adding kotlin.test infrastructure is its own change.
- **Not** changing the on-disk schema, `KEY_ALIAS`, `SERVICE`, or `ACCOUNT` constants. No migration risk.
- **Not** adding a logging dependency — use `println` for now (project does not yet have a structured logger; introducing one is out of scope).

## Implementation Approach

Three small phases, each independently buildable. Order matters only for review clarity, not for compilation.

1. **Phase 1 — iOS hardening** (`TokenStore.ios.kt` only): add `kSecUseDataProtectionKeychain: kCFBooleanTrue` to the three queries; wrap each operation body in `withContext(Dispatchers.Default)`.
2. **Phase 2 — Android StrongBox attempt** (`TokenStore.android.kt` only): try `setIsStrongBoxBacked(true)`; on `StrongBoxUnavailableException`, retry without it.
3. **Phase 3 — typed exception handling + corruption recovery** (`TokenStore.android.kt`, `TokenStore.ios.kt`, `TokenStoreSessionManager.kt`): replace `runCatching {}.getOrNull()` with typed catches; on detected corruption, clear the slot and return `null`.

---

## Phase 1: iOS — Apple-aligned Keychain queries + dispatcher hop

### Overview
Two surgical changes to `TokenStore.ios.kt`:

1. Add `kSecUseDataProtectionKeychain: kCFBooleanTrue` to the three CFDictionaries (`get` query, `set` add query, `set` match-on-duplicate query, `clear` query). Default is `true` on iOS but `false` on macOS — explicit `true` is what Apple's reference Swift sample uses (research lines 293-301) and is the cheapest way to keep the code correct if Compose Multiplatform later targets macOS.
2. Wrap each `actual suspend fun` body in `withContext(Dispatchers.Default)` so Keychain syscalls don't run on the caller's dispatcher (parity with Android's `Dispatchers.IO` hop).

### Changes Required

#### 1. iOS imports
**File**: `composeApp/src/iosMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStore.ios.kt`
**Changes**: add three imports.

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Security.kSecUseDataProtectionKeychain
```

Insert in alphabetical position among existing imports. `kCFBooleanTrue` is already imported (line 16).

#### 2. `get()` body — wrap in dispatcher; add data-protection key
**File**: `composeApp/src/iosMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStore.ios.kt:59-87`
**Changes**: outer `withContext(Dispatchers.Default) { ... }`; add one entry to the `createDict` call.

```kotlin
actual suspend fun get(): String? = withContext(Dispatchers.Default) {
    val service = CFBridgingRetain(SERVICE)
    val account = CFBridgingRetain(ACCOUNT)
    try {
        val query = createDict(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service,
            kSecAttrAccount to account,
            kSecUseDataProtectionKeychain to kCFBooleanTrue,
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            when (status) {
                errSecSuccess -> {
                    (CFBridgingRelease(result.value) as? NSData)?.let {
                        NSString.create(it, NSUTF8StringEncoding) as String?
                    }
                }
                errSecItemNotFound -> null
                else -> null
            }
        }
    } finally {
        CFBridgingRelease(service)
        CFBridgingRelease(account)
    }
}
```

#### 3. `set()` body — wrap in dispatcher; add data-protection key to both queries
**File**: `composeApp/src/iosMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStore.ios.kt:89-120`

```kotlin
actual suspend fun set(value: String): Unit = withContext(Dispatchers.Default) {
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
            kSecUseDataProtectionKeychain to kCFBooleanTrue,
            kSecValueData to data,
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        )
        val addStatus = SecItemAdd(addQuery, null)
        if (addStatus == errSecDuplicateItem) {
            val matchQuery = createDict(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to service,
                kSecAttrAccount to account,
                kSecUseDataProtectionKeychain to kCFBooleanTrue,
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
```

#### 4. `clear()` body — wrap in dispatcher; add data-protection key
**File**: `composeApp/src/iosMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStore.ios.kt:122-136`

```kotlin
actual suspend fun clear(): Unit = withContext(Dispatchers.Default) {
    val service = CFBridgingRetain(SERVICE)
    val account = CFBridgingRetain(ACCOUNT)
    try {
        val query = createDict(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service,
            kSecAttrAccount to account,
            kSecUseDataProtectionKeychain to kCFBooleanTrue,
        )
        SecItemDelete(query)
    } finally {
        CFBridgingRelease(service)
        CFBridgingRelease(account)
    }
}
```

### Success Criteria

#### Automated Verification:
- [x] commonMain typecheck passes: `./scripts/compile.sh`
- [x] iOS framework compiles: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --no-daemon`

#### Manual Verification:
- [ ] iOS simulator: sign-in persists across app cold start.
- [ ] iOS simulator: sign-out clears the Keychain item (a subsequent cold start lands on `NotAuthenticated`).
- [ ] iOS device (lock screen): sign-in succeeds; lock device, wait, return to app — session still readable (validates that `AfterFirstUnlock` was preserved on overwrite).

---

## Phase 2: Android — StrongBox-backed key with graceful fallback

### Overview
On API 28+ devices that have a Secure Element, `setIsStrongBoxBacked(true)` makes the AES key live in dedicated security hardware. On devices without it, `KeyGenerator.init(spec)` (or, on some OEMs, `generateKey()`) throws `StrongBoxUnavailableException` — caller must retry without the flag. This is the canonical pattern from Google's [Sign in with biometrics training](https://developer.android.com/training/sign-in/biometric-auth) and the [Keystore page](https://developer.android.com/privacy-and-security/keystore).

### Changes Required

#### 1. Imports
**File**: `composeApp/src/androidMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStore.android.kt`

```kotlin
import android.os.Build
import android.security.keystore.StrongBoxUnavailableException
```

#### 2. `loadOrCreateKey()` — try StrongBox, fall back without
**File**: `composeApp/src/androidMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStore.android.kt:66-81`

Replace the existing `loadOrCreateKey()` body so the spec is built via a small helper that toggles `setIsStrongBoxBacked`, and the call site retries on `StrongBoxUnavailableException`.

```kotlin
private fun loadOrCreateKey(): SecretKey {
    val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    ks.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

    val tryStrongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    return try {
        generateAesKey(strongBoxBacked = tryStrongBox)
    } catch (_: StrongBoxUnavailableException) {
        // StrongBox not available on this device — retry without it.
        generateAesKey(strongBoxBacked = false)
    }
}

private fun generateAesKey(strongBoxBacked: Boolean): SecretKey {
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
    val spec = KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .apply { if (strongBoxBacked) setIsStrongBoxBacked(true) }
        .build()
    generator.init(spec)
    return generator.generateKey()
}
```

Notes:
- The `KEY_ALIAS = ".session_v1"` constant is unchanged. Devices that already have a non-StrongBox key persisted will continue to use it (the `getKey` early-return is hit first); only fresh installs / cleared keystores attempt StrongBox.
- The `Build.VERSION.SDK_INT >= P` guard avoids touching the API on API < 28 entirely. The project's `minSdk` is not changing as part of this plan.

### Success Criteria

#### Automated Verification:
- [x] commonMain typecheck passes: `./scripts/compile.sh`
- [x] Android debug build passes: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:assembleDebug --no-daemon`

#### Manual Verification:
- [ ] Android emulator (Pixel API 34, no StrongBox): app installs, sign-in succeeds, key falls back silently.
- [ ] Pixel device with Titan M (API 28+, has StrongBox): clear app data, sign-in, restart — session persists. (Best-effort manual check; lack of crash is the primary signal.)

---

## Phase 3: Typed exception handling + corruption recovery

### Overview
Three changes, all small, that together stop swallowing `CancellationException` and turn a corrupted blob into a clean re-auth instead of an opaque "no session":

1. `TokenStore.android.kt` `get()` — replace `runCatching {}.getOrNull()` with typed catches; on detected corruption, clear the slot and return `null` (also log a single warning so the issue is observable).
2. `TokenStoreSessionManager.kt` `loadSessionOrNull()` — replace `runCatching {}.getOrNull()` with typed catch on `SerializationException`; on corruption, call `tokenStore.clear()` before returning `null`.
3. iOS `TokenStore.ios.kt` `get()` — already returns `null` for the `else` branch of `OSStatus`. Add a single `println` warning for non-`errSecSuccess`, non-`errSecItemNotFound` statuses so silent failures become observable. (No exception handling change needed — the iOS code path doesn't use `runCatching` and no `CancellationException` is in play.)

### Changes Required

#### 1. Android `TokenStore.android.kt:30-43` `get()` — typed catches + self-clear
**File**: `composeApp/src/androidMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStore.android.kt`

Add imports:
```kotlin
import android.util.Log
import java.security.GeneralSecurityException
```

Replace the body:

```kotlin
actual suspend fun get(): String? = withContext(Dispatchers.IO) {
    val combined = prefs.getString(PREF_KEY, null) ?: return@withContext null
    val bytes = try {
        Base64.decode(combined, Base64.NO_WRAP)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Stored session blob is not valid Base64 — clearing", e)
        prefs.edit().remove(PREF_KEY).apply()
        return@withContext null
    }
    if (bytes.size < IV_BYTES + 1) {
        Log.w(TAG, "Stored session blob too short — clearing")
        prefs.edit().remove(PREF_KEY).apply()
        return@withContext null
    }
    val iv = bytes.copyOfRange(0, IV_BYTES)
    val ciphertext = bytes.copyOfRange(IV_BYTES, bytes.size)
    try {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, loadKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        cipher.doFinal(ciphertext).decodeToString()
    } catch (e: GeneralSecurityException) {
        Log.w(TAG, "Stored session blob failed AES/GCM decryption — clearing", e)
        prefs.edit().remove(PREF_KEY).apply()
        null
    }
}
```

Add a new constant in the companion:
```kotlin
const val TAG = "TokenStore"
```

Notes:
- `GeneralSecurityException` is the common base of every `Cipher.init` / `doFinal` failure (`AEADBadTagException`, `IllegalBlockSizeException`, `BadPaddingException`, `KeyStoreException`, `InvalidKeyException`, `NoSuchAlgorithmException`, `NoSuchPaddingException`, `InvalidAlgorithmParameterException`). It does not catch `CancellationException` (which extends `IllegalStateException` / `RuntimeException` only — not `GeneralSecurityException`).
- `IllegalArgumentException` is what `Base64.decode` throws on malformed input.
- `prefs.edit().remove(...).apply()` is idempotent and re-uses the existing `prefs` reference.

#### 2. `TokenStoreSessionManager.kt:22-25` `loadSessionOrNull()` — typed catch + self-clear
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStoreSessionManager.kt`

Add import:
```kotlin
import kotlinx.serialization.SerializationException
```

Replace the body:

```kotlin
override suspend fun loadSessionOrNull(): UserSession? {
    val raw = tokenStore.get() ?: return null
    return try {
        json.decodeFromString(UserSession.serializer(), raw)
    } catch (e: SerializationException) {
        // Corrupted JSON — clear the slot so the next sign-in starts clean.
        tokenStore.clear()
        null
    }
}
```

Notes:
- `SerializationException` extends `IllegalArgumentException`. We deliberately do **not** catch the broader `IllegalArgumentException` here — `SerializationException` is the only failure mode for `decodeFromString` we want to recover from. `CancellationException` is unaffected.
- Logging uses `println` to stay multiplatform-safe (commonMain has no logger). Skipped here to keep the change minimal — the slot self-clears, which is the user-facing recovery; operators can detect the rare case via the `Log.w(TAG, ...)` on Android (Phase 3.1) or the `println` on iOS (Phase 3.3) once the layer below has already cleared.

#### 3. iOS `TokenStore.ios.kt:59-87` `get()` — log unexpected `OSStatus`
**File**: `composeApp/src/iosMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/storage/TokenStore.ios.kt`

In the `when (status)` block, change the `else -> null` branch to log first:

```kotlin
else -> {
    println("TokenStore: SecItemCopyMatching returned unexpected OSStatus=$status")
    null
}
```

Apply the same pattern to `set()` (currently line 103-114, which only handles `errSecDuplicateItem`): log a `println` for any non-`errSecSuccess`, non-`errSecDuplicateItem` `addStatus`. Also log if `SecItemUpdate`'s return is non-zero (currently ignored).

Updated `set()` snippet:

```kotlin
val addStatus = SecItemAdd(addQuery, null)
when (addStatus) {
    errSecSuccess -> Unit
    errSecDuplicateItem -> {
        val matchQuery = createDict(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service,
            kSecAttrAccount to account,
            kSecUseDataProtectionKeychain to kCFBooleanTrue,
        )
        val attributesToUpdate = createDict(
            kSecValueData to data,
        )
        val updateStatus = SecItemUpdate(matchQuery, attributesToUpdate)
        if (updateStatus != errSecSuccess) {
            println("TokenStore: SecItemUpdate failed with OSStatus=$updateStatus")
        }
    }
    else -> println("TokenStore: SecItemAdd failed with OSStatus=$addStatus")
}
```

Notes:
- iOS does not currently use `runCatching`, so `CancellationException` is not at risk on this path. The change is purely about making silent Keychain failures visible.
- Apple's `OSStatus` values are documented at [`SecBase.h` errors](https://developer.apple.com/documentation/security/1542001-security_framework_result_codes). We surface the raw integer to keep the log minimal; an enum mapping is a future polish.

### Success Criteria

#### Automated Verification:
- [x] commonMain typecheck passes: `./scripts/compile.sh`
- [x] Android debug build passes: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:assembleDebug --no-daemon`
- [x] iOS framework compiles: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --no-daemon`

#### Manual Verification:
- [ ] Android: tamper with the SharedPreferences value (e.g., `adb shell run-as ... echo garbage > shared_prefs/...auth.xml`) → app cold start lands on `NotAuthenticated`, no crash, `logcat | grep TokenStore` shows the warning, the slot is cleared on next read.
- [ ] iOS: sign-in twice in a row to verify the duplicate-item update path still works; intentionally stop the simulator between `SecItemAdd` and `SecItemUpdate` is not feasible — instead, manually use Keychain Access on the simulator host or tweak `SERVICE` temporarily to validate the failure-log path.
- [ ] Common: cancellation does not produce zombie sessions — open a screen that triggers `loadSessionOrNull()` and rapidly cancel its scope; the next legitimate read still works (verifies `CancellationException` is no longer swallowed).

---

## Testing Strategy

No automated test source sets exist yet, so verification is build + manual:

- `./scripts/compile.sh` — fast type check on commonMain.
- `./gradlew :composeApp:assembleDebug` — full Android compile, surfaces `StrongBoxUnavailableException` import / SDK-guarded code paths.
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` — iOS framework link, surfaces `kSecUseDataProtectionKeychain` symbol / `Dispatchers.Default` import.
- Manual smoke per phase as listed in each Success Criteria block.

A follow-up plan should add `commonTest` (for `TokenStoreSessionManager` with a fake `TokenStore`) and `androidUnitTest` (for `TokenStore.android` corruption-recovery paths via Robolectric or a forge of `SharedPreferences`). Out of scope here.

## Performance Considerations

- iOS `withContext(Dispatchers.Default)` adds a dispatcher hop per Keychain op (~microseconds). Negligible vs. Keychain syscall latency, and matches Android. No measurable impact on cold start.
- StrongBox path is one-shot per fresh install and runs on `Dispatchers.IO` already. The `try { strongBox } catch { fallback }` adds at most one extra `KeyGenerator.init` call on devices without StrongBox — measurable in ms, only on first auth ever.
- Self-clear on corruption adds one `prefs.edit().apply()` (Android) or `SecItemDelete` (iOS) per corrupted-read event. Both are async (`apply()`) or fire-and-forget. No hot-path impact.

## Migration Notes

- No on-disk schema change. `KEY_ALIAS = "...session_v1"` and `SERVICE = "ar.com.ingenieriaalimentacion.auth"` / `ACCOUNT = "session"` are unchanged.
- Existing installs continue to use their existing AndroidKeyStore key (StrongBox attempt only happens on fresh installs — `loadOrCreateKey()` early-returns when a key already exists).
- Existing iOS Keychain items are read by `get()` regardless of whether `kSecUseDataProtectionKeychain` was set on write — Apple resolves the key against item identity (`service`/`account`/`accessGroup`), not against the data-protection flag.
- No version bump or rollback hatch needed.

## References

- Research document: `thoughts/research/2026-05-01_23-05-58_auth_token_storage.md`
- Original implementation plan: `thoughts/plans/auth_setup.md`
- [Android Keystore system](https://developer.android.com/privacy-and-security/keystore)
- [Security | Jetpack release notes](https://developer.android.com/jetpack/androidx/releases/security) — `androidx.security:security-crypto` deprecation
- [Storing CryptoKit Keys in the Keychain](https://developer.apple.com/documentation/cryptokit/storing_cryptokit_keys_in_the_keychain) — Apple canonical sample
- [`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`](https://developer.apple.com/documentation/security/ksecattraccessibleafterfirstunlockthisdeviceonly)
- Reference auth state persistence: [openid/AppAuth-Android `AuthStateManager`](https://github.com/openid/appauth-android)
