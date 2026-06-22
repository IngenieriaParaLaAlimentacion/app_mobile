package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.subject.NewSubject
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectUpload
import ar.com.ingenieriaalimentacion.domain.sync.SyncEngine
import kotlinx.coroutines.flow.Flow

interface SubjectRepository : SyncEngine {
    /** Persist a new subject locally as PENDING. Returns immediately (offline-first). */
    suspend fun enqueue(newSubject: NewSubject): Result<Unit>

    /** Observe all locally-tracked uploads for the Uploads screen. */
    fun observeUploads(): Flow<List<SubjectUpload>>

    /** Re-queue a single FAILED row for the next sync pass. */
    suspend fun retry(localId: String): Result<Unit>

    /** Remove a not-yet-synced row. */
    suspend fun delete(localId: String): Result<Unit>

    // syncPending / resetStuckSyncing / pruneTerminal inherited from SyncEngine
}
