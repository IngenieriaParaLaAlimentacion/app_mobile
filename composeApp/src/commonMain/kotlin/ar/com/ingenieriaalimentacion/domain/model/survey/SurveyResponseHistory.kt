package ar.com.ingenieriaalimentacion.domain.model.survey

import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState

/** One row in the "My survey responses" list — union of remote (/me/activity) + local pending. */
data class SurveyResponseSummary(
    val id: String,
    val surveyName: String,
    val establishmentName: String,
    val eventAt: String,
    val syncState: SyncState,
    val isLocal: Boolean,
)

/**
 * Full answers, from GET /survey-responses/{id} or (offline fallback) the local queue.
 * [submittedById] is null only in the offline-local-fallback path — a row this device itself
 * queued or synced is by definition this device's own submission, so the "is the original
 * submitter" edit-gating check (Phase 6) treats a null [submittedById] as "yes, it's mine."
 */
data class SurveyResponseDetail(
    val id: String,
    val surveyId: String,
    val establishmentId: String,
    val submittedById: String?,
    val answers: Map<String, SurveyAnswerValue>,
    val eventAtMs: Long,
)
