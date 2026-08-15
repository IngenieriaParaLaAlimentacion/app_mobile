package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.domain.model.auth.canRespondToSurveys
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseTarget
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyProfileUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.GetSurveyDetailUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.GetSurveyResponseDetailUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.ObserveSurveyResponseEditStatusUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.RetrySurveyResponseEditUploadUseCase
import ar.com.ingenieriaalimentacion.presentation.feature.survey.common.formatAnswerForDisplay
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

class SurveyResponseDetailViewModel(
    route: Route.SurveyResponseDetail,
    private val getSurveyResponseDetail: GetSurveyResponseDetailUseCase,
    private val getSurveyDetail: GetSurveyDetailUseCase,
    private val getMyProfile: GetMyProfileUseCase,
    observeEditStatus: ObserveSurveyResponseEditStatusUseCase,
    private val retryEditUpload: RetrySurveyResponseEditUploadUseCase,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SurveyResponseDetailState(surveyId = route.surveyId, responseId = route.responseId),
    )
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    init {
        load()
        viewModelScope.launch {
            observeEditStatus(route.responseId).collect { status ->
                _state.update {
                    it.copy(
                        editId = status?.editId,
                        editSyncState = status?.syncState,
                        editErrorMessage = status?.errorMessage,
                    )
                }
            }
        }
    }

    fun onAction(action: SurveyResponseDetailAction) {
        when (action) {
            SurveyResponseDetailAction.EditClick ->
                _state.update {
                    it.copy(
                        destination = SurveyResponseDetailDestination.SurveyResponseEdit(
                            surveyId = it.surveyId,
                            responseId = it.responseId,
                        ),
                    )
                }
            SurveyResponseDetailAction.RetryLoad -> load()
            SurveyResponseDetailAction.BackClick ->
                _state.update { it.copy(destination = SurveyResponseDetailDestination.Back) }
            SurveyResponseDetailAction.DismissSuccess -> _state.update { it.copy(successMessage = null) }
            SurveyResponseDetailAction.RetryEditClick -> {
                val editId = _state.value.editId ?: return
                viewModelScope.launch { retryEditUpload(editId); syncCoordinator.requestSync() }
            }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    /** Called after a successful edit (navigate-back-with-result) so the screen shows the
     *  edit's effect — noting the re-fetched state may not equal what was just sent if a
     *  later-`event_at` edit from elsewhere already won (last-write-wins, no conflict UI). */
    fun refresh() {
        _state.update { it.copy(successMessage = "Respuesta actualizada") }
        load()
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, loadError = false) }
        viewModelScope.launch {
            val detail = getSurveyResponseDetail(_state.value.surveyId, _state.value.responseId).getOrNull()
            if (detail == null) {
                _state.update { it.copy(isLoading = false, loadError = true) }
                return@launch
            }

            val surveyDetail = getSurveyDetail(detail.surveyId).getOrNull()
            val questions = surveyDetail?.questions.orEmpty()
            val answered = questions.mapNotNull { question ->
                detail.answers[question.id]?.let { value ->
                    AnsweredQuestionUi(
                        questionText = question.text,
                        displayValue = formatAnswerForDisplay(value, question),
                    )
                }
            }

            val profile = getMyProfile().getOrNull()
            val canEdit = profile != null && canRespondToSurveys(profile.role) &&
                (detail.submittedById == null || detail.submittedById == profile.id || profile.role in ADMIN_ROLES)

            _state.update {
                it.copy(
                    isLoading = false,
                    loadError = false,
                    surveyId = detail.surveyId,
                    surveyName = surveyDetail?.name ?: it.surveyName,
                    // No getMyEstablishments lookup any more: any establishment is answerable now
                    // (CA4), so that lookup would miss by construction. The label comes from the
                    // queue row when this device submitted it, and degrades to the raw id.
                    targetLabel = when (detail.responseTarget) {
                        SurveyResponseTarget.Anonymous -> "Anónima"
                        else -> detail.targetLabel ?: detail.targetId.orEmpty()
                    },
                    eventAtLabel = formatEventAt(detail.eventAtMs),
                    answeredQuestions = answered,
                    canEdit = canEdit,
                )
            }
        }
    }

    private fun formatEventAt(epochMs: Long): String = runCatching {
        val ldt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault())
        val dd = ldt.dayOfMonth.toString().padStart(2, '0')
        val mm = ldt.monthNumber.toString().padStart(2, '0')
        val hh = ldt.hour.toString().padStart(2, '0')
        val min = ldt.minute.toString().padStart(2, '0')
        "$dd/$mm/${ldt.year} $hh:$min"
    }.getOrDefault("")

    private companion object {
        val ADMIN_ROLES = setOf("admin", "superadmin")
    }
}
