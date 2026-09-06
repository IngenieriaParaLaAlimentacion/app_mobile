package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_history

sealed interface SurveyResponseHistoryAction {
    data class ResponseClick(val surveyId: String, val responseId: String) : SurveyResponseHistoryAction
    data class RetryUploadClick(val responseId: String) : SurveyResponseHistoryAction

    /** Throws the queued row away. Destructive — the screen confirms before dispatching this. */
    data class DiscardClick(val responseId: String) : SurveyResponseHistoryAction
    data object LoadMore : SurveyResponseHistoryAction
    data object RetryLoad : SurveyResponseHistoryAction
}
