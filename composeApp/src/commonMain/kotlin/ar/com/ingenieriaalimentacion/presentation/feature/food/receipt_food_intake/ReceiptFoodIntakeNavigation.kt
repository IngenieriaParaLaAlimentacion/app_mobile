package ar.com.ingenieriaalimentacion.presentation.feature.food.receipt_food_intake

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.navigation.FOOD_MOVEMENT_CREATED_RESULT
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToReceiptFoodIntake(options: NavOptions? = null) =
    navigate(Route.ReceiptFoodIntake, options)

fun NavGraphBuilder.receiptFoodIntakeScreen(navController: NavController) {
    composable<Route.ReceiptFoodIntake> {
        ReceiptFoodIntakeScreenRoot(navigate = { destination ->
            when (destination) {
                ReceiptFoodIntakeDestination.Back -> navController.popBackStack()
                ReceiptFoodIntakeDestination.Saved -> {
                    navController.previousBackStackEntry?.savedStateHandle
                        ?.set(FOOD_MOVEMENT_CREATED_RESULT, true)
                    navController.popBackStack()
                }
            }
        })
    }
}
