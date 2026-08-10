package ar.com.ingenieriaalimentacion.presentation.feature.profile.notify_establishment

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.navigation.ESTABLISHMENT_ABSENCE_NOTIFIED_RESULT
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToNotifyEstablishment() =
    navigate(Route.NotifyEstablishment)

fun NavGraphBuilder.notifyEstablishmentScreen(navController: NavController) {
    composable<Route.NotifyEstablishment> {
        NotifyEstablishmentScreenRoot(
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: NotifyEstablishmentDestination,
    navController: NavController,
) {
    when (destination) {
        NotifyEstablishmentDestination.Back -> navController.popBackStack()
        NotifyEstablishmentDestination.Submitted -> {
            // Signal RequestEstablishment (the previous entry) to show the sent
            // confirmation after we pop.
            navController.previousBackStackEntry?.savedStateHandle
                ?.set(ESTABLISHMENT_ABSENCE_NOTIFIED_RESULT, true)
            navController.popBackStack()
        }
    }
}
