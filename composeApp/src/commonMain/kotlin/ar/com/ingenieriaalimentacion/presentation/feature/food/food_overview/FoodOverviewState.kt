package ar.com.ingenieriaalimentacion.presentation.feature.food.food_overview

import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementSummary
import ar.com.ingenieriaalimentacion.domain.model.food.FoodCategory
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockFilters
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockItem
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus

data class FoodOverviewStockState(
    val items: List<FoodStockItem> = emptyList(),
    val categories: List<FoodCategory> = emptyList(),
    val filters: FoodStockFilters = FoodStockFilters(),
    val isLoading: Boolean = false,
    val error: FoodOverviewSectionError? = null,
    val showCategorySheet: Boolean = false,
    val showSortSheet: Boolean = false,
) {
    val selectedCategoryName: String?
        get() = filters.categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name }
}

data class FoodOverviewMovementsState(
    val items: List<EstablishmentFoodMovementSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: FoodOverviewSectionError? = null,
)

data class FoodOverviewState(
    val establishment: EstablishmentWithStatus? = null,
    val isResolvingEstablishment: Boolean = true,
    val establishmentProblem: EstablishmentProblem? = null,
    val isOnline: Boolean = true,
    val stock: FoodOverviewStockState = FoodOverviewStockState(),
    val movements: FoodOverviewMovementsState = FoodOverviewMovementsState(),
    val showReceiptSourceSheet: Boolean = false,
    val destination: FoodOverviewDestination? = null,
)

enum class EstablishmentProblem { NoneSelected, StaleSelection, LoadFailed }
enum class FoodOverviewSectionError { Retryable, AccessDenied, EstablishmentNotFound, Terminal }

internal const val STOCK_PREVIEW_SIZE = 5
