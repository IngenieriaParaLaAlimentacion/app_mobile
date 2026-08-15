package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_form

import ar.com.ingenieriaalimentacion.domain.model.survey.ResponseTargetRef
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyQuestion
import ar.com.ingenieriaalimentacion.presentation.feature.survey.common.SurveyAnswerFieldState
import ar.com.ingenieriaalimentacion.presentation.feature.survey.common.isFieldValid

data class SurveyResponseFormState(
    val destination: SurveyResponseFormDestination? = null,
    val surveyId: String = "",
    val surveyName: String = "",
    val questions: List<SurveyQuestion> = emptyList(),
    val answers: Map<String, SurveyAnswerFieldState> = emptyMap(),
    /** Resolved once the survey detail loads: the route's ids interpreted against the survey's
     *  `response_target`. Null while loading, or when a required target id never arrived. */
    val target: ResponseTargetRef? = null,
    val targetError: String? = null,
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val successMessage: String? = null,
) {
    /** Header label for the chosen target — read-only; the pick happened before this screen. */
    val targetLabel: String?
        get() = when (val t = target) {
            is ResponseTargetRef.Subject -> t.label
            is ResponseTargetRef.Establishment -> t.label
            ResponseTargetRef.Anonymous, null -> null
        }

    val targetHeading: String
        get() = when (target) {
            is ResponseTargetRef.Subject -> "SUJETO"
            is ResponseTargetRef.Establishment -> "ESTABLECIMIENTO"
            ResponseTargetRef.Anonymous, null -> "RESPUESTA"
        }

    val canSubmit: Boolean
        get() = !isLoading &&
            !loadError &&
            !isSubmitting &&
            target != null &&
            questions.all { question -> question.isFieldValid(answers[question.id] ?: return@all false) }
}
