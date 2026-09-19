package ar.com.ingenieriaalimentacion.presentation.feature.food.establishment_food_movement_detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.network.connectivity.ConnectivityObserver
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementError
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementFailure
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetEstablishmentFoodMovementDetailUseCase
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EstablishmentFoodMovementDetailViewModel(
    route: Route.EstablishmentFoodMovementDetail,
    private val getDetail: GetEstablishmentFoodMovementDetailUseCase,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    private val _state = MutableStateFlow(
        EstablishmentFoodMovementDetailState(route.establishmentId, route.movementId),
    )
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> _state.update { it.copy(isOnline = online) } }
        }
        load()
    }

    fun onAction(action: EstablishmentFoodMovementDetailAction) {
        when (action) {
            EstablishmentFoodMovementDetailAction.BackClick -> _state.update {
                it.copy(destination = EstablishmentFoodMovementDetailDestination.Back)
            }
            EstablishmentFoodMovementDetailAction.RetryLoad -> load()
        }
    }

    fun clearDestination() = _state.update { it.copy(destination = null) }

    private fun load() {
        val state = _state.value
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            getDetail(state.establishmentId, state.movementId).fold(
                onSuccess = { detail ->
                    _state.update { it.copy(detail = detail, isLoading = false) }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(isLoading = false, error = throwable.toScreenError())
                    }
                },
            )
        }
    }

    private fun Throwable.toScreenError() =
        when ((this as? EstablishmentFoodMovementFailure)?.error) {
            EstablishmentFoodMovementError.Network, null ->
                EstablishmentFoodMovementDetailError.Retryable
            EstablishmentFoodMovementError.MovementNotFound,
            EstablishmentFoodMovementError.EstablishmentNotFound,
            -> EstablishmentFoodMovementDetailError.NotFound
            EstablishmentFoodMovementError.EstablishmentAccessDenied ->
                EstablishmentFoodMovementDetailError.AccessDenied
            else -> EstablishmentFoodMovementDetailError.Terminal
        }
}
