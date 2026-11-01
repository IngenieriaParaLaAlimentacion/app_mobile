package ar.com.ingenieriaalimentacion.presentation.feature.food.establishment_food_movement_detail

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToEstablishmentFoodMovementDetail(
    establishmentId: String,
    movementId: String,
) = navigate(Route.EstablishmentFoodMovementDetail(establishmentId, movementId))

fun NavGraphBuilder.establishmentFoodMovementDetailScreen(navController: NavController) {
    composable<Route.EstablishmentFoodMovementDetail> { entry ->
        val route = entry.toRoute<Route.EstablishmentFoodMovementDetail>()
        EstablishmentFoodMovementDetailScreenRoot(
            route = route,
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: EstablishmentFoodMovementDetailDestination,
    navController: NavController,
) {
    when (destination) {
        EstablishmentFoodMovementDetailDestination.Back -> navController.popBackStack()
    }
}
