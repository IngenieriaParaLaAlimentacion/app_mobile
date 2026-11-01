package ar.com.ingenieriaalimentacion.presentation.feature.food.food_stock

/**
 * Only [Back]: stock rows are not navigable — `GET /stock/{food_id}/movements` exists on the
 * backend but is out of scope for this story, and the establishment is re-picked in a sheet on this
 * screen (research §9.3: `Route.SelectEstablishment` reads the *public* catalog and does not persist
 * the choice, so it is the wrong mechanism for CA1).
 */
sealed class FoodStockDestination {
    data object Back : FoodStockDestination()
}
