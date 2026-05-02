package ar.com.ingenieriaalimentacion.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App(
    appViewModel: AppViewModel = koinViewModel(),
    navController: NavHostController = rememberNavController(),
) {
    // Subscribe so the SessionStatus flow stays hot for the lifetime of App.
    // Future Login screen will branch start destination on this value.
    @Suppress("UNUSED_VARIABLE")
    val sessionStatus by appViewModel.sessionStatus.collectAsStateWithLifecycle()

    AlTheme {
        AppNavHost(
            navController = navController,
            startScreen = Route.Home,
        )
    }
}
