package ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_outflow

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import ar.com.ingenieriaalimentacion.presentation.navigation.FOOD_MOVEMENT_CREATED_RESULT

fun NavController.navigateToCreateFoodOutflow(navOptions: NavOptions? = null) =
    navigate(Route.CreateFoodOutflow, navOptions)

fun NavGraphBuilder.createFoodOutflowScreen(navController: NavController) {
    composable<Route.CreateFoodOutflow> {
        CreateFoodOutflowScreenRoot(
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: CreateFoodOutflowDestination,
    navController: NavController,
) {
    when (destination) {
        CreateFoodOutflowDestination.Back -> navController.popBackStack()
        CreateFoodOutflowDestination.Saved -> {
            navController.previousBackStackEntry?.savedStateHandle
                ?.set(FOOD_MOVEMENT_CREATED_RESULT, true)
            navController.popBackStack()
        }
    }
}
