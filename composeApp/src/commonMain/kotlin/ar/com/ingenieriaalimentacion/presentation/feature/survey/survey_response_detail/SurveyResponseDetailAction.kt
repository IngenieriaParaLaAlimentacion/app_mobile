package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_detail

sealed interface SurveyResponseDetailAction {
    data object EditClick : SurveyResponseDetailAction
    data object RetryLoad : SurveyResponseDetailAction
    data object BackClick : SurveyResponseDetailAction
}
