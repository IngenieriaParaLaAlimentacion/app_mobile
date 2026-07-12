package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyAnswerValue
import ar.com.ingenieriaalimentacion.domain.sync.SyncEngine

interface SurveyResponseEditRepository : SyncEngine {
    /** Queue a full-replace edit for [responseId]. Coalesces into a still-PENDING edit for the
     *  same response instead of minting a new idempotency key (see ADR-0004). */
    suspend fun enqueueEdit(
        responseId: String,
        surveyName: String,
        establishmentName: String,
        answers: Map<String, SurveyAnswerValue>,
        eventAtMs: Long,
    ): Result<Unit>

    /** Re-queue a single FAILED row for the next sync pass. */
    suspend fun retry(editId: String): Result<Unit>

    // syncPending / resetStuckSyncing / pruneTerminal inherited from SyncEngine
}
