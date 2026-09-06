package ar.com.ingenieriaalimentacion.domain.model.survey

import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import kotlinx.serialization.Serializable

/**
 * The chosen answer target for one response. Exactly one shape is valid per the survey's
 * [SurveyResponseTarget], and the sealed hierarchy is what makes "both a subject and an
 * establishment" unrepresentable rather than merely invalid.
 *
 * [label] is denormalized on purpose — the history list renders it offline with no join.
 */
sealed interface ResponseTargetRef {
    data class Subject(val id: String, val label: String) : ResponseTargetRef
    data class Establishment(val id: String, val label: String) : ResponseTargetRef
    data object Anonymous : ResponseTargetRef
}

/** The id this target sends to the server, or null for an anonymous response. */
val ResponseTargetRef.id: String?
    get() = when (this) {
        is ResponseTargetRef.Subject -> id
        is ResponseTargetRef.Establishment -> id
        ResponseTargetRef.Anonymous -> null
    }

/** Display name for the target, or null for an anonymous response. */
val ResponseTargetRef.label: String?
    get() = when (this) {
        is ResponseTargetRef.Subject -> label
        is ResponseTargetRef.Establishment -> label
        ResponseTargetRef.Anonymous -> null
    }

/** Which [SurveyResponseTarget] this ref satisfies. */
val ResponseTargetRef.kind: SurveyResponseTarget
    get() = when (this) {
        is ResponseTargetRef.Subject -> SurveyResponseTarget.Subject
        is ResponseTargetRef.Establishment -> SurveyResponseTarget.Establishment
        ResponseTargetRef.Anonymous -> SurveyResponseTarget.Anonymous
    }

/** Payload to enqueue a brand-new response (mirrors [ar.com.ingenieriaalimentacion.domain.model.subject.NewSubject]). */
data class NewSurveyResponse(
    val surveyId: String,
    val surveyName: String,
    val target: ResponseTargetRef,
    val responseNumber: Int,
    val answers: Map<String, SurveyAnswerValue>,
    /** Epoch millis when the relevador actually finished the interview (client clock). */
    val eventAtMs: Long,
)

/** Who already answered this survey for this target, and when — the `409` payload.
 *  `@Serializable` because it rides along on [SurveyResponseSummary], which round-trips through
 *  `CachedActivityItem.payloadJson`. */
@Serializable
enum class ResponseConflictKind { Target, ResponseNumber }

@Serializable
data class ResponseConflict(
    val submittedByName: String,
    val eventAt: String,
    val kind: ResponseConflictKind = ResponseConflictKind.Target,
    val existingResponseNumber: Int? = null,
)

/** A locally-queued response plus its sync metadata. [localId] doubles as the SQLDelight PK
 *  and the wire idempotency `id` — same single-id design as [ar.com.ingenieriaalimentacion.domain.model.subject.SubjectUpload].
 *  Reusing it on every attempt is what makes a dropped-connection retry replay as `200` rather
 *  than surfacing as a spurious duplicate. */
data class SurveyResponseUpload(
    val localId: String,
    val surveyId: String,
    val surveyName: String,
    val target: ResponseTargetRef,
    val answersJson: String,
    val eventAt: String,
    val syncState: SyncState,
    val canonicalId: String?,
    val errorMessage: String?,
    /** Set only on a 409: who already answered, and when. Non-null ⇒ terminal, never retried. */
    val conflict: ResponseConflict?,
    val attemptCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val responseNumber: Int?,
)

fun SurveyResponseUpload.isEligibleAt(nowMs: Long): Boolean {
    // A conflicted row can never succeed — another response already owns this survey+target — so
    // it stays out of every backoff sweep until the user discards it.
    if (conflict?.kind == ResponseConflictKind.Target) return false
    if (attemptCount == 0) return true
    val backoff = minOf(30_000L, 1_000L shl attemptCount) + (createdAt % 1_000L)
    return nowMs >= updatedAt + backoff
}
