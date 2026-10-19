package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementUpload
import ar.com.ingenieriaalimentacion.domain.model.measurement.NewMeasurement
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncSummary
import ar.com.ingenieriaalimentacion.domain.repository.MeasurementRepository
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeMeasurementRepository : MeasurementRepository {
    val enqueued = mutableListOf<NewMeasurement>()
    val persisted = mutableListOf<NewMeasurement>()
    @kotlin.concurrent.Volatile var syncCalls = 0
    val retried = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    var enqueueResult: Result<Unit> = Result.success(Unit)
    var syncSummary: SyncSummary = SyncSummary()
    val uploads = MutableStateFlow<List<MeasurementUpload>>(emptyList())

    override suspend fun enqueue(measurement: NewMeasurement): Result<Unit> {
        enqueued += measurement
        if (enqueueResult.isSuccess) persisted += measurement
        return enqueueResult
    }

    override fun observeUploads(): Flow<List<MeasurementUpload>> = uploads

    override suspend fun syncPending(): SyncSummary {
        syncCalls++
        return syncSummary
    }

    override suspend fun retry(localId: String): Result<Unit> {
        retried += localId
        return Result.success(Unit)
    }

    override suspend fun delete(localId: String): Result<Unit> {
        deleted += localId
        return Result.success(Unit)
    }

    override suspend fun resetStuckSyncing() = Unit

    override suspend fun pruneTerminal(olderThan: Duration) = Unit
}
