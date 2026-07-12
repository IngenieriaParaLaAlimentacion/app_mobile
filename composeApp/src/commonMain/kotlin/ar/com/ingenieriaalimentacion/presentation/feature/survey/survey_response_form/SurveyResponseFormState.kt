package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_form

import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyQuestion
import ar.com.ingenieriaalimentacion.presentation.feature.survey.common.SurveyAnswerFieldState

data class SurveyResponseFormState(
    val destination: SurveyResponseFormDestination? = null,
    val surveyId: String = "",
    val surveyName: String = "",
    val questions: List<SurveyQuestion> = emptyList(),
    val answers: Map<String, SurveyAnswerFieldState> = emptyMap(),
    val establishmentOptions: List<EstablishmentWithStatus> = emptyList(),
    val selectedEstablishment: EstablishmentWithStatus? = null,
    val establishmentError: String? = null,
    val showEstablishmentPicker: Boolean = false,
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val successMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = !isLoading && !loadError && selectedEstablishment != null && !isSubmitting
}
