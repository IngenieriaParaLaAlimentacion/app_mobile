package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.domain.usecase.survey.GetSurveyResponseHistoryPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.RetrySurveyResponseUploadUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SurveyResponseHistoryViewModel(
    private val getHistoryPage: GetSurveyResponseHistoryPageUseCase,
    private val retrySurveyResponseUpload: RetrySurveyResponseUploadUseCase,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(SurveyResponseHistoryState())
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    init {
        load(page = 1)
    }

    fun onAction(action: SurveyResponseHistoryAction) {
        when (action) {
            is SurveyResponseHistoryAction.ResponseClick ->
                _state.update {
                    it.copy(destination = SurveyResponseHistoryDestination.SurveyResponseDetail(action.responseId))
                }

            is SurveyResponseHistoryAction.RetryUploadClick -> viewModelScope.launch {
                retrySurveyResponseUpload(action.responseId)
                syncCoordinator.requestSync()
                load(page = 1)
            }

            SurveyResponseHistoryAction.LoadMore -> if (_state.value.canLoadMore) load(page = _state.value.page + 1)
            SurveyResponseHistoryAction.RetryLoad -> load(page = 1)
            SurveyResponseHistoryAction.BackClick ->
                _state.update { it.copy(destination = SurveyResponseHistoryDestination.Back) }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    private fun load(page: Int) {
        _state.update {
            if (page == 1) it.copy(isLoading = true, loadError = false) else it.copy(isLoadingMore = true)
        }
        viewModelScope.launch {
            getHistoryPage(page = page, pageSize = PAGE_SIZE).fold(
                onSuccess = { items ->
                    _state.update {
                        it.copy(
                            items = if (page == 1) items else it.items + items,
                            page = page,
                            hasMore = items.size >= PAGE_SIZE,
                            isLoading = false,
                            isLoadingMore = false,
                            loadError = false,
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
