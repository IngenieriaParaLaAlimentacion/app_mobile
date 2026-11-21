package ar.com.ingenieriaalimentacion.presentation.feature.auth.verify_email

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import ar.com.ingenieriaalimentacion.presentation.navigation.DeepLinks
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavGraphBuilder.verifyEmailScreen(navController: NavController, startScreen: Route) {
    composable<Route.VerifyEmail>(
        deepLinks = listOf(
            navDeepLink { uriPattern = DeepLinks.VERIFY_EMAIL_URI_PATTERN },
        ),
    ) { entry ->
        val route: Route.VerifyEmail = entry.toRoute()
        VerifyEmailScreenRoot(
            route = route,
            navigate = { destination -> navigate(destination, navController, startScreen) },
        )
    }
}

internal fun navigate(
    destination: VerifyEmailDestination,
    navController: NavController,
    startScreen: Route,
) {
    when (destination) {
        VerifyEmailDestination.Exit -> {
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
            } else {
                navController.navigate(startScreen) { popUpTo(0) { inclusive = true } }
            }
        }
    }
}
