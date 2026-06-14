package ar.com.ingenieriaalimentacion.presentation.feature.profile.edit

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ar.com.ingenieriaalimentacion.presentation.navigation.PROFILE_UPDATED_RESULT
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToEditProfile(
    name: String,
    lastName: String,
    email: String,
    role: String,
) = navigate(Route.EditProfile(name = name, lastName = lastName, email = email, role = role))

fun NavGraphBuilder.editProfileScreen(navController: NavController) {
    composable<Route.EditProfile> { entry ->
        val route = entry.toRoute<Route.EditProfile>()
        EditProfileScreenRoot(
            route = route,
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: EditProfileDestination,
    navController: NavController,
) {
    when (destination) {
        EditProfileDestination.Back -> navController.popBackStack()
        EditProfileDestination.Saved -> {
            // EditProfile sits on the outer nav, so the previous entry is the
            // AuthedShell entry that ProfileScreenRoot reads on return.
            navController.previousBackStackEntry?.savedStateHandle?.set(PROFILE_UPDATED_RESULT, true)
            navController.popBackStack()
        }
    }
}
