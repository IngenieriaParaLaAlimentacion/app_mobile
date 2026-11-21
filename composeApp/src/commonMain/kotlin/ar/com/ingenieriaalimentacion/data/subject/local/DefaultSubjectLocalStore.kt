package ar.com.ingenieriaalimentacion.data.subject.local

import ar.com.ingenieriaalimentacion.data.auth.SessionLifecycle
import ar.com.ingenieriaalimentacion.data.db.PendingSubjectQueries
import ar.com.ingenieriaalimentacion.data.db.observeForOwner
import ar.com.ingenieriaalimentacion.data.db.queryForOwner
import ar.com.ingenieriaalimentacion.data.subject.mapper.toDbString
import ar.com.ingenieriaalimentacion.data.subject.mapper.toDomain
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

/**
 * SQLDelight-backed [SubjectLocalStore]. All writes stamp `updatedAt` via the injected [now] source
 * so tests stay deterministic; DB I/O runs off the main thread on [dispatcher].
 */
class DefaultSubjectLocalStore(
    private val queries: PendingSubjectQueries,
    private val lifecycle: SessionLifecycle,
    private val now: () -> Long,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SubjectLocalStore {

    override fun observeAll(): Flow<List<SubjectUpload>> =
        observeForOwner(lifecycle, dispatcher, emptyList(), queries::selectAll) { query ->
            query.executeAsList().map { it.toDomain() }
        }

    override suspend fun all(): List<SubjectUpload> = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.selectAll(ownerUserId = owner).executeAsList().map { it.toDomain() }
    }

    override suspend fun find(localId: String): SubjectUpload? = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.selectById(ownerUserId = owner, localId = localId).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun insert(upload: SubjectUpload): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.insert(
            ownerUserId = owner,
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

    override suspend fun syncable(): List<SubjectUpload> = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.selectSyncable(ownerUserId = owner).executeAsList().map { it.toDomain() }
    }

    override suspend fun markSyncing(localId: String): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.markState(syncState = SyncState.Syncing.toDbString(), updatedAt = now(), localId = localId, ownerUserId = owner)
    }

    override suspend fun markSynced(localId: String, state: SyncState, canonicalId: String): Unit =
        lifecycle.queryForOwner(dispatcher) { owner ->
            queries.markSynced(
                ownerUserId = owner,
                syncState = state.toDbString(),
                canonicalId = canonicalId,
                updatedAt = now(),
                localId = localId,
            )
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
