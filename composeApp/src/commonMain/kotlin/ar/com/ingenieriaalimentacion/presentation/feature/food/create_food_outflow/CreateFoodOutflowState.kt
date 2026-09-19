package ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_outflow

import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.domain.model.food.FoodCategory
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockItem
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus

const val MAX_OUTFLOW_COMMENTS = 500

data class FoodOutflowLine(
    val stock: FoodStockItem,
    val quantity: TextFieldState = TextFieldState(),
)

data class AvailableStockState(
    val items: List<FoodStockItem> = emptyList(),
    val categories: List<FoodCategory> = emptyList(),
    val selectedCategoryId: String? = null,
    val page: Int = 1,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val showPicker: Boolean = false,
    val showCategoryPicker: Boolean = false,
) {
    val selectedCategoryName: String?
        get() = selectedCategoryId?.let { id -> categories.firstOrNull { it.id == id }?.name }
}

data class CreateFoodOutflowState(
    val destination: CreateFoodOutflowDestination? = null,
    val outflowId: String = "",
    val isLoadingProfile: Boolean = true,
    val profileError: String? = null,
    val establishmentOptions: List<EstablishmentWithStatus> = emptyList(),
    val selectedEstablishment: EstablishmentWithStatus? = null,
    val showEstablishmentPicker: Boolean = false,
    val occurredAt: TextFieldState = TextFieldState(),
    val comments: TextFieldState = TextFieldState(),
    val lines: List<FoodOutflowLine> = emptyList(),
    val stock: AvailableStockState = AvailableStockState(),
    val isOnline: Boolean = true,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val successMessage: String? = null,
) {
    val commentsCount: String get() = "${comments.value.length}/$MAX_OUTFLOW_COMMENTS"
    val canSubmit: Boolean
        get() = isOnline && !isSubmitting && selectedEstablishment != null && lines.isNotEmpty()
}
