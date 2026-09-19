package ar.com.ingenieriaalimentacion.presentation.feature.food.food_overview

sealed interface FoodOverviewDestination {
    data object Back : FoodOverviewDestination
    data object CreateIntake : FoodOverviewDestination
    data object CreateOutflow : FoodOverviewDestination
    data object MovementHistory : FoodOverviewDestination
    data class MovementDetail(
        val establishmentId: String,
        val movementId: String,
    ) : FoodOverviewDestination
}
