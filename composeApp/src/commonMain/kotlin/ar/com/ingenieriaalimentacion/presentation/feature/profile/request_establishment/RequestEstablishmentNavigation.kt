package ar.com.ingenieriaalimentacion.presentation.feature.profile.request_establishment

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ar.com.ingenieriaalimentacion.presentation.feature.profile.notify_establishment.navigateToNotifyEstablishment
import ar.com.ingenieriaalimentacion.presentation.navigation.ESTABLISHMENT_REQUESTED_RESULT
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToRequestEstablishment(role: String) =
    navigate(Route.RequestEstablishment(role = role))

fun NavGraphBuilder.requestEstablishmentScreen(navController: NavController) {
    composable<Route.RequestEstablishment> { entry ->
        RequestEstablishmentScreenRoot(
            route = entry.toRoute<Route.RequestEstablishment>(),
            savedStateHandle = entry.savedStateHandle,
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: RequestEstablishmentDestination,
    navController: NavController,
) {
    when (destination) {
        RequestEstablishmentDestination.Back -> navController.popBackStack()
        RequestEstablishmentDestination.Submitted -> {
            // Signal MyEstablishments (the previous entry) to refresh so the newly
            // requested access appears after we pop.
            navController.previousBackStackEntry?.savedStateHandle?.set(ESTABLISHMENT_REQUESTED_RESULT, true)
            navController.popBackStack()
        }

        RequestEstablishmentDestination.NotifyAbsence ->
            navController.navigateToNotifyEstablishment()
    }
}
