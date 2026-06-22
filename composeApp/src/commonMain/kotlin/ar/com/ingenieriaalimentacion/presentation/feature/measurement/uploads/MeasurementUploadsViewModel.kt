package ar.com.ingenieriaalimentacion.presentation.feature.measurement.uploads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.DeleteMeasurementUploadUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.ObserveMeasurementsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.RetryMeasurementUploadUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MeasurementUploadsViewModel(
    observeUploads: ObserveMeasurementsUseCase,
    private val retryUpload: RetryMeasurementUploadUseCase,
    private val deleteUpload: DeleteMeasurementUploadUseCase,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(MeasurementUploadsState())
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

    fun onAction(action: MeasurementUploadsAction) {
        when (action) {
            is MeasurementUploadsAction.Retry -> retry(action.localId)
            is MeasurementUploadsAction.Delete -> delete(action.localId)
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
