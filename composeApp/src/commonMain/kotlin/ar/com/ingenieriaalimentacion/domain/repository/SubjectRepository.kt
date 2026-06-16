package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.subject.NewSubject
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncSummary
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

interface SubjectRepository {
    /** Persist a new subject locally as PENDING. Returns immediately (offline-first). */
    suspend fun enqueue(newSubject: NewSubject): Result<Unit>

    /** Observe all locally-tracked uploads for the Uploads screen. */
    fun observeUploads(): Flow<List<SubjectUpload>>

    /** Push all syncable (eligible PENDING) rows to the backend; reconcile outcomes locally. */
    suspend fun syncPending(): SyncSummary

    /** Re-queue a single FAILED row for the next sync pass. */
    suspend fun retry(localId: String): Result<Unit>

    /** Remove a not-yet-synced row. */
    suspend fun delete(localId: String): Result<Unit>

    /** CRASH RECOVERY: un-stick rows left in SYNCING by a kill mid-POST. Call once on coordinator start. */
    suspend fun resetStuckSyncing()

    /** GC terminal (SYNCED/MERGED) rows older than [olderThan]. Called from the periodic cleanup loop. */
    suspend fun pruneTerminal(olderThan: Duration)
}
