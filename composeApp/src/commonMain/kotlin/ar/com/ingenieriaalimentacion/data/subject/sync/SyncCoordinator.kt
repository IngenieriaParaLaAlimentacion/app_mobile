package ar.com.ingenieriaalimentacion.data.subject.sync

import ar.com.ingenieriaalimentacion.data.network.connectivity.ConnectivityObserver
import ar.com.ingenieriaalimentacion.di.AppScope
import ar.com.ingenieriaalimentacion.domain.sync.SyncEngine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Single-flight sync orchestrator on [AppScope] driving every registered [SyncEngine] (subjects,
 * measurements, …). A [Mutex] makes overlapping triggers *drop* (not queue) across all engines.
 * Auto-syncs when connectivity returns and prunes terminal rows on an hourly loop. Crash recovery
 * (un-stick SYNCING rows) runs once per engine on [start].
 */
class SyncCoordinator(
    private val engines: List<SyncEngine>,
    private val connectivity: ConnectivityObserver,
    private val scope: AppScope,
) {
    private val mutex = Mutex()
    private var started = false

    fun start() {
        if (started) return                          // idempotent (extra BatchProcessor.start guard)
        started = true
        scope.launch {
            engines.forEach { runCatching { it.resetStuckSyncing() } }  // CRASH RECOVERY: all queues
            connectivity.isOnline.collect { online -> if (online) requestSync() }  // optimization layer
        }
        scope.launch {                               // periodic cleanup (extra telemetry hourly loop)
            while (isActive) {
                delay(1.hours)
                engines.forEach { runCatching { it.pruneTerminal(olderThan = 7.days) } }
            }
        }
    }

    fun requestSync() {
        scope.launch {
            if (!mutex.tryLock()) return@launch       // single-flight across all engines: DROP overlaps
            try {
                engines.forEach { runCatching { it.syncPending() } } // never crash the app scope
            } finally {
                mutex.unlock()
            }
        }
    }
}
