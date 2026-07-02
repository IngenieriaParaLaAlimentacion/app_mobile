package ar.com.ingenieriaalimentacion.data.measurement.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import ar.com.ingenieriaalimentacion.data.db.PendingMeasurementQueries
import ar.com.ingenieriaalimentacion.data.measurement.mapper.toDomain
import ar.com.ingenieriaalimentacion.data.subject.mapper.toDbString
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed [MeasurementLocalStore]. All writes stamp `updatedAt` via the injected [now]
 * source so tests stay deterministic; DB I/O runs off the main thread on [dispatcher].
 */
class DefaultMeasurementLocalStore(
    private val queries: PendingMeasurementQueries,
    private val now: () -> Long,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MeasurementLocalStore {

    override fun observeAll(): Flow<List<MeasurementUpload>> =
        queries.selectAll().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override suspend fun all(): List<MeasurementUpload> = withContext(dispatcher) {
        queries.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun insert(upload: MeasurementUpload): Unit = withContext(dispatcher) {
        queries.insert(
            localId = upload.localId,
            subjectId = upload.subjectId,
            subjectName = upload.subjectName,
            subjectLastName = upload.subjectLastName,
            subjectDni = upload.subjectDni.toLong(),
            establishmentId = upload.establishmentId,
            establishmentName = upload.establishmentName,
            weightKg = upload.weightKg,
            heightCm = upload.heightCm,
            measuredAt = upload.measuredAt,
            syncState = upload.syncState.toDbString(),
            errorMessage = upload.errorMessage,
            attemptCount = upload.attemptCount.toLong(),
            bmi = upload.bmi,
            alertsJson = null,
            createdAt = upload.createdAt,
            updatedAt = upload.updatedAt,
        )
    }

    override suspend fun syncable(): List<MeasurementUpload> = withContext(dispatcher) {
        queries.selectSyncable().executeAsList().map { it.toDomain() }
    }

    override suspend fun markSyncing(localId: String): Unit = withContext(dispatcher) {
        queries.markState(syncState = SyncState.Syncing.toDbString(), updatedAt = now(), localId = localId)
    }

    override suspend fun markSynced(localId: String, bmi: Double?, alertsJson: String?): Unit =
        withContext(dispatcher) {
            queries.markSynced(bmi = bmi, alertsJson = alertsJson, updatedAt = now(), localId = localId)
        }

    override suspend fun markFailed(localId: String, reason: String): Unit = withContext(dispatcher) {
        queries.markFailed(errorMessage = reason, updatedAt = now(), localId = localId)
    }

    override suspend fun bumpRetry(localId: String, reason: String): Unit = withContext(dispatcher) {
        queries.bumpRetry(errorMessage = reason, updatedAt = now(), localId = localId)
    }

    override suspend fun resetForRetry(localId: String): Unit = withContext(dispatcher) {
        queries.resetForRetry(updatedAt = now(), localId = localId)
    }

    override suspend fun delete(localId: String): Unit = withContext(dispatcher) {
        queries.deleteById(localId)
    }

    override suspend fun resetStuckSyncing(): Unit = withContext(dispatcher) {
        queries.resetSyncingToPending(updatedAt = now())
    }

    override suspend fun pruneTerminalOlderThan(cutoff: Long): Unit = withContext(dispatcher) {
        queries.pruneTerminalOlderThan(cutoff)
    }
}
