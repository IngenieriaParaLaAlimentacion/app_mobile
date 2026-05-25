package ar.com.ingenieriaalimentacion.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import ar.com.ingenieriaalimentacion.presentation.feature.auth.reset_password.resetPasswordScreen
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.select_establishment.selectEstablishmentScreen
import ar.com.ingenieriaalimentacion.presentation.feature.get_started.getStartedScreen
import ar.com.ingenieriaalimentacion.presentation.feature.home.homeScreen
import ar.com.ingenieriaalimentacion.presentation.feature.register.registerScreen
import ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject.createSubjectScreen
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
        getStartedScreen(navController)
        homeScreen(navController)
        registerScreen(navController)
        selectEstablishmentScreen(navController)
        createSubjectScreen(navController)
        resetPasswordScreen(navController)
    }
}
