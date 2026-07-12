package ar.com.ingenieriaalimentacion.data.survey.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SurveyResponseRequest(
    val id: String,
    @SerialName("survey_id") val surveyId: String,
    @SerialName("establishment_id") val establishmentId: String,
    val answers: Map<String, JsonElement>,
    @SerialName("event_at") val eventAt: String,
)

@Serializable
data class SurveyResponseEditRequest(
    val id: String,
    val answers: Map<String, JsonElement>,
    @SerialName("event_at") val eventAt: String,
)

@Serializable
data class SurveyResponseResponse(
    val id: String,
    @SerialName("survey_id") val surveyId: String,
    @SerialName("establishment_id") val establishmentId: String,
    @SerialName("submitted_by_id") val submittedById: String,
    val answers: Map<String, JsonElement>,
    @SerialName("event_at") val eventAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)
