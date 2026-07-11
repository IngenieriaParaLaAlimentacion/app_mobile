package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_history

sealed class SurveyResponseHistoryDestination {
    data object Back : SurveyResponseHistoryDestination()
    data class SurveyResponseDetail(val responseId: String) : SurveyResponseHistoryDestination()
}
