package ar.com.ingenieriaalimentacion.data.survey.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import ar.com.ingenieriaalimentacion.data.db.PendingSurveyResponseEditQueries
import ar.com.ingenieriaalimentacion.data.survey.mapper.toDomain
import ar.com.ingenieriaalimentacion.data.survey.mapper.toSurveyDbString
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseEditUpload
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed [SurveyResponseEditLocalStore]. All writes stamp `updatedAt` via the injected
 * [now] source so tests stay deterministic; DB I/O runs off the main thread on [dispatcher].
 */
class DefaultSurveyResponseEditLocalStore(
    private val queries: PendingSurveyResponseEditQueries,
    private val now: () -> Long,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SurveyResponseEditLocalStore {

    override suspend fun findPendingForResponse(responseId: String): SurveyResponseEditUpload? =
        withContext(dispatcher) {
            queries.selectPendingForResponse(responseId).executeAsOneOrNull()?.toDomain()
        }

    override fun observeLatestForResponse(responseId: String): Flow<SurveyResponseEditUpload?> =
        queries.selectLatestForResponse(responseId).asFlow().mapToOneOrNull(dispatcher).map { it?.toDomain() }

    override suspend fun updatePending(
        editId: String,
        surveyName: String,
        establishmentName: String,
        answersJson: String,
        eventAt: String,
        responseNumber: Int?,
        now: Long,
    ): Unit = withContext(dispatcher) {
        queries.updatePending(
            surveyName = surveyName,
            establishmentName = establishmentName,
            answersJson = answersJson,
            eventAt = eventAt,
            responseNumber = responseNumber?.toLong(),
            updatedAt = now,
            editId = editId,
        )
    }

    override suspend fun insert(edit: SurveyResponseEditUpload): Unit = withContext(dispatcher) {
        queries.insert(
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

    override suspend fun syncable(): List<SurveyResponseEditUpload> = withContext(dispatcher) {
        queries.selectSyncable().executeAsList().map { it.toDomain() }
    }

    override suspend fun markSyncing(editId: String): Unit = withContext(dispatcher) {
        queries.markSyncing(updatedAt = now(), editId = editId)
    }

    override suspend fun markSynced(editId: String): Unit = withContext(dispatcher) {
        queries.markSynced(updatedAt = now(), editId = editId)
    }

    override suspend fun markFailed(editId: String, reason: String): Unit = withContext(dispatcher) {
        queries.markFailed(errorMessage = reason, updatedAt = now(), editId = editId)
    }

    override suspend fun bumpRetry(editId: String, reason: String): Unit = withContext(dispatcher) {
        queries.bumpRetry(errorMessage = reason, updatedAt = now(), editId = editId)
    }

    override suspend fun resetForRetry(editId: String): Unit = withContext(dispatcher) {
        queries.resetForRetry(updatedAt = now(), editId = editId)
    }

    override suspend fun resetStuckSyncing(): Unit = withContext(dispatcher) {
        queries.resetSyncingToPending(updatedAt = now())
    }

    override suspend fun pruneTerminalOlderThan(cutoff: Long): Unit = withContext(dispatcher) {
        queries.pruneTerminalOlderThan(cutoff)
    }
}
