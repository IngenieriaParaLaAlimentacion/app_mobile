package ar.com.ingenieriaalimentacion.data.survey.local

import ar.com.ingenieriaalimentacion.data.auth.SessionLifecycle
import ar.com.ingenieriaalimentacion.data.db.PendingSurveyResponseQueries
import ar.com.ingenieriaalimentacion.data.db.observeForOwner
import ar.com.ingenieriaalimentacion.data.db.queryForOwner
import ar.com.ingenieriaalimentacion.data.survey.mapper.toDomain
import ar.com.ingenieriaalimentacion.data.survey.mapper.toSurveyDbString
import ar.com.ingenieriaalimentacion.data.survey.mapper.toDbString
import ar.com.ingenieriaalimentacion.domain.model.survey.ResponseConflict
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseUpload
import ar.com.ingenieriaalimentacion.domain.model.survey.id
import ar.com.ingenieriaalimentacion.domain.model.survey.label
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

/**
 * SQLDelight-backed [SurveyResponseLocalStore]. All writes stamp `updatedAt` via the injected [now]
 * source so tests stay deterministic; DB I/O runs off the main thread on [dispatcher]. Mirrors
 * [ar.com.ingenieriaalimentacion.data.subject.local.DefaultSubjectLocalStore].
 */
class DefaultSurveyResponseLocalStore(
    private val queries: PendingSurveyResponseQueries,
    private val lifecycle: SessionLifecycle,
    private val now: () -> Long,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SurveyResponseLocalStore {

    override fun observeAll(): Flow<List<SurveyResponseUpload>> =
        observeForOwner(lifecycle, dispatcher, emptyList(), queries::selectAll) { query ->
            query.executeAsList().map { it.toDomain() }
        }

    override suspend fun all(): List<SurveyResponseUpload> = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.selectAll(ownerUserId = owner).executeAsList().map { it.toDomain() }
    }

    override suspend fun find(localId: String): SurveyResponseUpload? = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.selectById(ownerUserId = owner, localId = localId).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun insert(upload: SurveyResponseUpload): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.insert(
            ownerUserId = owner,
            localId = upload.localId,
            surveyId = upload.surveyId,
            surveyName = upload.surveyName,
            responseTarget = upload.target.toDbString(),
            targetId = upload.target.id,
            targetLabel = upload.target.label,
            answersJson = upload.answersJson,
            eventAt = upload.eventAt,
            syncState = upload.syncState.toSurveyDbString(),
            canonicalId = upload.canonicalId,
            errorMessage = upload.errorMessage,
            conflictBy = upload.conflict?.submittedByName,
            conflictAt = upload.conflict?.eventAt,
            attemptCount = upload.attemptCount.toLong(),
            createdAt = upload.createdAt,
            updatedAt = upload.updatedAt,
            responseNumber = upload.responseNumber?.toLong(),
        )
    }

    override suspend fun syncable(): List<SurveyResponseUpload> = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.selectSyncable(ownerUserId = owner).executeAsList().map { it.toDomain() }
    }

    override suspend fun awaitingNumberPolicy(): List<SurveyResponseUpload> = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.selectAwaitingNumberPolicy(ownerUserId = owner).executeAsList().map { it.toDomain() }
    }

    override suspend fun markSyncing(localId: String): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.markSyncing(updatedAt = now(), localId = localId, ownerUserId = owner)
    }

    override suspend fun markSynced(localId: String, canonicalId: String): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.markSynced(canonicalId = canonicalId, updatedAt = now(), localId = localId, ownerUserId = owner)
    }

    override suspend fun markFailed(localId: String, reason: String): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.markFailed(errorMessage = reason, updatedAt = now(), localId = localId, ownerUserId = owner)
    }

    override suspend fun markConflict(localId: String, conflict: ResponseConflict): Unit =
        lifecycle.queryForOwner(dispatcher) { owner ->
            queries.markConflict(
                ownerUserId = owner,
                conflictBy = conflict.submittedByName,
                conflictAt = conflict.eventAt,
                updatedAt = now(),
                localId = localId,
            )
        }

    override suspend fun updateResponseNumber(localId: String, responseNumber: Int): Unit =
        lifecycle.queryForOwner(dispatcher) { owner ->
            queries.updateResponseNumber(responseNumber = responseNumber.toLong(), updatedAt = now(), localId = localId, ownerUserId = owner)
        }

    override suspend fun delete(localId: String): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.deleteById(ownerUserId = owner, localId = localId)
    }

    override suspend fun bumpRetry(localId: String, reason: String): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.bumpRetry(errorMessage = reason, updatedAt = now(), localId = localId, ownerUserId = owner)
    }

    override suspend fun resetForRetry(localId: String): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.resetForRetry(updatedAt = now(), localId = localId, ownerUserId = owner)
    }

    override suspend fun resetStuckSyncing(): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.resetSyncingToPending(updatedAt = now(), ownerUserId = owner)
    }

    override suspend fun pruneTerminalOlderThan(cutoff: Long): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.pruneTerminalOlderThan(ownerUserId = owner, cutoff = cutoff)
    }
}
