package ar.com.ingenieriaalimentacion.presentation.feature.food.food_overview

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_intake.navigateToCreateFoodIntake
import ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_outflow.navigateToCreateFoodOutflow
import ar.com.ingenieriaalimentacion.presentation.feature.food.establishment_food_movement_history.navigateToEstablishmentFoodMovementHistory
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToFoodOverview(navOptions: NavOptions? = null) =
    navigate(Route.FoodOverview, navOptions)

fun NavGraphBuilder.foodOverviewScreen(navController: NavController) {
    composable<Route.FoodOverview> { entry ->
        FoodOverviewScreenRoot(
            savedStateHandle = entry.savedStateHandle,
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(destination: FoodOverviewDestination, navController: NavController) {
    when (destination) {
        FoodOverviewDestination.Back -> navController.popBackStack()
        FoodOverviewDestination.CreateIntake -> navController.navigateToCreateFoodIntake()
        FoodOverviewDestination.CreateOutflow -> navController.navigateToCreateFoodOutflow()
        FoodOverviewDestination.MovementHistory ->
            navController.navigateToEstablishmentFoodMovementHistory()
        is FoodOverviewDestination.MovementDetail -> navController.navigate(
            Route.EstablishmentFoodMovementDetail(
                establishmentId = destination.establishmentId,
                movementId = destination.movementId,
            ),
        )
    }
}
