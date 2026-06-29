package ar.com.ingenieriaalimentacion.data.subject.local

import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import kotlinx.coroutines.flow.Flow

/**
 * Local source of truth for the subject upload queue. The concrete
 * [DefaultSubjectLocalStore] wraps SQLDelight; tests use a fake.
 */
interface SubjectLocalStore {
    fun observeAll(): Flow<List<SubjectUpload>>

    /** One-shot snapshot of every queued row, used by the subject picker union read. */
    suspend fun all(): List<SubjectUpload>

    /** Single queued row by its [localId], or null if no local row exists (pre-existing server subject). */
    suspend fun find(localId: String): SubjectUpload?

    suspend fun insert(upload: SubjectUpload)

    /** All PENDING rows (fresh or auto-retrying); backoff gating is applied in Kotlin by the caller. */
    suspend fun syncable(): List<SubjectUpload>

    suspend fun markSyncing(localId: String)
    suspend fun markSynced(localId: String, state: SyncState, canonicalId: String)

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
