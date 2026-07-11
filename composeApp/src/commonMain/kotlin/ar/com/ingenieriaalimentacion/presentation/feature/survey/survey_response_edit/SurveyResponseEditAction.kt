package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_edit

import ar.com.ingenieriaalimentacion.presentation.feature.survey.common.SurveyAnswerFieldState

sealed interface SurveyResponseEditAction {
    data class AnswerChanged(val questionId: String, val state: SurveyAnswerFieldState) : SurveyResponseEditAction
    data object Submit : SurveyResponseEditAction
    data object RetryLoad : SurveyResponseEditAction
    data object BackClick : SurveyResponseEditAction
}
