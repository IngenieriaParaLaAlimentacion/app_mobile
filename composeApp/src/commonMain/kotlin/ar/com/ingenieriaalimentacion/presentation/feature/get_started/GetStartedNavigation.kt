package ar.com.ingenieriaalimentacion.presentation.feature.get_started

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.feature.register.navigateToRegister
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToGetStarted(navOptions: NavOptions? = null) =
    navigate(Route.GetStarted, navOptions)

fun NavGraphBuilder.getStartedScreen(navController: NavController) {
    composable<Route.GetStarted> {
        GetStartedScreenRoot(
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: GetStartedDestination,
    navController: NavController,
) {
    when (destination) {
        GetStartedDestination.NavigateToRegister -> navController.navigateToRegister()
    }
}
