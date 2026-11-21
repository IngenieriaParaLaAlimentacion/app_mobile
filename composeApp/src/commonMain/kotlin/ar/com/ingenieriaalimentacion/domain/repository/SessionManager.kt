package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession

sealed interface StoredSession {
    data object Empty : StoredSession
    data object Blocked : StoredSession
    data class Active(val session: UserSession, val generation: String?) : StoredSession
}

interface SessionManager {
    suspend fun load(): StoredSession
    suspend fun replace(value: StoredSession)
}

class SessionStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
