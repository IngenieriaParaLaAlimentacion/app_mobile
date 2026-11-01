package ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_intake

sealed class CreateFoodIntakeDestination {
    data object Back : CreateFoodIntakeDestination()
    data object Saved : CreateFoodIntakeDestination()
}
