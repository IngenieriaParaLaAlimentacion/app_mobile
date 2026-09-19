package ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_outflow

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToCreateFoodOutflow(navOptions: NavOptions? = null) =
    navigate(Route.CreateFoodOutflow, navOptions)

fun NavGraphBuilder.createFoodOutflowScreen(navController: NavController) {
    composable<Route.CreateFoodOutflow> {
        CreateFoodOutflowScreenRoot(navigate = { destination ->
            when (destination) {
                CreateFoodOutflowDestination.Back -> navController.popBackStack()
            }
        })
    }
}
