package ar.com.ingenieriaalimentacion.presentation.feature.profile.establishments

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ar.com.ingenieriaalimentacion.presentation.feature.profile.request_establishment.navigateToRequestEstablishment
import ar.com.ingenieriaalimentacion.presentation.navigation.ESTABLISHMENTS_CHANGED_RESULT
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToMyEstablishments(role: String) =
    navigate(Route.MyEstablishments(role = role))

fun NavGraphBuilder.myEstablishmentsScreen(navController: NavController) {
    composable<Route.MyEstablishments> { entry ->
        val route = entry.toRoute<Route.MyEstablishments>()
        MyEstablishmentsScreenRoot(
            route = route,
            savedStateHandle = entry.savedStateHandle,
            navigate = { destination -> navigate(destination, navController, route.role) },
            onEstablishmentsChanged = {
                // MyEstablishments sits on the outer nav, so the previous entry is the
                // AuthedShell entry that HomeScreenRoot reads. Written eagerly (not on Back)
                // so a system back gesture still delivers it.
                navController.previousBackStackEntry?.savedStateHandle
                    ?.set(ESTABLISHMENTS_CHANGED_RESULT, true)
            },
        )
    }
}

internal fun navigate(
    destination: MyEstablishmentsDestination,
    navController: NavController,
    role: String,
) {
    when (destination) {
        MyEstablishmentsDestination.Back -> navController.popBackStack()
        MyEstablishmentsDestination.RequestEstablishment ->
            navController.navigateToRequestEstablishment(role = role)
    }
}
