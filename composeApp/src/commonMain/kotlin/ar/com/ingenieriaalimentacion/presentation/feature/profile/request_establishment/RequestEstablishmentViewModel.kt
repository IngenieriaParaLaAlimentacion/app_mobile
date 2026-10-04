package ar.com.ingenieriaalimentacion.presentation.feature.profile.request_establishment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.profile.EstablishmentRequestFailure
import ar.com.ingenieriaalimentacion.domain.model.auth.EstablishmentLinkMode
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentRequestError
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetAvailableEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.RequestEstablishmentAccessUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.SubscribeToEstablishmentsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class RequestEstablishmentViewModel(
    mode: EstablishmentLinkMode,
    private val getAvailable: GetAvailableEstablishmentsUseCase,
    private val requestAccess: RequestEstablishmentAccessUseCase,
    private val subscribe: SubscribeToEstablishmentsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(RequestEstablishmentState(mode = mode))
    val state = _state.asStateFlow()

    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            query
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { reload(it) }
        }
    }

    fun onAction(action: RequestEstablishmentAction) {
        when (action) {
            is RequestEstablishmentAction.QueryChanged -> {
                _state.update { it.copy(query = action.value) }
                query.value = action.value
            }

            RequestEstablishmentAction.LoadMore -> loadMore()
            is RequestEstablishmentAction.EstablishmentToggled -> toggle(action.id)
            RequestEstablishmentAction.SubmitClick -> submit()
            RequestEstablishmentAction.RetryClick ->
                viewModelScope.launch { reload(_state.value.query) }

            RequestEstablishmentAction.BackClick ->
                _state.update { it.copy(destination = RequestEstablishmentDestination.Back) }

            RequestEstablishmentAction.NotifyAbsenceClick ->
                _state.update { it.copy(destination = RequestEstablishmentDestination.NotifyAbsence) }

            RequestEstablishmentAction.AbsenceNoticeSent ->
                _state.update { it.copy(successMessage = ABSENCE_NOTICE_SENT_MESSAGE) }

            RequestEstablishmentAction.SuccessToastDismissed ->
                _state.update { it.copy(successMessage = null) }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    private fun toggle(id: String) {
        _state.update { current ->
            val selected = if (current.isSelected(id)) {
                current.selectedIds - id
            } else {
                current.selectedIds + id
            }
            current.copy(selectedIds = selected)
        }
    }

    private suspend fun reload(value: String) {
        _state.update { it.copy(isLoading = true, loadError = false) }
        getAvailable(value.takeIf { it.isNotBlank() }, page = 1, pageSize = PAGE_SIZE).fold(
            onSuccess = { paged ->
                _state.update {
                    it.copy(
                        results = paged.items,
                        page = paged.page,
                        totalPages = paged.totalPages,
                        isLoading = false,
                        loadError = false,
                    )
                }
            },
            onFailure = { throwable ->
                if (throwable is CancellationException) throw throwable
                _state.update { it.copy(isLoading = false, loadError = true) }
            },
        )
    }

    private fun loadMore() {
        val current = _state.value
        if (!current.canLoadMore) return
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            getAvailable(current.query.takeIf { it.isNotBlank() }, page = current.page + 1, pageSize = PAGE_SIZE)
                .fold(
                    onSuccess = { paged ->
                        _state.update {
                            it.copy(
                                results = it.results + paged.items,
                                page = paged.page,
                                totalPages = paged.totalPages,
                                isLoadingMore = false,
                            )
                        }
                    },
                    onFailure = { throwable ->
                        if (throwable is CancellationException) throw throwable
                        _state.update { it.copy(isLoadingMore = false) }
                    },
                )
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            val ids = current.selectedIds.toList()
            val result = when (current.mode) {
                EstablishmentLinkMode.Subscribe -> subscribe(ids)
                EstablishmentLinkMode.RequestAccess -> requestAccess(ids)
            }
            result.fold(
                onSuccess = {
                    // Keep isSubmitting = true until navigation tears down this screen, so the
                    // button doesn't flash back to idle before we pop back.
                    _state.update { it.copy(destination = RequestEstablishmentDestination.Submitted) }
                },
                onFailure = { throwable ->
                    if (throwable is CancellationException) throw throwable
                    val error = (throwable as? EstablishmentRequestFailure)?.error
                        ?: EstablishmentRequestError.Unknown(throwable)
                    _state.update { it.copy(isSubmitting = false, submitError = error) }
                    if (error is EstablishmentRequestError.EstablishmentNotFound) resync()
                },
            )
        }
    }

    /**
     * A selected establishment no longer exists (the call is all-or-nothing, so nothing was
     * linked): drop the selection and reload the catalog so the user picks again.
     */
    private suspend fun resync() {
        _state.update { it.copy(selectedIds = emptySet()) }
        reload(_state.value.query)
    }

    companion object {
        private const val DEBOUNCE_MS = 300L
        private const val PAGE_SIZE = 20
        const val ABSENCE_NOTICE_SENT_MESSAGE =
            "Tu aviso fue enviado, un administrador lo va a revisar."
    }
}
