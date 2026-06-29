package ar.com.ingenieriaalimentacion.presentation.feature.home

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.feature.measurement.create_measurement.navigateToCreateMeasurement
import ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject.navigateToCreateSubject
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToHome(navOptions: NavOptions? = null) =
    navigate(Route.Home, navOptions)

fun NavGraphBuilder.homeScreen(navController: NavController) {
    composable<Route.Home> {
        HomeScreenRoot(
            navigate = { destination ->
                navigate(destination, navController)
            },
        )
    }
}

internal fun navigate(
    destination: HomeDestination,
    navController: NavController,
) {
    when (destination) {
        HomeDestination.Back -> navController.popBackStack()
        HomeDestination.NavigateToCreateSubject -> navController.navigateToCreateSubject()
        HomeDestination.NavigateToCreateMeasurement -> navController.navigateToCreateMeasurement()
        HomeDestination.NavigateToHistory -> navController.navigate(Route.History)
    }
}
