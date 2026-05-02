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
