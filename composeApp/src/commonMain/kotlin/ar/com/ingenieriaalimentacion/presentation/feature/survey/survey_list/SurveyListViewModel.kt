package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseTarget
import ar.com.ingenieriaalimentacion.domain.usecase.survey.GetAvailableSurveysUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SurveyListViewModel(
    private val getAvailableSurveys: GetAvailableSurveysUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SurveyListState())
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    init {
        load(page = 1)
    }

    fun onAction(action: SurveyListAction) {
        when (action) {
            // Route by the survey's own target: subject picker, establishment picker, or straight
            // into the questionnaire for an anonymous survey.
            is SurveyListAction.SurveyClick -> _state.update {
                when (action.survey.responseTarget) {
                    SurveyResponseTarget.Subject -> it.copy(
                        pendingSurveyId = action.survey.id,
                        destination = SurveyListDestination.SelectSubject,
                    )
                    SurveyResponseTarget.Establishment -> it.copy(
                        pendingSurveyId = action.survey.id,
                        destination = SurveyListDestination.SelectEstablishment,
                    )
                    SurveyResponseTarget.Anonymous -> it.copy(
                        pendingSurveyId = null,
                        destination = SurveyListDestination.SurveyResponseForm(action.survey.id),
                    )
                }
            }

            is SurveyListAction.SubjectPicked -> openForm(
                targetId = action.subject.id,
                targetLabel = "${action.subject.name} ${action.subject.lastName}",
            )

            is SurveyListAction.EstablishmentPicked -> openForm(
                targetId = action.establishment.id,
                targetLabel = action.establishment.name,
            )

            SurveyListAction.LoadMore -> if (_state.value.canLoadMore) load(page = _state.value.page + 1)
            SurveyListAction.RetryClick -> load(page = 1)
            SurveyListAction.BackClick -> _state.update { it.copy(destination = SurveyListDestination.Back) }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    /** A picker result with no pending survey is a stale [savedStateHandle] value (e.g. left over
     *  from a different consumer) — ignore it rather than opening an arbitrary form. */
    private fun openForm(targetId: String, targetLabel: String) {
        _state.update { current ->
            val surveyId = current.pendingSurveyId ?: return@update current
            current.copy(
                pendingSurveyId = null,
                destination = SurveyListDestination.SurveyResponseForm(surveyId, targetId, targetLabel),
            )
        }
    }

    private fun load(page: Int) {
        _state.update {
            if (page == 1) it.copy(isLoading = true, loadError = false) else it.copy(isLoadingMore = true)
        }
        viewModelScope.launch {
            getAvailableSurveys(page = page, pageSize = PAGE_SIZE).fold(
                onSuccess = { paged ->
                    _state.update {
                        it.copy(
                            surveys = if (page == 1) paged.items else it.surveys + paged.items,
                            page = paged.page,
                            totalPages = paged.totalPages,
                            isLoading = false,
                            isLoadingMore = false,
                            loadError = false,
                            fromCache = paged.fromCache,
                        )
                    }
                },
                onFailure = { t ->
                    if (t is CancellationException) throw t
                    _state.update {
                        it.copy(isLoading = false, isLoadingMore = false, loadError = page == 1)
                    }
                },
            )
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
