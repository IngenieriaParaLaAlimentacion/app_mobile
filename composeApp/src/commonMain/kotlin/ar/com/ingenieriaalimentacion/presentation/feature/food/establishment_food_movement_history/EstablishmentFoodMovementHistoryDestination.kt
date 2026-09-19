package ar.com.ingenieriaalimentacion.presentation.feature.food.establishment_food_movement_history

sealed class EstablishmentFoodMovementHistoryDestination {
    data object Back : EstablishmentFoodMovementHistoryDestination()
    data class Detail(
        val establishmentId: String,
        val movementId: String,
    ) : EstablishmentFoodMovementHistoryDestination()
}
