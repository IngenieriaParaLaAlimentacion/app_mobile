package ar.com.ingenieriaalimentacion.presentation.feature.food.recipes

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.navigation.FOOD_MOVEMENT_CREATED_RESULT
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToRecipes(navOptions: NavOptions? = null) =
    navigate(Route.Recipes, navOptions)

fun NavGraphBuilder.recipesScreen(navController: NavController) {
    composable<Route.Recipes> { entry ->
        RecipesScreenRoot(
            savedStateHandle = entry.savedStateHandle,
            onStockChanged = {
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(FOOD_MOVEMENT_CREATED_RESULT, true)
            },
            navigate = { destination ->
                when (destination) {
                    RecipesDestination.Back -> navController.popBackStack()
                }
            },
        )
    }
}
