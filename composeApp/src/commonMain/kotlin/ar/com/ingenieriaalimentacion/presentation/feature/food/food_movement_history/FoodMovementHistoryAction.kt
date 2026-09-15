package ar.com.ingenieriaalimentacion.presentation.feature.food.food_movement_history

sealed interface FoodMovementHistoryAction {
    /** Near-end trigger from the LazyColumn. */
    data object LoadMore : FoodMovementHistoryAction

    /** The retry surface's button. Re-runs the page-1 refresh, clearing the error first. */
    data object RetryLoad : FoodMovementHistoryAction
}
