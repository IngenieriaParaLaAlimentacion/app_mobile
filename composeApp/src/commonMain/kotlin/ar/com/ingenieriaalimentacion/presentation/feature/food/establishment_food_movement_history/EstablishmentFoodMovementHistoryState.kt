package ar.com.ingenieriaalimentacion.presentation.feature.food.establishment_food_movement_history

import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementDirection
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementFilters
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementSummary
import ar.com.ingenieriaalimentacion.domain.model.food.Food
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus

data class MovementFilterDraft(
    val direction: EstablishmentFoodMovementDirection? = null,
    val from: TextFieldState = TextFieldState(),
    val to: TextFieldState = TextFieldState(),
    val foodQuery: String = "",
    val selectedFood: Food? = null,
    val foodSuggestions: List<Food> = emptyList(),
    val foodTotal: Int = 0,
    val foodFocused: Boolean = false,
    val isSearchingFood: Boolean = false,
    val foodSearchFailed: Boolean = false,
    val foodError: String? = null,
)

data class EstablishmentFoodMovementHistoryState(
    val establishment: EstablishmentWithStatus? = null,
    val establishmentOptions: List<EstablishmentWithStatus> = emptyList(),
    val showEstablishmentPicker: Boolean = false,
    val needsEstablishment: Boolean = false,
    val staleSelection: Boolean = false,
    val items: List<EstablishmentFoodMovementSummary> = emptyList(),
    val filters: EstablishmentFoodMovementFilters = EstablishmentFoodMovementFilters(),
    val filterDraft: MovementFilterDraft = MovementFilterDraft(),
    val showFilters: Boolean = false,
    val appliedFoodName: String? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val error: EstablishmentFoodMovementHistoryError? = null,
    val isOnline: Boolean = true,
    val destination: EstablishmentFoodMovementHistoryDestination? = null,
) {
    val canLoadMore get() =
        hasMore && !isLoading && !isLoadingMore && error == null && establishment != null
    val isEmpty get() = !isLoading && error == null && establishment != null && items.isEmpty()
    val showsBlockingError get() = error != null && items.isEmpty()
    val hasActiveFilters get() = filters != EstablishmentFoodMovementFilters()
}

sealed interface EstablishmentFoodMovementHistoryError {
    data object Retryable : EstablishmentFoodMovementHistoryError
    data object AccessDenied : EstablishmentFoodMovementHistoryError
    data object EstablishmentNotFound : EstablishmentFoodMovementHistoryError
    data object EstablishmentsUnavailable : EstablishmentFoodMovementHistoryError
    data object Terminal : EstablishmentFoodMovementHistoryError
}
