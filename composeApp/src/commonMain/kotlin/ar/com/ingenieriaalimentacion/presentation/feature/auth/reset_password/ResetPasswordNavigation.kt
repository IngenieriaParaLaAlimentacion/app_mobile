package ar.com.ingenieriaalimentacion.presentation.feature.auth.reset_password

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToResetPassword(token: String, navOptions: NavOptions? = null) =
    navigate(Route.ResetPassword(token), navOptions)

fun NavGraphBuilder.resetPasswordScreen(navController: NavController) {
    composable<Route.ResetPassword>(
        deepLinks = listOf(
            navDeepLink { uriPattern = "ingenieriaalimentacion://reset-password?token={token}" },
        ),
    ) { entry ->
        val route: Route.ResetPassword = entry.toRoute()
        ResetPasswordScreenRoot(
            route = route,
            navigate = { destination -> navigate(destination, navController) },
        )
    }
    composable<Route.ResetPasswordSuccess> {
        ResetPasswordSuccessScreen(
            onContinue = {
                navController.navigate(Route.GetStarted) {
                    popUpTo(0) { inclusive = true }
                }
            },
        )
    }
}

internal fun navigate(
    destination: ResetPasswordDestination,
    navController: NavController,
) {
    when (destination) {
        ResetPasswordDestination.Success ->
            navController.navigate(Route.ResetPasswordSuccess) {
                popUpTo<Route.ResetPassword> { inclusive = true }
            }
    }
}
