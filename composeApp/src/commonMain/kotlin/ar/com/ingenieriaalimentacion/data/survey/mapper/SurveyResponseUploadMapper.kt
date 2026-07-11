package ar.com.ingenieriaalimentacion.data.survey.mapper

import ar.com.ingenieriaalimentacion.data.db.PendingSurveyResponse
import ar.com.ingenieriaalimentacion.data.survey.dto.SurveyResponseRequest
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseUpload
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal fun PendingSurveyResponse.toDomain(): SurveyResponseUpload = SurveyResponseUpload(
    localId = localId,
    surveyId = surveyId,
    surveyName = surveyName,
    establishmentId = establishmentId,
    establishmentName = establishmentName,
    answersJson = answersJson,
    eventAt = eventAt,
    syncState = syncState.toSurveySyncState(),
    canonicalId = canonicalId,
    errorMessage = errorMessage,
    attemptCount = attemptCount.toInt(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Queued upload → POST body. The [SurveyResponseUpload.localId] doubles as the idempotency `id`. */
internal fun SurveyResponseUpload.toRequest(json: Json): SurveyResponseRequest = SurveyResponseRequest(
    id = localId,
    surveyId = surveyId,
    establishmentId = establishmentId,
    answers = json.decodeFromString<Map<String, JsonElement>>(answersJson),
    eventAt = eventAt,
)

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
