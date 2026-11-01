package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_number_fix

sealed interface SurveyResponseNumberFixAction {
    data class NumberChanged(val value: String) : SurveyResponseNumberFixAction
    data class NumberFocusChanged(val focused: Boolean) : SurveyResponseNumberFixAction
    data object Submit : SurveyResponseNumberFixAction
    data object Back : SurveyResponseNumberFixAction
}
