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
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseTarget
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseType
import ar.com.ingenieriaalimentacion.domain.repository.PagedSurveys

/** Null when this build doesn't recognize the survey's target — the row is then dropped from the
 *  list rather than taking the whole page down. See [toDomainResponseTargetOrNull]. */
internal fun SurveyListItemResponse.toDomainOrNull(): Survey? = Survey(
    id = id,
    name = name,
    responseTarget = responseTarget.toDomainResponseTargetOrNull() ?: return null,
    isActive = isActive,
    questionCount = questionCount,
)

internal fun PaginatedSurveysResponse.toDomain(): PagedSurveys = PagedSurveys(
    items = items.mapNotNull { it.toDomainOrNull() },
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

/** Unlike the list mapper this one still throws on an unknown target: there is nothing sensible to
 *  render for a survey this build cannot decide a target for, so it surfaces as a load failure. */
internal fun SurveyDetailResponse.toDomain(): SurveyDetail = SurveyDetail(
    id = id,
    name = name,
    responseTarget = responseTarget.toDomainResponseTargetOrNull()
        ?: error("Unknown survey response_target: $responseTarget"),
    isActive = isActive,
    questions = questions.map { it.toDomain() },
)

internal fun SurveyResponseResponse.toDomain(): SurveyResponseResponseDomain = SurveyResponseResponseDomain(
    id = id,
    surveyId = surveyId,
    subjectId = subjectId,
    establishmentId = establishmentId,
    submittedById = submittedById,
    eventAt = eventAt,
)

/** Thin projection of [SurveyResponseResponse] the sync engines/history repo need — `answers` are
 *  interpreted separately via [toAnswerValues], keyed against a specific survey's questions.
 *  Both target ids are nullable: an anonymous response carries neither. */
data class SurveyResponseResponseDomain(
    val id: String,
    val surveyId: String,
    val subjectId: String?,
    val establishmentId: String?,
    val submittedById: String,
    val eventAt: String,
)

/** Lenient on purpose: a target value this build doesn't know is not fatal. Callers drop the row
 *  (list) or fail that one read (detail) — a fourth server-side target must never take down the
 *  whole survey list on an older app. */
internal fun String.toDomainResponseTargetOrNull(): SurveyResponseTarget? = when (this) {
    "subject" -> SurveyResponseTarget.Subject
    "establishment" -> SurveyResponseTarget.Establishment
    "anonymous" -> SurveyResponseTarget.Anonymous
    else -> null
}

private fun String.toDomainResponseType(): SurveyResponseType = when (this) {
    "single_choice" -> SurveyResponseType.SingleChoice
    "multiple_choice" -> SurveyResponseType.MultipleChoice
    "numeric" -> SurveyResponseType.Numeric
    "text" -> SurveyResponseType.Text
    "date" -> SurveyResponseType.Date
    else -> error("Unknown survey response_type: $this")
}
