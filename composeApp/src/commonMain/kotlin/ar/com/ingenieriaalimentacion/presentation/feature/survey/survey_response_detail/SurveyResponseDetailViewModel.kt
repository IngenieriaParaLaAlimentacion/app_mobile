package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.domain.model.auth.canRespondToSurveys
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyProfileUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.GetSurveyDetailUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.GetSurveyResponseDetailUseCase
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
    private val getMyEstablishments: GetMyEstablishmentsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SurveyResponseDetailState(responseId = route.responseId))
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    init {
        load()
    }

    fun onAction(action: SurveyResponseDetailAction) {
        when (action) {
            SurveyResponseDetailAction.EditClick ->
                _state.update {
                    it.copy(destination = SurveyResponseDetailDestination.SurveyResponseEdit(it.responseId))
                }
            SurveyResponseDetailAction.RetryLoad -> load()
            SurveyResponseDetailAction.BackClick ->
                _state.update { it.copy(destination = SurveyResponseDetailDestination.Back) }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    /** Called after a successful edit (navigate-back-with-result) so the screen shows the
     *  edit's effect — noting the re-fetched state may not equal what was just sent if a
     *  later-`event_at` edit from elsewhere already won (last-write-wins, no conflict UI). */
    fun refresh() = load()

    private fun load() {
        _state.update { it.copy(isLoading = true, loadError = false) }
        viewModelScope.launch {
            val detail = getSurveyResponseDetail(_state.value.responseId).getOrNull()
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
            val establishments = getMyEstablishments().getOrNull().orEmpty()
            val canEdit = profile != null && canRespondToSurveys(profile.role) &&
                (detail.submittedById == null || detail.submittedById == profile.id || profile.role in ADMIN_ROLES)

            _state.update {
                it.copy(
                    isLoading = false,
                    loadError = false,
                    surveyId = detail.surveyId,
                    surveyName = surveyDetail?.name ?: it.surveyName,
                    establishmentLabel = establishments.firstOrNull { e -> e.id == detail.establishmentId }?.name
                        ?: detail.establishmentId,
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
