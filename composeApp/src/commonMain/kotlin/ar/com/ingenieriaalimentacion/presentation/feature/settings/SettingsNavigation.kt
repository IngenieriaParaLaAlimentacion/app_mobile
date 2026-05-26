package ar.com.ingenieriaalimentacion.presentation.feature.settings

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToSettings(navOptions: NavOptions? = null) =
    navigate(Route.Settings, navOptions)

fun NavGraphBuilder.settingsScreen(onLoggedOut: () -> Unit) {
    composable<Route.Settings> {
        SettingsScreenRoot(onLoggedOut = onLoggedOut)
    }
}
