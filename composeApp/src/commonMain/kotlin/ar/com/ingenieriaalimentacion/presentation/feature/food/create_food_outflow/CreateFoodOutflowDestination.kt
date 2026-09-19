package ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_outflow

sealed class CreateFoodOutflowDestination {
    data object Back : CreateFoodOutflowDestination()
    data object Saved : CreateFoodOutflowDestination()
}
