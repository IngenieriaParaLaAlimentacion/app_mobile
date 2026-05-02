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
