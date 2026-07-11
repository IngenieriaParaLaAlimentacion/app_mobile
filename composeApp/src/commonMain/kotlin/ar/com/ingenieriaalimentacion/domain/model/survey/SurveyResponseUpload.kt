package ar.com.ingenieriaalimentacion.domain.model.survey

import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState

/** Payload to enqueue a brand-new response (mirrors [ar.com.ingenieriaalimentacion.domain.model.subject.NewSubject]). */
data class NewSurveyResponse(
    val surveyId: String,
    val surveyName: String,
    val establishmentId: String,
    val establishmentName: String,
    val answers: Map<String, SurveyAnswerValue>,
    /** Epoch millis when the relevador actually finished the interview (client clock). */
    val eventAtMs: Long,
)

/** A locally-queued response plus its sync metadata. [localId] doubles as the SQLDelight PK
 *  and the wire idempotency `id` — same single-id design as [ar.com.ingenieriaalimentacion.domain.model.subject.SubjectUpload]. */
data class SurveyResponseUpload(
    val localId: String,
    val surveyId: String,
    val surveyName: String,
    val establishmentId: String,
    val establishmentName: String,
    val answersJson: String,
    val eventAt: String,
    val syncState: SyncState,
    val canonicalId: String?,
    val errorMessage: String?,
    val attemptCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

fun SurveyResponseUpload.isEligibleAt(nowMs: Long): Boolean {
    if (attemptCount == 0) return true
    val backoff = minOf(30_000L, 1_000L shl attemptCount) + (createdAt % 1_000L)
    return nowMs >= updatedAt + backoff
}
