package ar.com.ingenieriaalimentacion.domain.model.survey

import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState

/**
 * A locally-queued edit *attempt*. [editId] is a UUID that is a SEPARATE idempotency key
 * from [responseId] — the response being edited. A fresh [editId] is minted only the first
 * time an edit is enqueued for a given [responseId]; while that edit attempt is still PENDING
 * (never transmitted), later local edits overwrite it in place rather than minting a new id
 * (see `SurveyResponseEditRepositoryImpl.enqueueEdit`) — reusing an id that already reached
 * the server would silently no-op, but overwriting a row that was never sent carries no such
 * risk.
 */
data class SurveyResponseEditUpload(
    val editId: String,
    val responseId: String,
    val surveyName: String,
    val establishmentName: String,
    val answersJson: String,
    val eventAt: String,
    val syncState: SyncState,
    val errorMessage: String?,
    val attemptCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

fun SurveyResponseEditUpload.isEligibleAt(nowMs: Long): Boolean {
    if (attemptCount == 0) return true
    val backoff = minOf(30_000L, 1_000L shl attemptCount) + (createdAt % 1_000L)
    return nowMs >= updatedAt + backoff
}
