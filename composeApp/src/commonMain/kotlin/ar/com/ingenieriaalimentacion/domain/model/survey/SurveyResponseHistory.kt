package ar.com.ingenieriaalimentacion.domain.model.survey

import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import kotlinx.serialization.Serializable

/** One row in the "My survey responses" list — union of remote (/me/activity) + local pending.
 *  `@Serializable` so it round-trips through `CachedActivityItem.payloadJson`, same as
 *  [ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryEntry]. */
@Serializable
data class SurveyResponseSummary(
    val id: String,
    /** Needed to open the response — every response endpoint is nested under its survey. */
    val surveyId: String,
    val surveyName: String,
    val responseTarget: SurveyResponseTarget,
    /** Denormalized display name for the target; null for an anonymous response. Callers render
     *  `targetLabel ?: "Anónima"` so anonymous reads as intentional rather than as missing data. */
    val targetLabel: String?,
    val eventAt: String,
    val syncState: SyncState,
    val isLocal: Boolean,
    /** Set only on a locally-queued row that hit a 409. Non-null ⇒ terminal: the row shows who
     *  already answered and offers only *Descartar*, never *Reintentar*. */
    val conflict: ResponseConflict? = null,
    /** The sync engine's user-facing reason on a FAILED row — rendered rather than dropped. */
    val errorMessage: String? = null,
    val responseNumber: Int? = null,
)

val SurveyResponseSummary.needsRequiredResponseNumber: Boolean
    get() = isLocal && syncState == SyncState.Failed && responseNumber == null &&
        errorMessage == MISSING_REQUIRED_RESPONSE_NUMBER_MESSAGE

/**
 * Full answers, from `GET /surveys/{surveyId}/responses/{id}` or (offline fallback) the local queue.
 * [submittedById] is null only in the offline-local-fallback path — a row this device itself
 * queued or synced is by definition this device's own submission, so the "is the original
 * submitter" edit-gating check treats a null [submittedById] as "yes, it's mine."
 *
 * The read endpoints return flat ids with no names, so [targetLabel] is only populated from this
 * device's own queue row; the screen falls back to the id, and to "Anónima" for an anonymous one.
 */
data class SurveyResponseDetail(
    val id: String,
    val surveyId: String,
    val responseTarget: SurveyResponseTarget,
    val targetId: String?,
    val targetLabel: String?,
    val submittedById: String?,
    val responseNumber: Int? = null,
    val answers: Map<String, SurveyAnswerValue>,
    val eventAtMs: Long,
)
