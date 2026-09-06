package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_edit

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyQuestion
import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.presentation.feature.survey.common.SurveyAnswerFieldState
import ar.com.ingenieriaalimentacion.presentation.feature.survey.common.answerSnapshot
import ar.com.ingenieriaalimentacion.presentation.feature.survey.common.isFieldValid

data class SurveyResponseEditState(
    val destination: SurveyResponseEditDestination? = null,
    val responseId: String = "",
    val surveyId: String = "",
    val surveyName: String = "",
    val targetLabel: String = "",
    val questions: List<SurveyQuestion> = emptyList(),
    val answers: Map<String, SurveyAnswerFieldState> = emptyMap(),
    /** Snapshot of [answers] as originally loaded — lets [canSubmit] require a real edit
     *  instead of allowing a no-op re-save of the same values. */
    val originalAnswers: Map<String, SurveyAnswerFieldState> = emptyMap(),
    val responseNumber: TextFieldState = TextFieldState(),
    val originalResponseNumber: String = "",
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
) {
    private val hasChanges: Boolean
        get() = responseNumber.value != originalResponseNumber || questions.any { question ->
            answers[question.id]?.answerSnapshot() != originalAnswers[question.id]?.answerSnapshot()
        }

    val canSubmit: Boolean
        get() = !isLoading &&
            !loadError &&
            !isSubmitting &&
            hasChanges &&
            responseNumber.value.isNotBlank() && responseNumber.isValid &&
            questions.all { question -> question.isFieldValid(answers[question.id] ?: return@all false) }
}
