package ar.com.ingenieriaalimentacion.presentation.feature.food.receipt_food_intake

sealed interface ReceiptFoodIntakeDestination {
    data object Back : ReceiptFoodIntakeDestination
    data object Saved : ReceiptFoodIntakeDestination
}
