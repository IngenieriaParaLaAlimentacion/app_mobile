package ar.com.ingenieriaalimentacion.data.auth.storage

import ar.com.ingenieriaalimentacion.domain.repository.SessionManager
import ar.com.ingenieriaalimentacion.domain.repository.StoredSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MemorySessionManager(initial: StoredSession = StoredSession.Empty) : SessionManager {
    private val mutex = Mutex()
    private var stored = initial

    override suspend fun load(): StoredSession = mutex.withLock { stored }
    override suspend fun replace(value: StoredSession) = mutex.withLock { stored = value }
}
