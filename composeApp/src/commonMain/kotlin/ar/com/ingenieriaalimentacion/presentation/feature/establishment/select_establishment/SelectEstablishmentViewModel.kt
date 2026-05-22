package ar.com.ingenieriaalimentacion.presentation.feature.establishment.select_establishment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment
import ar.com.ingenieriaalimentacion.domain.usecase.establishment.GetEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(FlowPreview::class)
class SelectEstablishmentViewModel(
    route: Route.SelectEstablishment,
    private val getEstablishments: GetEstablishmentsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SelectEstablishmentState(
            selected = runCatching {
                Json.decodeFromString<List<Establishment>>(route.preselectedJson)
            }.getOrDefault(emptyList()),
        ),
    )
    val state = _state.asStateFlow()

    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            query
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { search(it) }
        }
    }

    fun onAction(action: SelectEstablishmentAction) {
        when (action) {
            is SelectEstablishmentAction.QueryChanged -> {
                _state.update { it.copy(query = action.value) }
                query.value = action.value
            }

            is SelectEstablishmentAction.EstablishmentToggled -> toggle(action.establishment)

            SelectEstablishmentAction.ConfirmClick ->
                _state.update {
                    it.copy(
                        destination = SelectEstablishmentDestination.Confirm(
                            Json.encodeToString(it.selected),
                        ),
                    )
                }

            SelectEstablishmentAction.BackClick ->
                _state.update { it.copy(destination = SelectEstablishmentDestination.Back) }

            SelectEstablishmentAction.RetryClick ->
                viewModelScope.launch { search(_state.value.query) }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    private fun toggle(establishment: Establishment) {
        _state.update { current ->
            val selected = if (current.isSelected(establishment.id)) {
                current.selected.filterNot { it.id == establishment.id }
            } else {
                current.selected + establishment
            }
            current.copy(selected = selected)
        }
    }

    private suspend fun search(value: String) {
        _state.update { it.copy(isLoading = true, loadError = false) }
        getEstablishments(value.takeIf { it.isNotBlank() }).fold(
            onSuccess = { list ->
                _state.update { it.copy(results = list, isLoading = false, loadError = false) }
            },
            onFailure = { throwable ->
                if (throwable is CancellationException) throw throwable
                _state.update { it.copy(isLoading = false, loadError = true) }
            },
        )
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
