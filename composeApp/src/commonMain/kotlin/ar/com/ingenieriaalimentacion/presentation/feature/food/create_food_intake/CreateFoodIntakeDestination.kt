package ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_intake

/**
 * Only [Back]: a successful save keeps the user on the screen to load the next intake, and the
 * typeahead replaced the food picker, so there is nowhere else to go from here.
 */
sealed class CreateFoodIntakeDestination {
    data object Back : CreateFoodIntakeDestination()
}
