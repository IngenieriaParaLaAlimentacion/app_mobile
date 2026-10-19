package ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.edit_characterization

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ar.com.ingenieriaalimentacion.presentation.navigation.ESTABLISHMENTS_CHANGED_RESULT
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToEditCharacterization(establishmentId: String, establishmentName: String) =
    navigate(Route.EditCharacterization(establishmentId = establishmentId, establishmentName = establishmentName))

fun NavGraphBuilder.editCharacterizationScreen(navController: NavController) {
    composable<Route.EditCharacterization> { entry ->
        EditCharacterizationScreenRoot(
            route = entry.toRoute(),
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: EditCharacterizationDestination,
    navController: NavController,
) {
    when (destination) {
        EditCharacterizationDestination.Back -> navController.popBackStack()
        EditCharacterizationDestination.Saved -> {
            // Opened from Home, the previous entry is the AuthedShell entry HomeScreenRoot reads;
            // from Mis establecimientos, it's that screen's own entry. Both refresh on this key,
            // so the "Pendiente de ficha" badge reflects the save.
            navController.previousBackStackEntry?.savedStateHandle?.set(ESTABLISHMENTS_CHANGED_RESULT, true)
            navController.popBackStack()
        }
    }
}
