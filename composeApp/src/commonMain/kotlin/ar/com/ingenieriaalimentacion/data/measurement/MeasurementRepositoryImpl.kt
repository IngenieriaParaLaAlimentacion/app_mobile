package ar.com.ingenieriaalimentacion.data.measurement

import ar.com.ingenieriaalimentacion.data.measurement.local.MeasurementLocalStore
import ar.com.ingenieriaalimentacion.data.measurement.mapper.encodeAlerts
import ar.com.ingenieriaalimentacion.data.measurement.mapper.toDomain
import ar.com.ingenieriaalimentacion.data.measurement.mapper.toPendingUpload
import ar.com.ingenieriaalimentacion.data.measurement.mapper.toRequest
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementUpload
import ar.com.ingenieriaalimentacion.domain.model.measurement.NewMeasurement
import ar.com.ingenieriaalimentacion.domain.model.measurement.isEligibleAt
import ar.com.ingenieriaalimentacion.domain.model.subject.MAX_SYNC_ATTEMPTS
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncSummary
import ar.com.ingenieriaalimentacion.domain.repository.MeasurementRepository
import ar.com.ingenieriaalimentacion.domain.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Offline-first measurement repository: the local DB is the source of truth. [enqueue] writes a
 * PENDING row instantly; [syncPending] pushes eligible rows and reconciles each outcome into a local
 * state transition, persisting server-computed `bmi`/`alerts` on success. Parallel to
 * [ar.com.ingenieriaalimentacion.data.subject.SubjectRepositoryImpl] on a separate queue.
 */
class MeasurementRepositoryImpl(
    private val api: MeasurementApi,
    private val local: MeasurementLocalStore,
    private val idGenerator: IdGenerator,
    private val now: () -> Long,
) : MeasurementRepository {

    override suspend fun enqueue(measurement: NewMeasurement): Result<Unit> = runCatching {
        val nowMs = now()
        val measuredAt = Instant.fromEpochMilliseconds(nowMs).toString()   // ISO 8601 …Z
        local.insert(
            measurement.toPendingUpload(id = idGenerator.newId(), measuredAt = measuredAt, now = nowMs),
        )
    }

    override fun observeUploads(): Flow<List<MeasurementUpload>> = local.observeAll()

    override suspend fun syncPending(): SyncSummary {
        var synced = 0
        var failed = 0
        var pending = 0
        // Backoff: read all PENDING, then keep only rows whose backoff window has elapsed.
        local.syncable().filter { it.isEligibleAt(now()) }.forEach { upload ->
            local.markSyncing(upload.localId)
            when (val outcome = api.create(upload.toRequest())) {
                is MeasurementUploadOutcome.Created -> {
                    val alertsJson = encodeAlerts(outcome.response.alerts.map { it.toDomain() })
                    local.markSynced(upload.localId, outcome.response.bmi, alertsJson)
                    synced++
                }
                MeasurementUploadOutcome.NotFound -> {
                    local.markFailed(upload.localId, "El sujeto o establecimiento no existe.")
                    failed++
                }
                MeasurementUploadOutcome.Forbidden -> {
                    local.markFailed(upload.localId, "No tenés acceso a ese establecimiento.")
                    failed++
                }
                MeasurementUploadOutcome.InvalidData -> {
                    local.markFailed(upload.localId, "Datos inválidos. Revisá peso, talla y fecha.")
                    failed++
                }
                // 401/network: stay PENDING + backoff until the cap, then dead-letter to FAILED.
                MeasurementUploadOutcome.Unauthorized ->
                    if (retryOrDeadLetter(upload, "Sesión expirada. Reintentando…")) pending++ else failed++
                is MeasurementUploadOutcome.Retryable ->
                    if (retryOrDeadLetter(upload, "Sin conexión. Reintentando…")) pending++ else failed++
            }
        }
        return SyncSummary(synced = synced, merged = 0, failed = failed, stillPending = pending)
    }

    /**
     * Stay PENDING + count the attempt while under the cap (returns true); dead-letter to FAILED at
     * the cap (returns false).
     */
    private suspend fun retryOrDeadLetter(upload: MeasurementUpload, retryMsg: String): Boolean =
        if (upload.attemptCount + 1 >= MAX_SYNC_ATTEMPTS) {
            local.markFailed(upload.localId, "No se pudo sincronizar tras varios intentos.")
            false
        } else {
            local.bumpRetry(upload.localId, retryMsg)
            true
        }

    override suspend fun retry(localId: String): Result<Unit> = runCatching { local.resetForRetry(localId) }

    override suspend fun delete(localId: String): Result<Unit> = runCatching { local.delete(localId) }

    override suspend fun resetStuckSyncing() {
        local.resetStuckSyncing()
    }

    override suspend fun pruneTerminal(olderThan: Duration) {
        local.pruneTerminalOlderThan(now() - olderThan.inWholeMilliseconds)
    }
}
