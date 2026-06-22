package ar.com.ingenieriaalimentacion.domain.sync

import ar.com.ingenieriaalimentacion.domain.model.subject.SyncSummary
import kotlin.time.Duration

/**
 * A per-entity offline-first queue the [ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator]
 * can drive. Each queue (subjects, measurements, …) is an independent [SyncEngine] sharing one
 * coordinator and one set of triggers.
 */
interface SyncEngine {
    /** Push all syncable (eligible PENDING) rows to the backend; reconcile outcomes locally. */
    suspend fun syncPending(): SyncSummary

    /** CRASH RECOVERY: un-stick rows left in SYNCING by a kill mid-POST. Call once on coordinator start. */
    suspend fun resetStuckSyncing()

    /** GC terminal rows older than [olderThan]. Called from the periodic cleanup loop. */
    suspend fun pruneTerminal(olderThan: Duration)
}
