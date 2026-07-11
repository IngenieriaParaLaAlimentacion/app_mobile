package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.survey.NewSurveyResponse
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.GetSurveyDetailUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.SubmitSurveyResponseUseCase
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

class SurveyResponseFormViewModel(
    route: Route.SurveyResponseForm,
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

            SurveyResponseFormAction.EstablishmentPickerOpen ->
                _state.update { it.copy(showEstablishmentPicker = true) }

            SurveyResponseFormAction.EstablishmentPickerDismiss ->
                _state.update { it.copy(showEstablishmentPicker = false) }

            is SurveyResponseFormAction.EstablishmentSelected ->
                _state.update {
                    it.copy(
                        selectedEstablishment = action.establishment,
                        establishmentError = null,
                        showEstablishmentPicker = false,
                        successMessage = null,
                    )
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

            val approved = getMyEstablishments().getOrNull()
                ?.filter { it.status == EstablishmentStatus.Approved }
                ?: emptyList()
            val current = getCurrentEstablishment(approved)

            _state.update {
                it.copy(
                    isLoading = false,
                    loadError = false,
                    surveyName = detail.name,
                    questions = detail.questions,
                    answers = detail.questions.associate { q -> q.id to q.initialFieldState() },
                    establishmentOptions = approved,
                    selectedEstablishment = current ?: approved.singleOrNull() ?: it.selectedEstablishment,
                )
            }
        }
    }

    private fun submit() {
        val current = _state.value
        val validated = current.copy(
            answers = forceShowAllErrors(current.questions, current.answers),
            establishmentError = if (current.selectedEstablishment == null) "Elegí el establecimiento." else null,
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
                    _state.update {
                        // Clear answers for the next interview; keep the establishment selected.
                        it.copy(
                            answers = it.questions.associate { q -> q.id to q.initialFieldState() },
                            isSubmitting = false,
                            submitError = null,
                            successMessage = "Respuesta guardada",
                        )
                    }
                    syncCoordinator.requestSync() // fire-and-forget; UI does not wait on the result
                },
                onFailure = {
                    _state.update {
                        it.copy(isSubmitting = false, submitError = "No se pudo guardar. Intentá nuevamente.")
                    }
                },
            )
        }
    }

    /** Returns the [NewSurveyResponse] only when every required question and the establishment
     *  picker are valid; otherwise `null`. */
    private fun buildNewResponse(state: SurveyResponseFormState): NewSurveyResponse? {
        val establishment = state.selectedEstablishment ?: return null
        val allValid = state.questions.all { q -> q.isFieldValid(state.answers[q.id] ?: return null) }
        if (!allValid) return null
        return NewSurveyResponse(
            surveyId = state.surveyId,
            surveyName = state.surveyName,
            establishmentId = establishment.id,
            establishmentName = establishment.name,
            answers = buildAnswers(state.questions, state.answers),
            eventAtMs = Clock.System.now().toEpochMilliseconds(),
        )
    }
}
