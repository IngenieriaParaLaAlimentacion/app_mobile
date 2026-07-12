package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_history

sealed interface SurveyResponseHistoryAction {
    data class ResponseClick(val responseId: String) : SurveyResponseHistoryAction
    data class RetryUploadClick(val responseId: String) : SurveyResponseHistoryAction
    data object LoadMore : SurveyResponseHistoryAction
    data object RetryLoad : SurveyResponseHistoryAction
    data object BackClick : SurveyResponseHistoryAction
}
