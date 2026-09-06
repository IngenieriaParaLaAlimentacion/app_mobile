package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.core.validation.Rules
import ar.com.ingenieriaalimentacion.core.validation.Validator
import ar.com.ingenieriaalimentacion.core.validation.onChange
import ar.com.ingenieriaalimentacion.core.validation.onFocus
import ar.com.ingenieriaalimentacion.core.validation.showError
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.survey.NewSurveyResponse
import ar.com.ingenieriaalimentacion.domain.model.survey.ResponseTargetRef
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyDetail
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseTarget
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.GetSurveyDetailUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.SubmitSurveyResponseUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.RESPONSE_NUMBER_RANGE
import ar.com.ingenieriaalimentacion.presentation.feature.survey.common.buildAnswers
import ar.com.ingenieriaalimentacion.presentation.feature.survey.common.forceShowAllErrors
import ar.com.ingenieriaalimentacion.presentation.feature.survey.common.initialFieldState
import ar.com.ingenieriaalimentacion.presentation.feature.survey.common.isFieldValid
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * The target is chosen *before* this screen (survey list → picker → here) and arrives as route
 * arguments; this ViewModel only interprets them against the survey's own `response_target`.
 */
class SurveyResponseFormViewModel(
    private val route: Route.SurveyResponseForm,
    private val getSurveyDetail: GetSurveyDetailUseCase,
    private val getMyEstablishments: GetMyEstablishmentsUseCase,
    private val getCurrentEstablishment: GetCurrentEstablishmentUseCase,
    private val submitSurveyResponse: SubmitSurveyResponseUseCase,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(SurveyResponseFormState(surveyId = route.surveyId))
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    init {
        load()
    }

    fun onAction(action: SurveyResponseFormAction) {
        when (action) {
            is SurveyResponseFormAction.AnswerChanged ->
                _state.update {
                    it.copy(answers = it.answers + (action.questionId to action.state), successMessage = null)
                }
            is SurveyResponseFormAction.ResponseNumberChanged -> _state.update {
                it.copy(responseNumber = it.responseNumber.onChange(action.value.filter(Char::isDigit), responseNumberValidator))
            }
            is SurveyResponseFormAction.ResponseNumberFocusChanged -> _state.update {
                it.copy(responseNumber = it.responseNumber.onFocus(action.focused, responseNumberValidator))
            }

            SurveyResponseFormAction.Submit -> submit()
            SurveyResponseFormAction.DismissSuccess -> _state.update { it.copy(successMessage = null) }
            SurveyResponseFormAction.RetryLoad -> load()
            SurveyResponseFormAction.BackClick ->
                _state.update { it.copy(destination = SurveyResponseFormDestination.Back) }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, loadError = false) }
        viewModelScope.launch {
            val detail = getSurveyDetail(_state.value.surveyId).getOrNull()
            if (detail == null) {
                _state.update { it.copy(isLoading = false, loadError = true) }
                return@launch
            }

            val target = resolveTarget(detail)
            _state.update {
                it.copy(
                    isLoading = false,
                    loadError = false,
                    surveyName = detail.name,
                    questions = detail.questions,
                    answers = detail.questions.associate { q -> q.id to q.initialFieldState() },
                    target = target,
                    targetError = null,
                )
            }
        }
    }

    /**
     * Route arguments win. For an establishment survey entered without one, the Home default
     * pre-fills it — a convenience, not a gate: the picker on the list still offers every
     * establishment, including ones this user has no approved access to.
     */
    private suspend fun resolveTarget(detail: SurveyDetail): ResponseTargetRef? =
        when (detail.responseTarget) {
            SurveyResponseTarget.Anonymous -> ResponseTargetRef.Anonymous

            SurveyResponseTarget.Subject -> route.targetId?.let {
                ResponseTargetRef.Subject(it, route.targetLabel.orEmpty())
            }

            SurveyResponseTarget.Establishment -> route.targetId
                ?.let { ResponseTargetRef.Establishment(it, route.targetLabel.orEmpty()) }
                ?: defaultEstablishmentTarget()
        }

    private suspend fun defaultEstablishmentTarget(): ResponseTargetRef.Establishment? {
        val approved = getMyEstablishments().getOrNull()
            ?.filter { it.status == EstablishmentStatus.Approved }
            ?: return null
        val current = getCurrentEstablishment(approved) ?: approved.singleOrNull() ?: return null
        return ResponseTargetRef.Establishment(current.id, current.name)
    }

    private fun submit() {
        val current = _state.value
        val validated = current.copy(
            answers = forceShowAllErrors(current.questions, current.answers),
            responseNumber = current.responseNumber.showError(responseNumberValidator),
            targetError = if (current.target == null) "Elegí para quién es esta respuesta." else null,
        )

        val newResponse = buildNewResponse(validated)
        if (newResponse == null) {
            _state.update { validated }
            return
        }

        _state.update { validated.copy(isSubmitting = true, submitError = null, successMessage = null) }
        viewModelScope.launch {
            submitSurveyResponse(newResponse).fold(
                onSuccess = {
                    syncCoordinator.requestSync() // fire-and-forget; UI does not wait on the result
                    _state.update {
                        if (it.target is ResponseTargetRef.Anonymous) {
                            // Anonymous surveys have no uniqueness rule, so the loop-through-
                            // interviews flow still holds: clear the answers and stay put.
                            it.copy(
                                answers = it.questions.associate { q -> q.id to q.initialFieldState() },
                                responseNumber = it.responseNumber.onChange(
                                    ((it.responseNumber.value.toIntOrNull() ?: 0) + 1).toString(), responseNumberValidator,
                                ),
                                isSubmitting = false,
                                submitError = null,
                                successMessage = "Respuesta guardada",
                            )
                        } else {
                            // This subject/establishment can't answer this survey again, so
                            // staying here would only offer an action that is now guaranteed to
                            // conflict. Back to the list.
                            it.copy(
                                isSubmitting = false,
                                submitError = null,
                                destination = SurveyResponseFormDestination.Done,
                            )
                        }
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(isSubmitting = false, submitError = "No se pudo guardar. Intentá nuevamente.")
                    }
                },
            )
        }
    }

    /** Returns the [NewSurveyResponse] only when every required question is valid and a target was
     *  resolved; otherwise `null`. */
    private fun buildNewResponse(state: SurveyResponseFormState): NewSurveyResponse? {
        val target = state.target ?: return null
        val responseNumber = state.responseNumber.value.toIntOrNull()?.takeIf { it in RESPONSE_NUMBER_RANGE } ?: return null
        val allValid = state.questions.all { q -> q.isFieldValid(state.answers[q.id] ?: return null) }
        if (!allValid) return null
        return NewSurveyResponse(
            surveyId = state.surveyId,
            surveyName = state.surveyName,
            target = target,
            responseNumber = responseNumber,
            answers = buildAnswers(state.questions, state.answers),
            eventAtMs = Clock.System.now().toEpochMilliseconds(),
        )
    }

    private val responseNumberValidator: Validator = Rules.of(
        Rules.required("Ingresá el número de respuesta."),
        Rules.digitsInRange(RESPONSE_NUMBER_RANGE, "Ingresá un número entre 1 y 999999."),
    )
}
