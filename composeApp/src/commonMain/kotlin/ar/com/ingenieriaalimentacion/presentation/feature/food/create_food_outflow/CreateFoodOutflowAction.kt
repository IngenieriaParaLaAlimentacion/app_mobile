package ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_outflow

import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockItem
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus

sealed interface CreateFoodOutflowAction {
    data object BackClick : CreateFoodOutflowAction
    data object RetryProfile : CreateFoodOutflowAction
    data object EstablishmentPickerOpen : CreateFoodOutflowAction
    data object EstablishmentPickerDismiss : CreateFoodOutflowAction
    data class EstablishmentSelected(val value: EstablishmentWithStatus) : CreateFoodOutflowAction
    data class OccurredAtChanged(val value: String) : CreateFoodOutflowAction
    data class OccurredAtFocusChanged(val focused: Boolean) : CreateFoodOutflowAction
    data class CommentsChanged(val value: String) : CreateFoodOutflowAction
    data class CommentsFocusChanged(val focused: Boolean) : CreateFoodOutflowAction
    data object StockPickerOpen : CreateFoodOutflowAction
    data object StockPickerDismiss : CreateFoodOutflowAction
    data object CategoryPickerOpen : CreateFoodOutflowAction
    data object CategoryPickerDismiss : CreateFoodOutflowAction
    data class CategorySelected(val id: String?) : CreateFoodOutflowAction
    data object RetryStock : CreateFoodOutflowAction
    data object LoadMoreStock : CreateFoodOutflowAction
    data class FoodPicked(val item: FoodStockItem) : CreateFoodOutflowAction
    data class LineQuantityChanged(val foodId: String, val value: String) : CreateFoodOutflowAction
    data class LineQuantityFocusChanged(val foodId: String, val focused: Boolean) : CreateFoodOutflowAction
    data class LineRemoved(val foodId: String) : CreateFoodOutflowAction
    data object Submit : CreateFoodOutflowAction
    data object DismissSuccess : CreateFoodOutflowAction
}
