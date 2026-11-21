package ar.com.ingenieriaalimentacion.data.subject.sync

import ar.com.ingenieriaalimentacion.data.auth.SessionLease
import ar.com.ingenieriaalimentacion.data.auth.SessionLifecycle
import ar.com.ingenieriaalimentacion.data.network.connectivity.ConnectivityObserver
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.sync.SyncEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** Runs foreground and reconnect sync, interrupted-queue recovery and pruning for the active session. */
class SyncCoordinator(
    private val engines: List<SyncEngine>,
    private val connectivity: ConnectivityObserver,
    private val scope: CoroutineScope,
    private val lifecycle: SessionLifecycle,
) {
    private val requests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val started = MutableStateFlow(false)

    fun start() {
        if (!started.compareAndSet(expect = false, update = true)) return
        scope.launch {
            lifecycle.status.map { (it as? SessionStatus.Authenticated)?.generation }
                .distinctUntilChanged()
                .collectLatest { generation ->
                    val lease = lifecycle.leaseOrNull()?.takeIf { it.generation == generation }
                        ?: return@collectLatest
                    withContext(lease) { runForOwner(lease) }
                }
        }
    }

    fun requestSync(lease: SessionLease? = lifecycle.leaseOrNull()) {
        if (lease != null && lifecycle.sessionFor(lease) != null) requests.tryEmit(lease.generation)
    }

    private suspend fun runForOwner(lease: SessionLease) = coroutineScope {
        forEachEngine { it.resetStuckSyncing() }
        val syncLock = Mutex()

        fun sync() {
            if (!syncLock.tryLock()) return
            launch {
                try {
                    forEachEngine { it.syncPending() }
                } finally {
                    syncLock.unlock()
                }
            }
        }

        launch { requests.collect { if (it == lease.generation) sync() } }
        launch { connectivity.isOnline.collect { if (it) sync() } }
        while (isActive) {
            delay(1.hours)
            forEachEngine { it.pruneTerminal(olderThan = 7.days) }
        }
    }

    private suspend inline fun forEachEngine(work: (SyncEngine) -> Unit) {
        for (engine in engines) {
            lifecycle.capture()
            try {
                work(engine)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                println("Sync operation failed: ${failure::class.simpleName}")
            }
        }
    }
}
