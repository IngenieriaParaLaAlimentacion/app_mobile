package ar.com.ingenieriaalimentacion.presentation.feature.food.establishment_food_movement_history

import ar.com.ingenieriaalimentacion.domain.model.food.Food
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementDirection
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus

sealed interface EstablishmentFoodMovementHistoryAction {
    data object BackClick : EstablishmentFoodMovementHistoryAction
    data object LoadMore : EstablishmentFoodMovementHistoryAction
    data object RetryLoad : EstablishmentFoodMovementHistoryAction
    data object FilterOpen : EstablishmentFoodMovementHistoryAction
    data object FilterDismiss : EstablishmentFoodMovementHistoryAction
    data object FilterApply : EstablishmentFoodMovementHistoryAction
    data object FilterClear : EstablishmentFoodMovementHistoryAction
    data class DirectionChanged(
        val value: EstablishmentFoodMovementDirection?,
    ) : EstablishmentFoodMovementHistoryAction
    data class FromChanged(val value: String) : EstablishmentFoodMovementHistoryAction
    data class FromFocusChanged(val focused: Boolean) : EstablishmentFoodMovementHistoryAction
    data class ToChanged(val value: String) : EstablishmentFoodMovementHistoryAction
    data class ToFocusChanged(val focused: Boolean) : EstablishmentFoodMovementHistoryAction
    data class FoodQueryChanged(val value: String) : EstablishmentFoodMovementHistoryAction
    data class FoodFocusChanged(val focused: Boolean) : EstablishmentFoodMovementHistoryAction
    data class FoodSelected(val food: Food) : EstablishmentFoodMovementHistoryAction
    data object FoodSearchRetry : EstablishmentFoodMovementHistoryAction
    data object EstablishmentPickerOpen : EstablishmentFoodMovementHistoryAction
    data object EstablishmentPickerDismiss : EstablishmentFoodMovementHistoryAction
    data class EstablishmentSelected(
        val value: EstablishmentWithStatus,
    ) : EstablishmentFoodMovementHistoryAction
    data class MovementClick(val movementId: String) : EstablishmentFoodMovementHistoryAction
}
