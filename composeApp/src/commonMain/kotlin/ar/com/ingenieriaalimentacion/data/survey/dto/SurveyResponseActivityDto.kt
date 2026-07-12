package ar.com.ingenieriaalimentacion.data.survey.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Deliberately independent of [ar.com.ingenieriaalimentacion.data.activity.dto.ActivityResponse] —
 * that shape is hardcoded to subject/measurement and would silently drop a `survey_response` payload
 * (the shared `Json` sets `ignoreUnknownKeys = true`). This is its own parsing path for
 * `type=survey_response` reads against the same `/me/activity` URL.
 */
@Serializable
data class SurveyResponseActivitySurveyResponse(val id: String, val name: String)

@Serializable
data class SurveyResponseActivityEstablishmentResponse(
    val id: String,
    val name: String,
    val city: String,
)

@Serializable
data class SurveyResponseActivitySummaryResponse(
    val id: String,
    @SerialName("event_at") val eventAt: String,
    val survey: SurveyResponseActivitySurveyResponse,
    val establishment: SurveyResponseActivityEstablishmentResponse,
)

@Serializable
data class SurveyResponseActivityItemResponse(
    val type: String,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("survey_response") val surveyResponse: SurveyResponseActivitySummaryResponse? = null,
)

@Serializable
data class PaginatedSurveyResponseActivityResponse(
    val items: List<SurveyResponseActivityItemResponse>,
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    @SerialName("total_pages") val totalPages: Int,
)
