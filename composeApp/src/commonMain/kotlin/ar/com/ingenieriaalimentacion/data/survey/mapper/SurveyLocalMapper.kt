package ar.com.ingenieriaalimentacion.data.survey.mapper

import ar.com.ingenieriaalimentacion.data.db.CachedSurvey
import ar.com.ingenieriaalimentacion.domain.model.survey.Survey
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseTarget

internal fun SurveyResponseTarget.toDbString(): String = when (this) {
    SurveyResponseTarget.Subject -> "SUBJECT"
    SurveyResponseTarget.Establishment -> "ESTABLISHMENT"
    SurveyResponseTarget.Anonymous -> "ANONYMOUS"
}

internal fun String.toDomainSurveyResponseTarget(): SurveyResponseTarget = when (this) {
    "SUBJECT" -> SurveyResponseTarget.Subject
    "ESTABLISHMENT" -> SurveyResponseTarget.Establishment
    "ANONYMOUS" -> SurveyResponseTarget.Anonymous
    else -> error("Unknown survey responseTarget stored: $this")
}

internal fun CachedSurvey.toDomainSurvey(): Survey = Survey(
    id = id,
    name = name,
    responseTarget = responseTarget.toDomainSurveyResponseTarget(),
    isActive = isActive == 1L,
    questionCount = questionCount.toInt(),
)
