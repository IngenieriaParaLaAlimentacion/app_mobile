package ar.com.ingenieriaalimentacion.presentation.feature.food.food_stock

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToFoodStock(navOptions: NavOptions? = null) =
    navigate(Route.FoodStock, navOptions)

fun NavGraphBuilder.foodStockScreen(navController: NavController) {
    composable<Route.FoodStock> {
        FoodStockScreenRoot(
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: FoodStockDestination,
    navController: NavController,
) {
    when (destination) {
        FoodStockDestination.Back -> navController.popBackStack()
    }
}
