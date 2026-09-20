package ar.com.ingenieriaalimentacion.presentation.feature.profile.establishments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.profile.EstablishmentWithdrawalFailure
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithdrawalError
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.WithdrawFromEstablishmentUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MyEstablishmentsViewModel(
    private val getMyEstablishments: GetMyEstablishmentsUseCase,
    private val withdrawFromEstablishment: WithdrawFromEstablishmentUseCase,
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
            is MyEstablishmentsAction.WithdrawClick -> showWithdrawConfirm(action.establishmentId)
            MyEstablishmentsAction.ConfirmWithdraw -> performWithdraw()
            MyEstablishmentsAction.DismissWithdrawConfirm -> dismissWithdrawConfirm()
            MyEstablishmentsAction.ToastDismissed ->
                _state.update { it.copy(successMessage = null, withdrawError = null) }
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

    private fun showWithdrawConfirm(establishmentId: String) {
        val establishment = _state.value.establishments.firstOrNull { it.id == establishmentId } ?: return
        _state.update { it.copy(pendingWithdraw = establishment) }
    }

    private fun dismissWithdrawConfirm() {
        if (_state.value.isWithdrawing) return
        _state.update { it.copy(pendingWithdraw = null) }
    }

    private fun performWithdraw() {
        val establishment = _state.value.pendingWithdraw ?: return
        if (_state.value.isWithdrawing) return
        _state.update { it.copy(isWithdrawing = true) }
        viewModelScope.launch {
            withdrawFromEstablishment(establishment.id).fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            establishments = it.establishments.filterNot { e -> e.id == establishment.id },
                            isWithdrawing = false,
                            pendingWithdraw = null,
                            successMessage = "Te diste de baja de ${establishment.name}.",
                        )
                    }
                },
                onFailure = { throwable ->
                    if (throwable is CancellationException) throw throwable
                    val error = (throwable as? EstablishmentWithdrawalFailure)?.error
                        ?: EstablishmentWithdrawalError.Unknown(throwable)
                    _state.update {
                        it.copy(isWithdrawing = false, pendingWithdraw = null, withdrawError = error)
                    }
                    // The relation was already gone/not-withdrawable server-side: our cached
                    // list is stale, so resync instead of leaving a now-wrong row on screen.
                    if (error is EstablishmentWithdrawalError.EstablishmentNotFound ||
                        error is EstablishmentWithdrawalError.NotWithdrawable
                    ) {
                        load()
                    }
                },
            )
        }
    }
}
