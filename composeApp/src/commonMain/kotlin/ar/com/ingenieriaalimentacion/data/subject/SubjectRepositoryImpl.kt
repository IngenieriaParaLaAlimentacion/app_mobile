package ar.com.ingenieriaalimentacion.data.subject

import ar.com.ingenieriaalimentacion.data.subject.local.SubjectLocalStore
import ar.com.ingenieriaalimentacion.data.subject.mapper.toPendingUpload
import ar.com.ingenieriaalimentacion.data.subject.mapper.toRequest
import ar.com.ingenieriaalimentacion.domain.model.subject.MAX_SYNC_ATTEMPTS
import ar.com.ingenieriaalimentacion.domain.model.subject.NewSubject
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncSummary
import ar.com.ingenieriaalimentacion.domain.model.subject.isEligibleAt
import ar.com.ingenieriaalimentacion.domain.repository.SubjectRepository
import ar.com.ingenieriaalimentacion.domain.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * Offline-first subject repository: the local DB is the source of truth. [enqueue] writes a PENDING
 * row instantly; [syncPending] pushes eligible rows to the backend and reconciles each outcome into
 * a local state transition.
 */
class SubjectRepositoryImpl(
    private val api: SubjectApi,
    private val local: SubjectLocalStore,
    private val idGenerator: IdGenerator,
    private val now: () -> Long,
) : SubjectRepository {

    override suspend fun enqueue(newSubject: NewSubject): Result<String> = runCatching {
        val localId = idGenerator.newId()
        local.insert(newSubject.toPendingUpload(id = localId, now = now()))
        localId
    }

    override fun observeUploads(): Flow<List<SubjectUpload>> = local.observeAll()

    override suspend fun syncPending(): SyncSummary {
        var synced = 0
        var merged = 0
        var failed = 0
        var pending = 0
        // Backoff: read all PENDING, then keep only rows whose backoff window has elapsed.
        local.syncable().filter { it.isEligibleAt(now()) }.forEach { upload ->
            local.markSyncing(upload.localId)
            when (val outcome = api.create(upload.toRequest())) {
                is SubjectUploadOutcome.Created -> {
                    local.markSynced(upload.localId, SyncState.Synced, outcome.response.id)
                    synced++
                }
                is SubjectUploadOutcome.Duplicate -> {
                    local.markSynced(upload.localId, SyncState.Merged, outcome.canonicalId)
                    merged++
                }
                SubjectUploadOutcome.InvalidData -> {
                    local.markFailed(upload.localId, "Los datos no son válidos.")
                    failed++
                }
                SubjectUploadOutcome.Forbidden -> {
                    local.markFailed(upload.localId, "No tenés permisos para cargar sujetos.")
                    failed++
                }
                // 401/network: stay PENDING + backoff until the cap, then dead-letter to FAILED.
                SubjectUploadOutcome.Unauthorized ->
                    if (retryOrDeadLetter(upload, "Sesión expirada. Reintentando…")) pending++ else failed++
                is SubjectUploadOutcome.Retryable ->
                    if (retryOrDeadLetter(upload, "Sin conexión. Reintentando…")) pending++ else failed++
            }
        }
        return SyncSummary(synced = synced, merged = merged, failed = failed, stillPending = pending)
    }

    /**
     * Stay PENDING + count the attempt while under the cap (returns true); dead-letter to FAILED at
     * the cap (returns false).
     */
    private suspend fun retryOrDeadLetter(upload: SubjectUpload, retryMsg: String): Boolean =
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
