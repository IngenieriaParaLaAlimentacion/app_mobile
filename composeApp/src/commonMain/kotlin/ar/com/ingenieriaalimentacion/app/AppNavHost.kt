package ar.com.ingenieriaalimentacion.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import ar.com.ingenieriaalimentacion.presentation.feature.home.homeScreen
import ar.com.ingenieriaalimentacion.presentation.feature.register.registerScreen
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

@Composable
fun AppNavHost(
    navController: NavHostController,
    startScreen: Route,
) {
    NavHost(
        navController = navController,
        startDestination = startScreen,
    ) {
        homeScreen(navController)
        registerScreen(navController)
    }
}
