package ar.com.ingenieriaalimentacion.data.survey.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SurveyListItemResponse(
    val id: String,
    val name: String,
    @SerialName("response_target") val responseTarget: String,  // "subject"|"establishment"|"anonymous"
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("created_by_id") val createdById: String,
    @SerialName("question_count") val questionCount: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("requires_response_number") val requiresResponseNumber: Boolean? = null,
)

@Serializable
data class PaginatedSurveysResponse(
    val items: List<SurveyListItemResponse>,
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
data class SurveyOptionResponse(val id: String, val text: String)

@Serializable
data class SurveyQuestionResponse(
    val id: String,
    val text: String,
    @SerialName("response_type") val responseType: String,  // single_choice|multiple_choice|numeric|text|date
    @SerialName("is_required") val isRequired: Boolean,
    val options: List<SurveyOptionResponse> = emptyList(),
)

@Serializable
data class SurveyDetailResponse(
    val id: String,
    val name: String,
    @SerialName("response_target") val responseTarget: String,
    @SerialName("is_active") val isActive: Boolean,
    val questions: List<SurveyQuestionResponse>,
    @SerialName("requires_response_number") val requiresResponseNumber: Boolean? = null,
)
