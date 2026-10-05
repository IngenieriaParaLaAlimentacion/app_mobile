package ar.com.ingenieriaalimentacion.presentation.feature.food.receipt_food_intake

import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.domain.model.food.Food
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptError
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptSource
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_intake.FoodSearchState

enum class ReceiptStep { Choose, Preview, Processing, Review }

data class ReceiptReviewLine(
    val id: Int,
    val description: String,
    val rawQuantity: String?,
    val rawUnitText: String?,
    val food: Food? = null,
    val quantity: TextFieldState = TextFieldState(),
    val reviewed: Boolean = false,
    val resolutionError: String? = null,
)

data class ReceiptFoodIntakeState(
    val destination: ReceiptFoodIntakeDestination? = null,
    val intakeId: String,
    val step: ReceiptStep = ReceiptStep.Choose,
    val photoToken: Int = 0,
    val photoName: String? = null,
    val photoSize: Int = 0,
    val isLoadingProfile: Boolean = true,
    val selectedEstablishment: EstablishmentWithStatus? = null,
    val profileError: String? = null,
    val source: ReceiptSource = ReceiptSource.Photo,
    val proposalMessage: String? = null,
    val lines: List<ReceiptReviewLine> = emptyList(),
    val search: FoodSearchState = FoodSearchState(),
    val searchLineId: Int? = null,
    val date: TextFieldState = TextFieldState(),
    val comments: TextFieldState = TextFieldState(),
    val isOnline: Boolean = true,
    val isSubmitting: Boolean = false,
    val submissionLocked: Boolean = false,
    val submitFailureKind: ReceiptError? = null,
    val showDiscardDialog: Boolean = false,
    val error: String? = null,
) {
    val canSubmit get() = step == ReceiptStep.Review && !isSubmitting && isOnline &&
        selectedEstablishment != null && lines.isNotEmpty() && !submissionLocked
}
