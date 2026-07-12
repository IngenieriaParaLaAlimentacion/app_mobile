package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_edit

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyQuestion
import ar.com.ingenieriaalimentacion.presentation.feature.survey.common.SurveyAnswerFieldState

data class SurveyResponseEditState(
    val destination: SurveyResponseEditDestination? = null,
    val responseId: String = "",
    val surveyName: String = "",
    val establishmentName: String = "",
    val questions: List<SurveyQuestion> = emptyList(),
    val answers: Map<String, SurveyAnswerFieldState> = emptyMap(),
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
) {
    val canSubmit: Boolean
        get() = !isLoading && !loadError && !isSubmitting
}
