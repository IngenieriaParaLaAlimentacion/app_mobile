package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyAnswerValue
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseEditStatus
import ar.com.ingenieriaalimentacion.domain.sync.SyncEngine
import kotlinx.coroutines.flow.Flow

interface SurveyResponseEditRepository : SyncEngine {
    /** Queue a full-replace edit for [responseId]. Coalesces into a still-PENDING edit for the
     *  same response instead of minting a new idempotency key (see ADR-0004). */
    suspend fun enqueueEdit(
        responseId: String,
        surveyId: String,
        surveyName: String,
        establishmentName: String,
        responseNumber: Int? = null,
        answers: Map<String, SurveyAnswerValue>,
        eventAtMs: Long,
    ): Result<Unit>

    /** Re-queue a single FAILED row for the next sync pass. */
    suspend fun retry(editId: String): Result<Unit>

    /** The most recent edit attempt for [responseId], reactively — null once none exist (or the
     *  latest one has been pruned). */
    fun observeEditStatus(responseId: String): Flow<SurveyResponseEditStatus?>

    // syncPending / resetStuckSyncing / pruneTerminal inherited from SyncEngine
}
