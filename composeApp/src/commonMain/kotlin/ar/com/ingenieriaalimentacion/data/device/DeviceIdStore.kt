package ar.com.ingenieriaalimentacion.data.device

/**
 * Platform-backed store for the per-install device id (UUID v4).
 *
 * - Android `actual`: AES-256/GCM key in `AndroidKeyStore`; ciphertext in `SharedPreferences`.
 * - iOS `actual`: Keychain Services `kSecClassGenericPassword` item.
 *
 * Identity-scoped — separate from `TokenStore` so wiping a session never destroys the device id.
 */
expect class DeviceIdStore {
    suspend fun get(): String?
    suspend fun set(value: String)
}
