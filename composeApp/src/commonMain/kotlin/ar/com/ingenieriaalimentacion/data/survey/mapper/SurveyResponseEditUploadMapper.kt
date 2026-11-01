package ar.com.ingenieriaalimentacion.data.survey.mapper

import ar.com.ingenieriaalimentacion.data.db.PendingSurveyResponseEdit
import ar.com.ingenieriaalimentacion.data.survey.dto.SurveyResponseEditRequest
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseEditUpload
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal fun PendingSurveyResponseEdit.toDomain(): SurveyResponseEditUpload = SurveyResponseEditUpload(
    editId = editId,
    responseId = responseId,
    surveyId = surveyId,
    surveyName = surveyName,
    establishmentName = establishmentName,
    answersJson = answersJson,
    eventAt = eventAt,
    syncState = syncState.toSurveySyncState(),
    errorMessage = errorMessage,
    attemptCount = attemptCount.toInt(),
    createdAt = createdAt,
    updatedAt = updatedAt,
    responseNumber = responseNumber?.toInt(),
)

/** Queued edit → PUT body. [SurveyResponseEditUpload.editId] is the edit's own idempotency `id` —
 *  a SEPARATE key from [SurveyResponseEditUpload.responseId], never reused. */
internal fun SurveyResponseEditUpload.toRequest(json: Json): SurveyResponseEditRequest = SurveyResponseEditRequest(
    id = editId,
    responseNumber = responseNumber,
    answers = json.decodeFromString<Map<String, JsonElement>>(answersJson),
    eventAt = eventAt,
)
