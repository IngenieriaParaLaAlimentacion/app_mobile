package ar.com.ingenieriaalimentacion.presentation.feature.food.establishment_food_movement_detail

sealed interface EstablishmentFoodMovementDetailAction {
    data object BackClick : EstablishmentFoodMovementDetailAction
    data object RetryLoad : EstablishmentFoodMovementDetailAction
}
