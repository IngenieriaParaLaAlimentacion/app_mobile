package ar.com.ingenieriaalimentacion.presentation.feature.food.establishment_food_movement_history

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToEstablishmentFoodMovementHistory(navOptions: NavOptions? = null) =
    navigate(Route.EstablishmentFoodMovementHistory, navOptions)

fun NavGraphBuilder.establishmentFoodMovementHistoryScreen(navController: NavController) {
    composable<Route.EstablishmentFoodMovementHistory> {
        EstablishmentFoodMovementHistoryScreenRoot(
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: EstablishmentFoodMovementHistoryDestination,
    navController: NavController,
) {
    when (destination) {
        EstablishmentFoodMovementHistoryDestination.Back -> navController.popBackStack()
        is EstablishmentFoodMovementHistoryDestination.Detail -> navController.navigate(
            Route.EstablishmentFoodMovementDetail(
                destination.establishmentId,
                destination.movementId,
            ),
        )
    }
}
