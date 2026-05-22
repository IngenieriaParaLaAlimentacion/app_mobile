package ar.com.ingenieriaalimentacion.presentation.feature.register

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToRegister(navOptions: NavOptions? = null) =
    navigate(Route.Register, navOptions)

fun NavGraphBuilder.registerScreen(navController: NavController) {
    composable<Route.Register> {
        RegisterScreenRoot(
            navigate = { destination ->
                navigate(destination, navController)
            },
        )
    }
    composable<Route.RegisterSuccess> {
        RegisterSuccessScreen()
    }
}

internal fun navigate(
    destination: RegisterDestination,
    navController: NavController,
) {
    when (destination) {
        RegisterDestination.Success ->
            navController.navigate(Route.RegisterSuccess) {
                popUpTo(Route.Register) { inclusive = true }
            }
    }
}
