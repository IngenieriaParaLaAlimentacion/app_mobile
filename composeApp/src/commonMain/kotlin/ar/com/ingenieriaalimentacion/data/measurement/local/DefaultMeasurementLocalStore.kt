package ar.com.ingenieriaalimentacion.data.measurement.local

import ar.com.ingenieriaalimentacion.data.auth.SessionLifecycle
import ar.com.ingenieriaalimentacion.data.db.PendingMeasurementQueries
import ar.com.ingenieriaalimentacion.data.db.observeForOwner
import ar.com.ingenieriaalimentacion.data.db.queryForOwner
import ar.com.ingenieriaalimentacion.data.measurement.mapper.toDomain
import ar.com.ingenieriaalimentacion.data.subject.mapper.toDbString
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

/**
 * SQLDelight-backed [MeasurementLocalStore]. All writes stamp `updatedAt` via the injected [now]
 * source so tests stay deterministic; DB I/O runs off the main thread on [dispatcher].
 */
class DefaultMeasurementLocalStore(
    private val queries: PendingMeasurementQueries,
    private val lifecycle: SessionLifecycle,
    private val now: () -> Long,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MeasurementLocalStore {

    override fun observeAll(): Flow<List<MeasurementUpload>> =
        observeForOwner(lifecycle, dispatcher, emptyList(), queries::selectAll) { query ->
            query.executeAsList().map { it.toDomain() }
        }

    override suspend fun all(): List<MeasurementUpload> = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.selectAll(ownerUserId = owner).executeAsList().map { it.toDomain() }
    }

    override suspend fun insert(upload: MeasurementUpload): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.insert(
            ownerUserId = owner,
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
            observation = upload.observation,
        )
    }

    override suspend fun syncable(): List<MeasurementUpload> = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.selectSyncable(ownerUserId = owner).executeAsList().map { it.toDomain() }
    }

    override suspend fun markSyncing(localId: String): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.markState(syncState = SyncState.Syncing.toDbString(), updatedAt = now(), localId = localId, ownerUserId = owner)
    }

    override suspend fun markSynced(localId: String, bmi: Double?, alertsJson: String?): Unit =
        lifecycle.queryForOwner(dispatcher) { owner ->
            queries.markSynced(bmi = bmi, alertsJson = alertsJson, updatedAt = now(), localId = localId, ownerUserId = owner)
        }

    override suspend fun markFailed(localId: String, reason: String): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.markFailed(errorMessage = reason, updatedAt = now(), localId = localId, ownerUserId = owner)
    }

    override suspend fun bumpRetry(localId: String, reason: String): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.bumpRetry(errorMessage = reason, updatedAt = now(), localId = localId, ownerUserId = owner)
    }

    override suspend fun resetForRetry(localId: String): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.resetForRetry(updatedAt = now(), localId = localId, ownerUserId = owner)
    }

    override suspend fun delete(localId: String): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.deleteById(ownerUserId = owner, localId = localId)
    }

    override suspend fun resetStuckSyncing(): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.resetSyncingToPending(updatedAt = now(), ownerUserId = owner)
    }

    override suspend fun pruneTerminalOlderThan(cutoff: Long): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.pruneTerminalOlderThan(ownerUserId = owner, cutoff = cutoff)
    }
}
