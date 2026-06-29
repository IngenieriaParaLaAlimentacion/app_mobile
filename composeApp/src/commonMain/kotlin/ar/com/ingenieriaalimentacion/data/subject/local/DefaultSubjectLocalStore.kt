package ar.com.ingenieriaalimentacion.data.subject.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import ar.com.ingenieriaalimentacion.data.db.PendingSubjectQueries
import ar.com.ingenieriaalimentacion.data.subject.mapper.toDbString
import ar.com.ingenieriaalimentacion.data.subject.mapper.toDomain
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed [SubjectLocalStore]. All writes stamp `updatedAt` via the injected [now] source
 * so tests stay deterministic; DB I/O runs off the main thread on [dispatcher].
 */
class DefaultSubjectLocalStore(
    private val queries: PendingSubjectQueries,
    private val now: () -> Long,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SubjectLocalStore {

    override fun observeAll(): Flow<List<SubjectUpload>> =
        queries.selectAll().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override suspend fun all(): List<SubjectUpload> = withContext(dispatcher) {
        queries.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun find(localId: String): SubjectUpload? = withContext(dispatcher) {
        queries.selectById(localId).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun insert(upload: SubjectUpload): Unit = withContext(dispatcher) {
        queries.insert(
            localId = upload.localId,
            dni = upload.dni.toLong(),
            sex = upload.sex.toDbString(),
            name = upload.name,
            lastName = upload.lastName,
            birthDate = upload.birthDate.toString(),
            isRural = if (upload.isRural) 1L else 0L,
            isIndigenous = if (upload.isIndigenous) 1L else 0L,
            syncState = upload.syncState.toDbString(),
            canonicalId = upload.canonicalId,
            errorMessage = upload.errorMessage,
            attemptCount = upload.attemptCount.toLong(),
            createdAt = upload.createdAt,
            updatedAt = upload.updatedAt,
        )
    }

    override suspend fun syncable(): List<SubjectUpload> = withContext(dispatcher) {
        queries.selectSyncable().executeAsList().map { it.toDomain() }
    }

    override suspend fun markSyncing(localId: String): Unit = withContext(dispatcher) {
        queries.markState(syncState = SyncState.Syncing.toDbString(), updatedAt = now(), localId = localId)
    }

    override suspend fun markSynced(localId: String, state: SyncState, canonicalId: String): Unit =
        withContext(dispatcher) {
            queries.markSynced(
                syncState = state.toDbString(),
                canonicalId = canonicalId,
                updatedAt = now(),
                localId = localId,
            )
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
