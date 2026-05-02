package ar.com.ingenieriaalimentacion.data.auth.storage

/**
 * Platform-backed secure single-string store.
 *
 * - Android `actual`: AES-256/GCM key in `AndroidKeyStore`; ciphertext written to plain
 *   `SharedPreferences`. Per https://developer.android.com/privacy-and-security/keystore.
 * - iOS `actual`: Keychain Services `kSecClassGenericPassword` item with
 *   `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`.
 *   Per https://developer.apple.com/documentation/security/keychain-services.
 *
 * Used by `TokenStoreSessionManager` to persist a JSON-serialized `UserSession`.
 */
expect class TokenStore {
    suspend fun get(): String?
    suspend fun set(value: String)
    suspend fun clear()
}
