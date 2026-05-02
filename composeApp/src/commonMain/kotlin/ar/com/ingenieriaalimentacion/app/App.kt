package ar.com.ingenieriaalimentacion.app

import androidx.compose.runtime.Composable
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
    AlTheme {
        AppNavHost(
            navController = navController,
            startScreen = Route.Home,
        )
    }
}
