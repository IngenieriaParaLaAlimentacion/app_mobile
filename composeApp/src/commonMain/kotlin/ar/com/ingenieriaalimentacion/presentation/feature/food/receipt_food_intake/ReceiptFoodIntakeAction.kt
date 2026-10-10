package ar.com.ingenieriaalimentacion.presentation.feature.food.receipt_food_intake

import ar.com.ingenieriaalimentacion.domain.model.food.Food
import ar.com.ingenieriaalimentacion.domain.model.food.FoodIntakeOrigin
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptPhoto

sealed interface ReceiptFoodIntakeAction {
    data object CameraClick : ReceiptFoodIntakeAction
    data object GalleryClick : ReceiptFoodIntakeAction
    data class PhotoPicked(val result: Result<ReceiptPhoto>?) : ReceiptFoodIntakeAction
    data object RemovePhoto : ReceiptFoodIntakeAction
    data object Analyze : ReceiptFoodIntakeAction
    data object CancelAnalysis : ReceiptFoodIntakeAction
    data object ManualFallback : ReceiptFoodIntakeAction
    data class FoodSearchChanged(val lineId: Int, val value: String) : ReceiptFoodIntakeAction
    data class FoodSearchFocusChanged(val lineId: Int, val focused: Boolean) : ReceiptFoodIntakeAction
    data object SearchRetry : ReceiptFoodIntakeAction
    data class FoodPicked(val lineId: Int, val food: Food) : ReceiptFoodIntakeAction
    data class QuantityChanged(val lineId: Int, val value: String) : ReceiptFoodIntakeAction
    data class QuantityFocusChanged(val lineId: Int, val focused: Boolean) : ReceiptFoodIntakeAction
    data class LineConfirmed(val lineId: Int) : ReceiptFoodIntakeAction
    data class LineRemoved(val lineId: Int) : ReceiptFoodIntakeAction
    data object AddLine : ReceiptFoodIntakeAction
    data class DateChanged(val value: String) : ReceiptFoodIntakeAction
    data class DateFocusChanged(val focused: Boolean) : ReceiptFoodIntakeAction
    data class CommentsChanged(val value: String) : ReceiptFoodIntakeAction
    data class CommentsFocusChanged(val focused: Boolean) : ReceiptFoodIntakeAction
    data class OriginSelected(val origin: FoodIntakeOrigin) : ReceiptFoodIntakeAction
    data class RecurrenceSelected(val isRecurring: Boolean) : ReceiptFoodIntakeAction
    data object Submit : ReceiptFoodIntakeAction
    data object RetrySubmit : ReceiptFoodIntakeAction
    data object ReviseAfterFailure : ReceiptFoodIntakeAction
    data object RetryProfile : ReceiptFoodIntakeAction
    data object BackClick : ReceiptFoodIntakeAction
    data object ConfirmDiscard : ReceiptFoodIntakeAction
    data object CancelDiscard : ReceiptFoodIntakeAction
}
