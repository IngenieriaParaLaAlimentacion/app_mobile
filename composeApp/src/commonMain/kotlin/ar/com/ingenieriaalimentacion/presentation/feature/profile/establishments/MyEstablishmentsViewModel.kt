package ar.com.ingenieriaalimentacion.presentation.feature.profile.establishments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyEstablishmentsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MyEstablishmentsViewModel(
    private val getMyEstablishments: GetMyEstablishmentsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(MyEstablishmentsState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun onAction(action: MyEstablishmentsAction) {
        when (action) {
            MyEstablishmentsAction.Load -> load()
            MyEstablishmentsAction.RetryClick -> load()
            MyEstablishmentsAction.RequestAccessClick ->
                _state.update { it.copy(destination = MyEstablishmentsDestination.RequestEstablishment) }
            MyEstablishmentsAction.BackClick ->
                _state.update { it.copy(destination = MyEstablishmentsDestination.Back) }
        }
    }

    /** Re-fetches on resume so an approval granted elsewhere shows up immediately. */
    fun refresh() = load()

    /** Display order: approved first, then pending, then rejected, then unknown.
     *  Withdrawn/Revoked never reach this list (filtered upstream by
     *  [ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyEstablishmentsUseCase])
     *  but still need a spot to keep this `when` exhaustive. */
    private fun statusOrder(status: EstablishmentStatus): Int = when (status) {
        EstablishmentStatus.Approved -> 0
        EstablishmentStatus.Pending -> 1
        EstablishmentStatus.Rejected -> 2
        EstablishmentStatus.Withdrawn, EstablishmentStatus.Revoked, EstablishmentStatus.Unknown -> 3
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, loadError = false) }
        viewModelScope.launch {
            getMyEstablishments().fold(
                onSuccess = { establishments ->
                    val ordered = establishments.sortedBy { statusOrder(it.status) }
                    _state.update {
                        it.copy(establishments = ordered, isLoading = false, loadError = false)
                    }
                },
                onFailure = { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _state.update { it.copy(isLoading = false, loadError = true) }
                },
            )
        }
    }
}
