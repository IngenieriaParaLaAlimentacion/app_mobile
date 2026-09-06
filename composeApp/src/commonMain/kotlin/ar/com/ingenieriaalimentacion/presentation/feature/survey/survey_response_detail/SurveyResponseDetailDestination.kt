package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_detail

sealed class SurveyResponseDetailDestination {
    data object Back : SurveyResponseDetailDestination()
    data class SurveyResponseEdit(
        val surveyId: String,
        val responseId: String,
    ) : SurveyResponseDetailDestination()
}
