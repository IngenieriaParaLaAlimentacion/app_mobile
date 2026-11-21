package ar.com.ingenieriaalimentacion.data.survey.local

import ar.com.ingenieriaalimentacion.data.auth.SessionLifecycle
import ar.com.ingenieriaalimentacion.data.db.PendingSurveyResponseEditQueries
import ar.com.ingenieriaalimentacion.data.db.observeForOwner
import ar.com.ingenieriaalimentacion.data.db.queryForOwner
import ar.com.ingenieriaalimentacion.data.survey.mapper.toDomain
import ar.com.ingenieriaalimentacion.data.survey.mapper.toSurveyDbString
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseEditUpload
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

/**
 * SQLDelight-backed [SurveyResponseEditLocalStore]. All writes stamp `updatedAt` via the injected
 * [now] source so tests stay deterministic; DB I/O runs off the main thread on [dispatcher].
 */
class DefaultSurveyResponseEditLocalStore(
    private val queries: PendingSurveyResponseEditQueries,
    private val lifecycle: SessionLifecycle,
    private val now: () -> Long,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SurveyResponseEditLocalStore {

    override fun observeLatestForResponse(responseId: String): Flow<SurveyResponseEditUpload?> =
        observeForOwner(lifecycle, dispatcher, null, { owner ->
            queries.selectLatestForResponse(ownerUserId = owner, responseId = responseId)
        }) { query -> query.executeAsOneOrNull()?.toDomain() }

    override suspend fun enqueue(edit: SurveyResponseEditUpload): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.transaction {
            val unsent = queries.selectUnsentForResponse(owner, edit.responseId).executeAsOneOrNull()
            if (unsent == null) {
                insert(owner, edit)
            } else {
                queries.updatePending(
                    ownerUserId = owner,
                    surveyName = edit.surveyName,
                    establishmentName = edit.establishmentName,
                    answersJson = edit.answersJson,
                    eventAt = edit.eventAt,
                    responseNumber = edit.responseNumber?.toLong(),
                    updatedAt = edit.updatedAt,
                    editId = unsent.editId,
                )
            }
        }
    }

    private fun insert(owner: String, edit: SurveyResponseEditUpload) {
        queries.insert(
            ownerUserId = owner,
            editId = edit.editId,
            responseId = edit.responseId,
            surveyId = edit.surveyId,
            surveyName = edit.surveyName,
            establishmentName = edit.establishmentName,
            answersJson = edit.answersJson,
            eventAt = edit.eventAt,
            syncState = edit.syncState.toSurveyDbString(),
            errorMessage = edit.errorMessage,
            attemptCount = edit.attemptCount.toLong(),
            createdAt = edit.createdAt,
            updatedAt = edit.updatedAt,
            responseNumber = edit.responseNumber?.toLong(),
        )
    }

    override suspend fun syncable(): List<SurveyResponseEditUpload> = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.selectSyncable(ownerUserId = owner).executeAsList().map { it.toDomain() }
    }

    override suspend fun awaitingNumberPolicy(): List<SurveyResponseEditUpload> = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.selectAwaitingNumberPolicy(ownerUserId = owner).executeAsList().map { it.toDomain() }
    }

    override suspend fun claimForSync(edit: SurveyResponseEditUpload): Boolean =
        lifecycle.queryForOwner(dispatcher) { owner ->
            queries.transactionWithResult {
                val current = queries.selectById(owner, edit.editId).executeAsOneOrNull()?.toDomain()
                if (current != edit) return@transactionWithResult false
                queries.markSyncing(updatedAt = now(), editId = edit.editId, ownerUserId = owner)
                true
            }
        }

    override suspend fun markSynced(editId: String): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.markSynced(updatedAt = now(), editId = editId, ownerUserId = owner)
    }

    override suspend fun markFailed(editId: String, reason: String): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.markFailed(errorMessage = reason, updatedAt = now(), editId = editId, ownerUserId = owner)
    }

    override suspend fun bumpRetry(editId: String, reason: String): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.bumpRetry(errorMessage = reason, updatedAt = now(), editId = editId, ownerUserId = owner)
    }

    override suspend fun resetForRetry(editId: String): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.resetForRetry(updatedAt = now(), editId = editId, ownerUserId = owner)
    }

    override suspend fun resetStuckSyncing(): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.resetSyncingToPending(updatedAt = now(), ownerUserId = owner)
    }

    override suspend fun pruneTerminalOlderThan(cutoff: Long): Unit = lifecycle.queryForOwner(dispatcher) { owner ->
        queries.pruneTerminalOlderThan(ownerUserId = owner, cutoff = cutoff)
    }
}
