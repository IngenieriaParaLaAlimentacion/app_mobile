package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_edit

sealed class SurveyResponseEditDestination {
    data object Back : SurveyResponseEditDestination()
    data object Saved : SurveyResponseEditDestination()
}
