package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.core.validation.Rules
import ar.com.ingenieriaalimentacion.core.validation.Validator
import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.core.validation.onChange
import ar.com.ingenieriaalimentacion.core.validation.onFocus
import ar.com.ingenieriaalimentacion.core.validation.showError
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseTarget
import ar.com.ingenieriaalimentacion.domain.usecase.survey.RESPONSE_NUMBER_RANGE
import ar.com.ingenieriaalimentacion.domain.usecase.survey.EditSurveyResponseUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.GetSurveyDetailUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.GetSurveyResponseDetailUseCase
import ar.com.ingenieriaalimentacion.presentation.feature.survey.common.buildAnswers
import ar.com.ingenieriaalimentacion.presentation.feature.survey.common.forceShowAllErrors
import ar.com.ingenieriaalimentacion.presentation.feature.survey.common.isFieldValid
import ar.com.ingenieriaalimentacion.presentation.feature.survey.common.toFieldStates
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

class SurveyResponseEditViewModel(
    route: Route.SurveyResponseEdit,
    private val getSurveyResponseDetail: GetSurveyResponseDetailUseCase,
    private val getSurveyDetail: GetSurveyDetailUseCase,
    private val editSurveyResponse: EditSurveyResponseUseCase,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SurveyResponseEditState(surveyId = route.surveyId, responseId = route.responseId),
    )
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    init {
        load()
    }

    fun onAction(action: SurveyResponseEditAction) {
        when (action) {
            is SurveyResponseEditAction.AnswerChanged ->
                _state.update { it.copy(answers = it.answers + (action.questionId to action.state)) }

            SurveyResponseEditAction.Submit -> submit()
            SurveyResponseEditAction.RetryLoad -> load()
            SurveyResponseEditAction.BackClick ->
                _state.update { it.copy(destination = SurveyResponseEditDestination.Back) }
            is SurveyResponseEditAction.ResponseNumberChanged -> _state.update {
                it.copy(
                    responseNumber = it.responseNumber.onChange(
                        action.value.filter(Char::isDigit),
                        responseNumberValidator(it.requiresResponseNumber == true),
                    ),
                )
            }
            is SurveyResponseEditAction.ResponseNumberFocusChanged -> _state.update {
                it.copy(
                    responseNumber = it.responseNumber.onFocus(
                        action.focused,
                        responseNumberValidator(it.requiresResponseNumber == true),
                    ),
                )
            }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, loadError = false) }
        viewModelScope.launch {
            val detail = getSurveyResponseDetail(_state.value.surveyId, _state.value.responseId).getOrNull()
            val surveyDetail = detail?.let { getSurveyDetail(it.surveyId).getOrNull() }
            if (detail == null || surveyDetail == null) {
                _state.update { it.copy(isLoading = false, loadError = true) }
                return@launch
            }

            // Any establishment is answerable now (CA4), so a getMyEstablishments lookup would
            // miss by construction. Use the target label the queue row carried, falling back to
            // the raw id and to "Anónima" for a target-less response.
            val targetLabel = when (detail.responseTarget) {
                SurveyResponseTarget.Anonymous -> "Anónima"
                else -> detail.targetLabel ?: detail.targetId.orEmpty()
            }

            val fieldStates = surveyDetail.questions.toFieldStates(detail.answers)
            _state.update {
                it.copy(
                    isLoading = false,
                    loadError = false,
                    surveyName = surveyDetail.name,
                    targetLabel = targetLabel,
                    questions = surveyDetail.questions,
                    answers = fieldStates,
                    originalAnswers = fieldStates,
                    responseNumber = TextFieldState(detail.responseNumber?.toString().orEmpty()),
                    originalResponseNumber = detail.responseNumber?.toString().orEmpty(),
                    requiresResponseNumber = surveyDetail.requiresResponseNumber,
                )
            }
        }
    }

    private fun submit() {
        val current = _state.value
        val validated = current.copy(
            answers = forceShowAllErrors(current.questions, current.answers),
            responseNumber = current.responseNumber.showError(
                responseNumberValidator(current.requiresResponseNumber == true),
            ),
        )

        val allValid = validated.questions.all { q -> q.isFieldValid(validated.answers[q.id] ?: return@all false) }
        val responseNumber = validated.responseNumber.value.takeIf(String::isNotBlank)?.toIntOrNull()
        if (!allValid || !validated.responseNumber.isValid ||
            (validated.requiresResponseNumber == true && responseNumber == null) ||
            (responseNumber != null && responseNumber !in RESPONSE_NUMBER_RANGE)
        ) {
            _state.update { validated }
            return
        }

        val answers = buildAnswers(validated.questions, validated.answers)

        _state.update { validated.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            editSurveyResponse(
                responseId = validated.responseId,
                surveyId = validated.surveyId,
                surveyName = validated.surveyName,
                establishmentName = validated.targetLabel,
                responseNumber = responseNumber,
                answers = answers,
                // A new, client-clock event_at for this edit attempt — distinct from the
                // original response's event_at.
                eventAtMs = Clock.System.now().toEpochMilliseconds(),
            ).fold(
                onSuccess = {
                    syncCoordinator.requestSync() // fire-and-forget; UI does not wait on the result
                    _state.update { it.copy(isSubmitting = false, destination = SurveyResponseEditDestination.Saved) }
                },
                onFailure = {
                    _state.update {
                        it.copy(isSubmitting = false, submitError = "No se pudo guardar. Intentá nuevamente.")
                    }
                },
            )
        }
    }

    private fun responseNumberValidator(required: Boolean): Validator = Rules.of(
        *(if (required) arrayOf(Rules.required("Ingresá el número de respuesta.")) else emptyArray()),
        Rules.digitsInRange(RESPONSE_NUMBER_RANGE, "Ingresá un número entre 1 y 999999."),
    )
}
