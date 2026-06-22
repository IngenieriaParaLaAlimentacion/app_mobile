package ar.com.ingenieriaalimentacion.data.measurement.local

import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementUpload
import kotlinx.coroutines.flow.Flow

/**
 * Local source of truth for the measurement upload queue. The concrete
 * [DefaultMeasurementLocalStore] wraps SQLDelight; tests use a fake. Parallel to
 * [ar.com.ingenieriaalimentacion.data.subject.local.SubjectLocalStore] on a separate table.
 */
interface MeasurementLocalStore {
    fun observeAll(): Flow<List<MeasurementUpload>>
    suspend fun insert(upload: MeasurementUpload)

    /** All PENDING rows (fresh or auto-retrying); backoff gating is applied in Kotlin by the caller. */
    suspend fun syncable(): List<MeasurementUpload>

    suspend fun markSyncing(localId: String)

    /** Success (201/200): persist the server-computed [bmi] + serialized [alertsJson], clear error. */
    suspend fun markSynced(localId: String, bmi: Double?, alertsJson: String?)

    /** Terminal failure: bumps attemptCount and sets a user-facing reason. */
    suspend fun markFailed(localId: String, reason: String)

    /** Transient failure: stay PENDING, count the attempt, restart the backoff window. */
    suspend fun bumpRetry(localId: String, reason: String)

    /** Manual Retry on a dead-letter FAILED row: fresh start (attemptCount → 0). */
    suspend fun resetForRetry(localId: String)

    suspend fun delete(localId: String)

    /** CRASH RECOVERY — un-stick rows left mid-POST. Call once on SyncCoordinator.start(). */
    suspend fun resetStuckSyncing()

    /** GC terminal rows older than [cutoff] (epoch ms). Call from the periodic cleanup loop. */
    suspend fun pruneTerminalOlderThan(cutoff: Long)
}
