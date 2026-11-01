package ar.com.ingenieriaalimentacion.presentation.feature.food.establishment_food_movement_detail

import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementDetail

data class EstablishmentFoodMovementDetailState(
    val establishmentId: String,
    val movementId: String,
    val detail: EstablishmentFoodMovementDetail? = null,
    val isLoading: Boolean = true,
    val error: EstablishmentFoodMovementDetailError? = null,
    val isOnline: Boolean = true,
    val destination: EstablishmentFoodMovementDetailDestination? = null,
)

sealed interface EstablishmentFoodMovementDetailError {
    data object Retryable : EstablishmentFoodMovementDetailError
    data object NotFound : EstablishmentFoodMovementDetailError
    data object AccessDenied : EstablishmentFoodMovementDetailError
    data object Terminal : EstablishmentFoodMovementDetailError
}
