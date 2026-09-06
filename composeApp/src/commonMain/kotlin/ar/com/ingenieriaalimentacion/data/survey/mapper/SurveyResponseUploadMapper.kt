package ar.com.ingenieriaalimentacion.data.survey.mapper

import ar.com.ingenieriaalimentacion.data.db.PendingSurveyResponse
import ar.com.ingenieriaalimentacion.data.survey.dto.SurveyResponseRequest
import ar.com.ingenieriaalimentacion.data.survey.local.SurveyResponseLocalStore
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.model.survey.ResponseConflict
import ar.com.ingenieriaalimentacion.domain.model.survey.ResponseConflictKind
import ar.com.ingenieriaalimentacion.domain.model.survey.ResponseTargetRef
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseUpload
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal fun PendingSurveyResponse.toDomain(): SurveyResponseUpload = SurveyResponseUpload(
    localId = localId,
    surveyId = surveyId,
    surveyName = surveyName,
    target = toTargetRef(),
    answersJson = answersJson,
    eventAt = eventAt,
    syncState = syncState.toSurveySyncState(),
    canonicalId = canonicalId,
    errorMessage = errorMessage,
    // conflictBy is the discriminator: conflictAt can legitimately be blank if the server omitted
    // event_at, but a row is only ever a conflict if a submitter name was written with it.
    conflict = conflictBy?.let {
        ResponseConflict(it, conflictAt.orEmpty(), conflictKind.toResponseConflictKind(), conflictNumber?.toInt())
    },
    attemptCount = attemptCount.toInt(),
    createdAt = createdAt,
    updatedAt = updatedAt,
    responseNumber = responseNumber?.toInt(),
)

/** A row whose `responseTarget` says subject/establishment but whose `targetId` is NULL is
 *  unrepresentable through [SurveyResponseLocalStore], so it can only come from a hand-edited DB;
 *  treat it as anonymous rather than crashing the whole history list. */
private fun PendingSurveyResponse.toTargetRef(): ResponseTargetRef = when (responseTarget) {
    "SUBJECT" -> targetId?.let { ResponseTargetRef.Subject(it, targetLabel.orEmpty()) }
        ?: ResponseTargetRef.Anonymous
    "ESTABLISHMENT" -> targetId?.let { ResponseTargetRef.Establishment(it, targetLabel.orEmpty()) }
        ?: ResponseTargetRef.Anonymous
    "ANONYMOUS" -> ResponseTargetRef.Anonymous
    else -> error("Unknown responseTarget stored: $responseTarget")
}

internal fun ResponseTargetRef.toDbString(): String = when (this) {
    is ResponseTargetRef.Subject -> "SUBJECT"
    is ResponseTargetRef.Establishment -> "ESTABLISHMENT"
    ResponseTargetRef.Anonymous -> "ANONYMOUS"
}

/** Queued upload → POST body. The [SurveyResponseUpload.localId] doubles as the idempotency `id`;
 *  `survey_id` is not in the body — it is a path segment. [subjectIdOverride] lets the sync engine
 *  swap in a subject's server-side canonical id after a DNI merge. */
internal fun SurveyResponseUpload.toRequest(
    json: Json,
    responseNumber: Int,
    subjectIdOverride: String? = null,
): SurveyResponseRequest = SurveyResponseRequest(
    id = localId,
    subjectId = (target as? ResponseTargetRef.Subject)?.let { subjectIdOverride ?: it.id },
    establishmentId = (target as? ResponseTargetRef.Establishment)?.id,
    responseNumber = responseNumber,
    answers = json.decodeFromString<Map<String, JsonElement>>(answersJson),
    eventAt = eventAt,
)

internal fun ResponseConflictKind.toDbString(): String = when (this) {
    ResponseConflictKind.Target -> "TARGET"
    ResponseConflictKind.ResponseNumber -> "RESPONSE_NUMBER"
}

internal fun String?.toResponseConflictKind(): ResponseConflictKind =
    if (this == "RESPONSE_NUMBER") ResponseConflictKind.ResponseNumber else ResponseConflictKind.Target

internal fun SyncState.toSurveyDbString(): String = when (this) {
    SyncState.Pending -> "PENDING"
    SyncState.Syncing -> "SYNCING"
    SyncState.Synced -> "SYNCED"
    SyncState.Merged -> error("Survey responses have no MERGED state")
    SyncState.Failed -> "FAILED"
}

internal fun String.toSurveySyncState(): SyncState = when (this) {
    "PENDING" -> SyncState.Pending
    "SYNCING" -> SyncState.Syncing
    "SYNCED" -> SyncState.Synced
    "FAILED" -> SyncState.Failed
    else -> error("Unknown syncState stored: $this")
}
