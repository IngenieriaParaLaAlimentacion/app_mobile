package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_list

sealed class SurveyListDestination {
    data object Back : SurveyListDestination()
    data class SurveyResponseForm(val surveyId: String) : SurveyListDestination()
}
