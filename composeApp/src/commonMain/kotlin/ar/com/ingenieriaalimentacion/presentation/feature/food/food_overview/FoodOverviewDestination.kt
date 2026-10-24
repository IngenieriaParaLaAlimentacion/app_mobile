package ar.com.ingenieriaalimentacion.presentation.feature.food.food_overview

import ar.com.ingenieriaalimentacion.presentation.feature.food.receipt_food_intake.ReceiptCaptureSource

sealed interface FoodOverviewDestination {
    data object Recipes : FoodOverviewDestination
    data object Stock : FoodOverviewDestination
    data object Back : FoodOverviewDestination
    data object CreateIntake : FoodOverviewDestination
    data class ReceiptIntake(val source: ReceiptCaptureSource) : FoodOverviewDestination
    data object CreateOutflow : FoodOverviewDestination
    data object MovementHistory : FoodOverviewDestination
    data class MovementDetail(
        val establishmentId: String,
        val movementId: String,
    ) : FoodOverviewDestination
}
