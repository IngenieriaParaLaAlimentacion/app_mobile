package ar.com.ingenieriaalimentacion.data.survey.mapper

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
