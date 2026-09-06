package ar.com.ingenieriaalimentacion.data.survey.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import ar.com.ingenieriaalimentacion.data.db.PendingSurveyResponseQueries
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed [SurveyResponseLocalStore]. All writes stamp `updatedAt` via the injected [now]
 * source so tests stay deterministic; DB I/O runs off the main thread on [dispatcher]. Mirrors
 * [ar.com.ingenieriaalimentacion.data.subject.local.DefaultSubjectLocalStore].
 */
class DefaultSurveyResponseLocalStore(
    private val queries: PendingSurveyResponseQueries,
    private val now: () -> Long,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SurveyResponseLocalStore {

    override fun observeAll(): Flow<List<SurveyResponseUpload>> =
        queries.selectAll().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override suspend fun all(): List<SurveyResponseUpload> = withContext(dispatcher) {
        queries.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun find(localId: String): SurveyResponseUpload? = withContext(dispatcher) {
        queries.selectById(localId).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun insert(upload: SurveyResponseUpload): Unit = withContext(dispatcher) {
        queries.insert(
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
            conflictKind = upload.conflict?.kind?.toDbString(),
            conflictNumber = upload.conflict?.existingResponseNumber?.toLong(),
        )
    }

    override suspend fun syncable(): List<SurveyResponseUpload> = withContext(dispatcher) {
        queries.selectSyncable().executeAsList().map { it.toDomain() }
    }

    override suspend fun markSyncing(localId: String): Unit = withContext(dispatcher) {
        queries.markSyncing(updatedAt = now(), localId = localId)
    }

    override suspend fun markSynced(localId: String, canonicalId: String): Unit = withContext(dispatcher) {
        queries.markSynced(canonicalId = canonicalId, updatedAt = now(), localId = localId)
    }

    override suspend fun markFailed(localId: String, reason: String): Unit = withContext(dispatcher) {
        queries.markFailed(errorMessage = reason, updatedAt = now(), localId = localId)
    }

    override suspend fun markConflict(localId: String, conflict: ResponseConflict): Unit =
        withContext(dispatcher) {
            queries.markConflict(
                conflictBy = conflict.submittedByName,
                conflictAt = conflict.eventAt,
                conflictKind = conflict.kind.toDbString(),
                conflictNumber = conflict.existingResponseNumber?.toLong(),
                updatedAt = now(),
                localId = localId,
            )
        }

    override suspend fun updateResponseNumber(localId: String, responseNumber: Int): Unit =
        withContext(dispatcher) {
            queries.updateResponseNumber(responseNumber.toLong(), now(), localId)
        }

    override suspend fun findBySurveyAndNumber(
        surveyId: String,
        responseNumber: Int,
        excludeLocalId: String,
    ): SurveyResponseUpload? = withContext(dispatcher) {
        queries.selectBySurveyAndNumber(surveyId, responseNumber.toLong(), excludeLocalId)
            .executeAsOneOrNull()?.toDomain()
    }

    override suspend fun delete(localId: String): Unit = withContext(dispatcher) {
        queries.deleteById(localId)
    }

    override suspend fun bumpRetry(localId: String, reason: String): Unit = withContext(dispatcher) {
        queries.bumpRetry(errorMessage = reason, updatedAt = now(), localId = localId)
    }

    override suspend fun resetForRetry(localId: String): Unit = withContext(dispatcher) {
        queries.resetForRetry(updatedAt = now(), localId = localId)
    }

    override suspend fun resetStuckSyncing(): Unit = withContext(dispatcher) {
        queries.resetSyncingToPending(updatedAt = now())
    }

    override suspend fun pruneTerminalOlderThan(cutoff: Long): Unit = withContext(dispatcher) {
        queries.pruneTerminalOlderThan(cutoff)
    }
}
