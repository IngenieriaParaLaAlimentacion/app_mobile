package ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_intake

import ar.com.ingenieriaalimentacion.domain.model.food.Food
import ar.com.ingenieriaalimentacion.domain.model.food.FoodIntakeOrigin

sealed interface CreateFoodIntakeAction {
    data class ReceivedAtChanged(val value: String) : CreateFoodIntakeAction
    data class ReceivedAtFocusChanged(val focused: Boolean) : CreateFoodIntakeAction
    data class CommentsChanged(val value: String) : CreateFoodIntakeAction
    data class CommentsFocusChanged(val focused: Boolean) : CreateFoodIntakeAction
    data class OriginSelected(val origin: FoodIntakeOrigin) : CreateFoodIntakeAction
    data class RecurrenceSelected(val isRecurring: Boolean) : CreateFoodIntakeAction
    data class SearchQueryChanged(val value: String) : CreateFoodIntakeAction
    data class SearchFocusChanged(val focused: Boolean) : CreateFoodIntakeAction
    data object SearchRetry : CreateFoodIntakeAction
    data class FoodPicked(val food: Food) : CreateFoodIntakeAction

    /** Lines are addressed by food id, not index — the list mutates while fields hold focus. */
    data class LineQuantityChanged(val foodId: String, val value: String) : CreateFoodIntakeAction
    data class LineQuantityFocusChanged(val foodId: String, val focused: Boolean) : CreateFoodIntakeAction
    data class LineRemoved(val foodId: String) : CreateFoodIntakeAction

    data object Submit : CreateFoodIntakeAction
    data object RetryProfile : CreateFoodIntakeAction
    data object BackClick : CreateFoodIntakeAction
}
