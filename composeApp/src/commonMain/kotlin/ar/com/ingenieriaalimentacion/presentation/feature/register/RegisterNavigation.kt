package ar.com.ingenieriaalimentacion.presentation.feature.register

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.select_establishment.SELECTED_ESTABLISHMENTS_RESULT
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.select_establishment.navigateToSelectEstablishment
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToRegister(navOptions: NavOptions? = null) =
    navigate(Route.Register, navOptions)

fun NavGraphBuilder.registerScreen(navController: NavController) {
    composable<Route.Register> { entry ->
        val resultJson by entry.savedStateHandle
            .getStateFlow<String?>(SELECTED_ESTABLISHMENTS_RESULT, null)
            .collectAsStateWithLifecycle()
        RegisterScreenRoot(
            establishmentsResult = resultJson,
            onResultConsumed = { entry.savedStateHandle[SELECTED_ESTABLISHMENTS_RESULT] = null },
            navigate = { destination ->
                navigate(destination, navController)
            },
        )
    }
    composable<Route.RegisterSuccess> {
        RegisterSuccessScreen(
            onLoginClick = { /* TODO: navigate to LoginScreen once implemented */ },
        )
    }
}

internal fun navigate(
    destination: RegisterDestination,
    navController: NavController,
) {
    when (destination) {
        RegisterDestination.Success ->
            navController.navigate(Route.RegisterSuccess) {
                popUpTo(Route.Register) { inclusive = true }
            }

        is RegisterDestination.SelectEstablishment ->
            navController.navigateToSelectEstablishment(destination.preselectedJson)
    }
}
