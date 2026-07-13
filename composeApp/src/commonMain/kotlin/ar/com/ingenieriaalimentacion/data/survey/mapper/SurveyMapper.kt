package ar.com.ingenieriaalimentacion.data.survey.mapper

import ar.com.ingenieriaalimentacion.data.survey.dto.PaginatedSurveysResponse
import ar.com.ingenieriaalimentacion.data.survey.dto.SurveyDetailResponse
import ar.com.ingenieriaalimentacion.data.survey.dto.SurveyListItemResponse
import ar.com.ingenieriaalimentacion.data.survey.dto.SurveyOptionResponse
import ar.com.ingenieriaalimentacion.data.survey.dto.SurveyQuestionResponse
import ar.com.ingenieriaalimentacion.data.survey.dto.SurveyResponseResponse
import ar.com.ingenieriaalimentacion.domain.model.survey.Survey
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyDetail
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyOption
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyQuestion
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseType
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyScope
import ar.com.ingenieriaalimentacion.domain.repository.PagedSurveys

internal fun SurveyListItemResponse.toDomain(): Survey = Survey(
    id = id,
    name = name,
    scope = scope.toDomainScope(),
    isActive = isActive,
    questionCount = questionCount,
)

internal fun PaginatedSurveysResponse.toDomain(): PagedSurveys = PagedSurveys(
    items = items.map { it.toDomain() },
    page = page,
    totalPages = totalPages,
)

internal fun SurveyOptionResponse.toDomain(): SurveyOption = SurveyOption(id = id, text = text)

internal fun SurveyQuestionResponse.toDomain(): SurveyQuestion = SurveyQuestion(
    id = id,
    text = text,
    responseType = responseType.toDomainResponseType(),
    isRequired = isRequired,
    options = options.map { it.toDomain() },
)

internal fun SurveyDetailResponse.toDomain(): SurveyDetail = SurveyDetail(
    id = id,
    name = name,
    scope = scope.toDomainScope(),
    isActive = isActive,
    questions = questions.map { it.toDomain() },
)

internal fun SurveyResponseResponse.toDomain(): SurveyResponseResponseDomain = SurveyResponseResponseDomain(
    id = id,
    surveyId = surveyId,
    establishmentId = establishmentId,
    submittedById = submittedById,
    eventAt = eventAt,
)

/** Thin projection of [SurveyResponseResponse] the sync engines/history repo need — `answers` are
 *  interpreted separately via [toAnswerValues], keyed against a specific survey's questions. */
data class SurveyResponseResponseDomain(
    val id: String,
    val surveyId: String,
    val establishmentId: String,
    val submittedById: String,
    val eventAt: String,
)

private fun String.toDomainScope(): SurveyScope = when (this) {
    "generic" -> SurveyScope.Generic
    "targeted" -> SurveyScope.Targeted
    else -> error("Unknown survey scope: $this")
}

private fun String.toDomainResponseType(): SurveyResponseType = when (this) {
    "single_choice" -> SurveyResponseType.SingleChoice
    "multiple_choice" -> SurveyResponseType.MultipleChoice
    "numeric" -> SurveyResponseType.Numeric
    "text" -> SurveyResponseType.Text
    "date" -> SurveyResponseType.Date
    else -> error("Unknown survey response_type: $this")
}
