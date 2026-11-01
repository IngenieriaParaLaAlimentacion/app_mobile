package ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_intake

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import ar.com.ingenieriaalimentacion.presentation.navigation.FOOD_MOVEMENT_CREATED_RESULT

fun NavController.navigateToCreateFoodIntake(navOptions: NavOptions? = null) =
    navigate(Route.CreateFoodIntake, navOptions)

fun NavGraphBuilder.createFoodIntakeScreen(navController: NavController) {
    composable<Route.CreateFoodIntake> {
        CreateFoodIntakeScreenRoot(
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: CreateFoodIntakeDestination,
    navController: NavController,
) {
    when (destination) {
        CreateFoodIntakeDestination.Back -> navController.popBackStack()
        CreateFoodIntakeDestination.Saved -> {
            navController.previousBackStackEntry?.savedStateHandle
                ?.set(FOOD_MOVEMENT_CREATED_RESULT, true)
            navController.popBackStack()
        }
    }
}
