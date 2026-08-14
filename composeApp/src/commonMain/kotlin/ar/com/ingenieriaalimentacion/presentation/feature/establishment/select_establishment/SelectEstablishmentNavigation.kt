package ar.com.ingenieriaalimentacion.presentation.feature.establishment.select_establishment

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

/** Shared `savedStateHandle` key the picker writes its result under and the caller observes. */
const val SELECTED_ESTABLISHMENTS_RESULT = "selected_establishments_result"

fun NavController.navigateToSelectEstablishment(preselectedJson: String, singleSelect: Boolean = false) =
    navigate(Route.SelectEstablishment(preselectedJson, singleSelect))

fun NavGraphBuilder.selectEstablishmentScreen(navController: NavController) {
    composable<Route.SelectEstablishment> { entry ->
        val route = entry.toRoute<Route.SelectEstablishment>()
        SelectEstablishmentScreenRoot(
            route = route,
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: SelectEstablishmentDestination,
    navController: NavController,
) {
    when (destination) {
        SelectEstablishmentDestination.Back -> navController.popBackStack()
        is SelectEstablishmentDestination.Confirm -> {
            navController.previousBackStackEntry
                ?.savedStateHandle?.set(SELECTED_ESTABLISHMENTS_RESULT, destination.selectedJson)
            navController.popBackStack()
        }
    }
}
