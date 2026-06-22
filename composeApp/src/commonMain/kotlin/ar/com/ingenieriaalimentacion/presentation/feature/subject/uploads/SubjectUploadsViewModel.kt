package ar.com.ingenieriaalimentacion.presentation.feature.subject.uploads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.domain.usecase.subject.DeleteSubjectUploadUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.ObserveSubjectUploadsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.RetrySubjectUploadUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SubjectUploadsViewModel(
    observeUploads: ObserveSubjectUploadsUseCase,
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
        viewModelScope.launch {
            observeUploads().collect { uploads ->
                _state.update {
                    it.copy(uploads = uploads.map { upload -> upload.toUi() }, isLoading = false)
                }
            }
        }
        // Opportunistic sync whenever the screen is opened.
        syncCoordinator.requestSync()
    }

    fun onAction(action: SubjectUploadsAction) {
        when (action) {
            is SubjectUploadsAction.Retry -> retry(action.localId)
            is SubjectUploadsAction.Delete -> delete(action.localId)
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
