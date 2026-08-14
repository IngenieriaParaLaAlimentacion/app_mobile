package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_form

import ar.com.ingenieriaalimentacion.presentation.feature.survey.common.SurveyAnswerFieldState

sealed interface SurveyResponseFormAction {
    data class AnswerChanged(val questionId: String, val state: SurveyAnswerFieldState) : SurveyResponseFormAction
    data object Submit : SurveyResponseFormAction
    data object DismissSuccess : SurveyResponseFormAction
    data object RetryLoad : SurveyResponseFormAction
    data object BackClick : SurveyResponseFormAction
}
