package ar.com.ingenieriaalimentacion.data.auth

import ar.com.ingenieriaalimentacion.domain.model.auth.SessionSource
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession
import ar.com.ingenieriaalimentacion.domain.repository.DatabaseCleaner
import ar.com.ingenieriaalimentacion.domain.repository.DatabaseCleanupException
import ar.com.ingenieriaalimentacion.domain.repository.SessionStorageException
import ar.com.ingenieriaalimentacion.domain.repository.SessionManager
import ar.com.ingenieriaalimentacion.domain.repository.StoredSession
import ar.com.ingenieriaalimentacion.domain.util.IdGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

data class SessionLease(val generation: String, val ownerId: String) :
    AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<SessionLease>
}

class SessionEnded : CancellationException("Authenticated session ended")
enum class SessionExit { Blocked, RefreshFailed, SignedOut, PasswordReset }

class SessionLifecycle(
    private val store: SessionManager,
    private val cache: DatabaseCleaner,
    private val ids: IdGenerator,
) {
    private val mutex = Mutex()
    private val state = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
    val status = state.asStateFlow()
    private var signInTicket = 0L

    fun active(): SessionStatus.Authenticated? = state.value as? SessionStatus.Authenticated

    fun leaseOrNull(): SessionLease? = active()?.let {
        SessionLease(it.generation, requireNotNull(it.session.user).id)
    }

    fun sessionFor(lease: SessionLease): SessionStatus.Authenticated? =
        active()?.takeIf { it.generation == lease.generation && it.session.user?.id == lease.ownerId }

    suspend fun capture(): SessionLease {
        val inherited = currentCoroutineContext()[SessionLease]
        val lease = inherited ?: leaseOrNull() ?: throw SessionEnded()
        if (sessionFor(lease) == null) throw SessionEnded()
        return lease
    }

    suspend fun <T> scoped(work: suspend () -> T): T = withContext(capture()) { work() }

    suspend fun <T> local(lease: SessionLease, work: suspend (String) -> T): T {
        if (sessionFor(lease) == null) throw SessionEnded()
        return mutex.withLock {
            if (sessionFor(lease) == null) throw SessionEnded()
            work(lease.ownerId)
        }
    }

    fun <T> observe(work: () -> Flow<T>): Flow<T> = flow {
        val lease = try {
            capture()
        } catch (_: SessionEnded) {
            return@flow
        }
        emitAll(work().flowOn(lease).takeWhile { sessionFor(lease) != null })
    }

    suspend fun bootstrap() = mutex.withLock {
        if (state.value != SessionStatus.Initializing) return@withLock
        when (val stored = retryLocal { store.load() }) {
            StoredSession.Empty -> {
                retryLocal { cache.clearCaches() }
                state.value = SessionStatus.NotAuthenticated()
            }
            StoredSession.Blocked -> {
                retryLocal { cache.clearCaches() }
                state.value = SessionStatus.AccountBlocked
            }
            is StoredSession.Active -> {
                val user = stored.session.user
                if (user == null || user.id.isBlank()) {
                    retryLocal { store.replace(StoredSession.Empty) }
                    retryLocal { cache.clearCaches() }
                    state.value = SessionStatus.NotAuthenticated()
                } else {
                    val generation = stored.generation ?: ids.newId()
                    if (stored.generation == null) {
                        // The previous app did not track cache ownership.
                        retryLocal { cache.clearCaches() }
                        retryLocal { store.replace(StoredSession.Active(stored.session, generation)) }
                    }
                    state.value = SessionStatus.Authenticated(stored.session, generation, SessionSource.Storage)
                }
            }
        }
    }

    suspend fun beginSignIn(): Long = mutex.withLock {
        when (state.value) {
            is SessionStatus.NotAuthenticated, SessionStatus.AccountBlocked, SessionStatus.PasswordResetCompleted -> Unit
            else -> throw SessionEnded()
        }
        ++signInTicket
    }

    suspend fun completeSignIn(ticket: Long, session: UserSession) = mutex.withLock {
        if (ticket != signInTicket) throw SessionEnded()
        check(!session.user?.id.isNullOrBlank())
        val generation = ids.newId()
        cache.clearCaches()
        withContext(NonCancellable) {
            store.replace(StoredSession.Active(session, generation))
            state.value = SessionStatus.Authenticated(session, generation, SessionSource.SignIn)
            signInTicket++
        }
    }

    suspend fun completeRefresh(
        lease: SessionLease,
        usedRefreshToken: String,
        fresh: UserSession,
    ): UserSession? = mutex.withLock {
        val current = sessionFor(lease) ?: return@withLock null
        if (current.session.refreshToken != usedRefreshToken) return@withLock current.session
        val merged = fresh.copy(user = current.session.user)
        withContext(NonCancellable) {
            store.replace(StoredSession.Active(merged, lease.generation))
            state.value = current.copy(session = merged, source = SessionSource.Refresh(current.session))
        }
        merged
    }

    suspend fun end(lease: SessionLease, exit: SessionExit) = withContext(NonCancellable) {
        mutex.withLock {
            if (sessionFor(lease) != null) closeCurrent(lease, exit)
        }
    }

    suspend fun failRefreshIfCurrent(lease: SessionLease, usedRefreshToken: String): Boolean =
        withContext(NonCancellable) {
            mutex.withLock {
                if (sessionFor(lease)?.session?.refreshToken != usedRefreshToken) return@withLock false
                closeCurrent(lease, SessionExit.RefreshFailed)
                true
            }
        }

    private suspend fun closeCurrent(lease: SessionLease, exit: SessionExit) {
        state.value = SessionStatus.Closing
        signInTicket++
        val voluntary = exit == SessionExit.SignedOut || exit == SessionExit.PasswordReset
        if (voluntary) retryLocal { cache.clearForSignOut(lease.ownerId) }
        val next = if (exit == SessionExit.Blocked) StoredSession.Blocked else StoredSession.Empty
        retryLocal { store.replace(next) }
        if (!voluntary) retryLocal { cache.clearCaches() }
        state.value = when (exit) {
            SessionExit.Blocked -> SessionStatus.AccountBlocked
            SessionExit.RefreshFailed -> SessionStatus.NotAuthenticated()
            SessionExit.SignedOut -> SessionStatus.NotAuthenticated(isSignOut = true)
            SessionExit.PasswordReset -> SessionStatus.PasswordResetCompleted
        }
    }

    private suspend fun <T> retryLocal(operation: suspend () -> T): T {
        while (true) {
            try {
                return operation()
            } catch (_: SessionStorageException) {
                delay(500L)
            } catch (_: DatabaseCleanupException) {
                delay(500L)
            }
        }
    }
}
