package ar.com.ingenieriaalimentacion.presentation.feature.food.food_stock

import ar.com.ingenieriaalimentacion.domain.model.food.StockSortOrder
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus

sealed interface FoodStockAction {
    data object BackClick : FoodStockAction

    /** Near-end trigger from the LazyColumn. */
    data object LoadMore : FoodStockAction

    /** The retry surface's button. Re-runs page 1 (or the establishment resolve) after clearing. */
    data object RetryLoad : FoodStockAction

    /** `true` = "Disponibles" (sends `only_available=true`), `false` = "Todos". */
    data class OnlyAvailableChanged(val onlyAvailable: Boolean) : FoodStockAction

    /** `null` = "Todas las categorías" — the param is then omitted entirely. */
    data class CategorySelected(val categoryId: String?) : FoodStockAction

    data class SortSelected(val sortOrder: StockSortOrder) : FoodStockAction

    data object SortSheetOpen : FoodStockAction
    data object SortSheetDismiss : FoodStockAction
    data object CategorySheetOpen : FoodStockAction
    data object CategorySheetDismiss : FoodStockAction

    data object EstablishmentPickerOpen : FoodStockAction
    data object EstablishmentPickerDismiss : FoodStockAction
    data class EstablishmentSelected(val establishment: EstablishmentWithStatus) : FoodStockAction
}
