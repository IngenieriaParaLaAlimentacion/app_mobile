package ar.com.ingenieriaalimentacion.presentation.feature.subject.uploads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.domain.usecase.subject.DeleteSubjectUploadUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.GetSubjectHistoryPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.ObserveSubjectUploadsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.RetrySubjectUploadUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SubjectUploadsViewModel(
    observeUploads: ObserveSubjectUploadsUseCase,
    private val getPage: GetSubjectHistoryPageUseCase,
    private val retryUpload: RetrySubjectUploadUseCase,
    private val deleteUpload: DeleteSubjectUploadUseCase,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(SubjectUploadsState())
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    init {
        // Reactive floor: local queue + whatever's cached, so the screen never shows blank while
        // the network call below is in flight, and stays populated fully offline.
        viewModelScope.launch {
            observeUploads().collect { entries ->
                _state.update {
                    it.copy(uploads = entries.map { entry -> entry.toUi() }, isLoading = false)
                }
            }
        }
        // Page-1 network refresh on open — same trigger point as today's requestSync().
        viewModelScope.launch { refreshPage1() }
        syncCoordinator.requestSync()
    }

    fun onAction(action: SubjectUploadsAction) {
        when (action) {
            is SubjectUploadsAction.Retry -> retry(action.localId)
            is SubjectUploadsAction.Delete -> delete(action.localId)
            SubjectUploadsAction.LoadMore -> loadMore()
        }
    }

    private suspend fun refreshPage1() {
        getPage(page = 1).onSuccess { p ->
            _state.update { it.copy(page = p.page, totalPages = p.totalPages) }
        }
        // Failure is silent here: the reactive `observeUploads()` collector above already keeps
        // the screen populated from the local floor; page/totalPages simply don't advance.
    }

    private fun loadMore() {
        val current = _state.value
        if (!current.canLoadMore) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            getPage(page = current.page + 1).fold(
                onSuccess = { p ->
                    // Appends past the reactive floor's rows; dedup by id defends against the
                    // reactive collector re-emitting while a LoadMore is in flight.
                    _state.update { s ->
                        val seen = s.uploads.mapTo(mutableSetOf()) { it.localId }
                        s.copy(
                            uploads = s.uploads + p.items.map { it.toUi() }.filterNot { it.localId in seen },
                            page = p.page,
                            totalPages = p.totalPages,
                            isLoadingMore = false,
                        )
                    }
                },
                onFailure = { _state.update { it.copy(isLoadingMore = false) } },
            )
        }
    }

    private fun retry(localId: String) {
        viewModelScope.launch {
            retryUpload(localId)
            syncCoordinator.requestSync()
        }
    }

    private fun delete(localId: String) {
        viewModelScope.launch { deleteUpload(localId) }
    }
}
