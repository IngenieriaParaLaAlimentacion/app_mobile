package ar.com.ingenieriaalimentacion.presentation.feature.auth.forgot_password

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToForgotPassword(navOptions: NavOptions? = null) =
    navigate(Route.ForgotPassword, navOptions)

fun NavGraphBuilder.forgotPasswordScreen(navController: NavController) {
    composable<Route.ForgotPassword> {
        ForgotPasswordScreenRoot(
            navigate = { destination ->
                when (destination) {
                    ForgotPasswordDestination.Back -> navController.popBackStack()
                }
            },
        )
    }
}
