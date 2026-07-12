package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_form

sealed class SurveyResponseFormDestination {
    data object Back : SurveyResponseFormDestination()
}
