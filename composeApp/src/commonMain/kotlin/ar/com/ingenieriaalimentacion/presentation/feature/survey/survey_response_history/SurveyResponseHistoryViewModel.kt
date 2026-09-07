package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.domain.usecase.survey.DiscardSurveyResponseUploadUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.GetSurveyResponseHistoryPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.ObserveSurveyResponseUploadsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.RetrySurveyResponseUploadUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reactive floor (mirrors [ar.com.ingenieriaalimentacion.presentation.feature.measurement.uploads.MeasurementUploadsViewModel]):
 * the local queue + persistent cache keep the screen populated offline; a page-1 network refresh
 * on open keeps it fresh. A failed network refresh is silent — the reactive collector already
 * covers the "never blank" contract.
 */
class SurveyResponseHistoryViewModel(
    private val observeUploads: ObserveSurveyResponseUploadsUseCase,
    private val getHistoryPage: GetSurveyResponseHistoryPageUseCase,
    private val retrySurveyResponseUpload: RetrySurveyResponseUploadUseCase,
    private val discardSurveyResponseUpload: DiscardSurveyResponseUploadUseCase,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(SurveyResponseHistoryState())
    val state = _state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), _state.value)

    init {
        viewModelScope.launch {
            observeUploads().collect { items -> _state.update { it.copy(items = items, isLoading = false) } }
        }
        viewModelScope.launch { refreshPage1() }
        syncCoordinator.requestSync()
    }

    fun onAction(action: SurveyResponseHistoryAction) {
        when (action) {
            is SurveyResponseHistoryAction.ResponseClick ->
                _state.update {
                    it.copy(
                        navigateToResponse = SurveyResponseTargetRoute(action.surveyId, action.responseId),
                    )
                }
            is SurveyResponseHistoryAction.RetryUploadClick -> viewModelScope.launch {
                retrySurveyResponseUpload(action.responseId)
                syncCoordinator.requestSync()
            }
            is SurveyResponseHistoryAction.FixResponseNumberClick -> _state.update {
                it.copy(navigateToNumberFix = SurveyResponseNumberFixRoute(action.localId, action.surveyName))
            }
            is SurveyResponseHistoryAction.DiscardClick -> viewModelScope.launch {
                // The reactive queue collector drops the row from `items` on its own.
                discardSurveyResponseUpload(action.responseId)
            }
            SurveyResponseHistoryAction.LoadMore -> loadMore()
            SurveyResponseHistoryAction.RetryLoad -> viewModelScope.launch { refreshPage1() }
        }
    }

    fun clearNavigation() {
        _state.update { it.copy(navigateToResponse = null, navigateToNumberFix = null) }
    }

    private suspend fun refreshPage1() {
        getHistoryPage(page = 1, pageSize = PAGE_SIZE).onSuccess { items ->
            _state.update { it.copy(page = 1, hasMore = items.size >= PAGE_SIZE) }
        }
    }

    private fun loadMore() {
        val current = _state.value
        if (!current.canLoadMore) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            getHistoryPage(page = current.page + 1, pageSize = PAGE_SIZE).fold(
                onSuccess = { items ->
                    _state.update { s ->
                        val seen = s.items.mapTo(mutableSetOf()) { it.id }
                        s.copy(
                            items = s.items + items.filterNot { it.id in seen },
                            page = current.page + 1,
                            hasMore = items.size >= PAGE_SIZE,
                            isLoadingMore = false,
                        )
                    }
                },
                onFailure = { _state.update { it.copy(isLoadingMore = false) } },
            )
        }
    }

    private companion object { const val PAGE_SIZE = 20 }
}
