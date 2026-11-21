package ar.com.ingenieriaalimentacion.data.auth.storage

import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession
import ar.com.ingenieriaalimentacion.domain.repository.SessionManager
import ar.com.ingenieriaalimentacion.domain.repository.StoredSession
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class TokenStoreSessionManager(
    private val tokenStore: TokenStore,
    private val json: Json,
) : SessionManager {
    override suspend fun load(): StoredSession {
        val raw = tokenStore.get() ?: return StoredSession.Empty
        val value = decode(raw)
        if (value == StoredSession.Empty) tokenStore.clear()
        return value
    }

    private fun decode(raw: String): StoredSession = try {
        val objectValue = json.parseToJsonElement(raw) as? JsonObject
        when {
            objectValue == null -> StoredSession.Empty
            "kind" in objectValue -> when (val envelope = json.decodeFromString<SessionEnvelope>(raw)) {
                SessionEnvelope("blocked") -> StoredSession.Blocked
                else -> if (envelope.kind == "active" && envelope.session != null &&
                    !envelope.generation.isNullOrBlank()
                ) {
                    StoredSession.Active(envelope.session, envelope.generation)
                } else StoredSession.Empty
            }
            "access_token" in objectValue && "refresh_token" in objectValue ->
                StoredSession.Active(json.decodeFromString<UserSession>(raw), generation = null)
            else -> StoredSession.Empty
        }
    } catch (_: SerializationException) {
        StoredSession.Empty
    } catch (_: IllegalArgumentException) {
        StoredSession.Empty
    }

    override suspend fun replace(value: StoredSession) {
        when (value) {
            StoredSession.Empty -> tokenStore.clear()
            StoredSession.Blocked -> tokenStore.set(json.encodeToString(SessionEnvelope("blocked")))
            is StoredSession.Active -> tokenStore.set(
                json.encodeToString(SessionEnvelope("active", value.session, requireNotNull(value.generation))),
            )
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class SessionEnvelope(
    val kind: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val session: UserSession? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val generation: String? = null,
)
