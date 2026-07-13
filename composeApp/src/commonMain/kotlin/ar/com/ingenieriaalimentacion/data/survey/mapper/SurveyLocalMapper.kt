package ar.com.ingenieriaalimentacion.data.survey.mapper

import ar.com.ingenieriaalimentacion.data.db.CachedSurvey
import ar.com.ingenieriaalimentacion.domain.model.survey.Survey
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyScope

internal fun SurveyScope.toDbString(): String = when (this) {
    SurveyScope.Generic -> "GENERIC"
    SurveyScope.Targeted -> "TARGETED"
}

internal fun String.toDomainSurveyScope(): SurveyScope = when (this) {
    "GENERIC" -> SurveyScope.Generic
    "TARGETED" -> SurveyScope.Targeted
    else -> error("Unknown survey scope stored: $this")
}

internal fun CachedSurvey.toDomainSurvey(): Survey = Survey(
    id = id,
    name = name,
    scope = scope.toDomainSurveyScope(),
    isActive = isActive == 1L,
    questionCount = questionCount.toInt(),
)
