package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_list

sealed interface SurveyListAction {
    data class SurveyClick(val surveyId: String) : SurveyListAction
    data object LoadMore : SurveyListAction
    data object RetryClick : SurveyListAction
    data object BackClick : SurveyListAction
}
