package ar.com.ingenieriaalimentacion.presentation.feature.profile.establishments

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.feature.profile.request_establishment.navigateToRequestEstablishment
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToMyEstablishments() =
    navigate(Route.MyEstablishments)

fun NavGraphBuilder.myEstablishmentsScreen(navController: NavController) {
    composable<Route.MyEstablishments> { entry ->
        MyEstablishmentsScreenRoot(
            savedStateHandle = entry.savedStateHandle,
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: MyEstablishmentsDestination,
    navController: NavController,
) {
    when (destination) {
        MyEstablishmentsDestination.Back -> navController.popBackStack()
        MyEstablishmentsDestination.RequestEstablishment ->
            navController.navigateToRequestEstablishment()
    }
}
