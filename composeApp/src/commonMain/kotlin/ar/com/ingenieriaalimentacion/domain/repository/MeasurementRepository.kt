package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementUpload
import ar.com.ingenieriaalimentacion.domain.model.measurement.NewMeasurement
import ar.com.ingenieriaalimentacion.domain.sync.SyncEngine
import kotlinx.coroutines.flow.Flow

interface MeasurementRepository : SyncEngine {
    /** Persist a new measurement locally as PENDING. Returns immediately (offline-first). */
    suspend fun enqueue(measurement: NewMeasurement): Result<Unit>

    /** Observe all locally-tracked measurement uploads (for the history, Historia 3). */
    fun observeUploads(): Flow<List<MeasurementUpload>>

    /** Re-queue a single FAILED row for the next sync pass. */
    suspend fun retry(localId: String): Result<Unit>

    /** Remove a not-yet-synced row. */
    suspend fun delete(localId: String): Result<Unit>

    // syncPending / resetStuckSyncing / pruneTerminal inherited from SyncEngine
}
