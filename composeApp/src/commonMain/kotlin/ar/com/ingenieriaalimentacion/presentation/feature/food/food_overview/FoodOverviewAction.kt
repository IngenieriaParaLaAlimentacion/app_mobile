package ar.com.ingenieriaalimentacion.presentation.feature.food.food_overview

import ar.com.ingenieriaalimentacion.domain.model.food.StockSortOrder

sealed interface FoodOverviewAction {
    data object BackClick : FoodOverviewAction
    data object IntakeClick : FoodOverviewAction
    data object OutflowClick : FoodOverviewAction
    data object SeeAllMovementsClick : FoodOverviewAction
    data class MovementClick(val movementId: String) : FoodOverviewAction
    data object RetryAll : FoodOverviewAction
    data object RetryStock : FoodOverviewAction
    data object RetryMovements : FoodOverviewAction
    data object LoadMoreStock : FoodOverviewAction
    data class OnlyAvailableChanged(val value: Boolean) : FoodOverviewAction
    data class CategorySelected(val categoryId: String?) : FoodOverviewAction
    data class SortSelected(val sortOrder: StockSortOrder) : FoodOverviewAction
    data object CategorySheetOpen : FoodOverviewAction
    data object CategorySheetDismiss : FoodOverviewAction
    data object SortSheetOpen : FoodOverviewAction
    data object SortSheetDismiss : FoodOverviewAction
}
