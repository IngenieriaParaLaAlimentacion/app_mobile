package ar.com.ingenieriaalimentacion.presentation.feature.auth.verify_email

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import ar.com.ingenieriaalimentacion.presentation.navigation.DeepLinks
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavGraphBuilder.verifyEmailScreen(navController: NavController) {
    composable<Route.VerifyEmail>(
        deepLinks = listOf(
            navDeepLink { uriPattern = DeepLinks.VERIFY_EMAIL_URI_PATTERN },
        ),
    ) { entry ->
        val route: Route.VerifyEmail = entry.toRoute()
        VerifyEmailScreenRoot(
            route = route,
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: VerifyEmailDestination,
    navController: NavController,
) {
    when (destination) {
        VerifyEmailDestination.Exit -> {
            // A link opened from outside leaves [start destination, VerifyEmail] on the back
            // stack, and the start destination already reflects the session (GetStarted when
            // signed out, AuthedShell when signed in). Popping is therefore correct in both
            // cases and never signs anyone out.
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
            } else {
                // Only VerifyEmail on the stack: rebuild from the graph's start route.
                val start = navController.graph.startDestinationRoute
                if (start != null) {
                    navController.navigate(start) { popUpTo(0) { inclusive = true } }
                } else {
                    navController.navigate(Route.GetStarted) { popUpTo(0) { inclusive = true } }
                }
            }
        }
    }
}
