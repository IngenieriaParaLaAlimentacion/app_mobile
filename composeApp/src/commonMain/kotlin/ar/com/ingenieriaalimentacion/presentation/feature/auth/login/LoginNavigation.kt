package ar.com.ingenieriaalimentacion.presentation.feature.auth.login

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.feature.auth.forgot_password.navigateToForgotPassword
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToLogin(navOptions: NavOptions? = null) =
    navigate(Route.Login, navOptions)

fun NavGraphBuilder.loginScreen(navController: NavController) {
    composable<Route.Login> {
        LoginScreenRoot(
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: LoginDestination,
    navController: NavController,
) {
    when (destination) {
        LoginDestination.Home ->
            navController.navigate(Route.AuthedShell) {
                popUpTo(0) { inclusive = true }
            }
        LoginDestination.ForgotPassword -> navController.navigateToForgotPassword()
        LoginDestination.Back -> { navController.popBackStack() }
    }
}
