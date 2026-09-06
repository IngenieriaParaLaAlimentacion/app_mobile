package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_number_fix

import ar.com.ingenieriaalimentacion.core.validation.TextFieldState

data class SurveyResponseNumberFixState(
    val surveyName: String = "",
    val number: TextFieldState = TextFieldState(),
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val destination: SurveyResponseNumberFixDestination? = null,
) { val canSubmit get() = !isSubmitting && number.value.isNotBlank() && number.isValid }
