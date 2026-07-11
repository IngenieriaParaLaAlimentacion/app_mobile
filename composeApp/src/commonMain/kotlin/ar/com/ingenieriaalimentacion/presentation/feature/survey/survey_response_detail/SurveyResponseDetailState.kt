package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_detail

data class AnsweredQuestionUi(
    val questionText: String,
    val displayValue: String,
)

data class SurveyResponseDetailState(
    val destination: SurveyResponseDetailDestination? = null,
    val responseId: String = "",
    val surveyId: String = "",
    val surveyName: String = "",
    val establishmentLabel: String = "",
    val eventAtLabel: String = "",
    val answeredQuestions: List<AnsweredQuestionUi> = emptyList(),
    val canEdit: Boolean = false,
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
)
