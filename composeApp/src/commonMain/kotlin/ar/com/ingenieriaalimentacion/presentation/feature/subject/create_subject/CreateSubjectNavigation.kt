package ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToCreateSubject(navOptions: NavOptions? = null) =
    navigate(Route.CreateSubject, navOptions)

fun NavGraphBuilder.createSubjectScreen(navController: NavController) {
    composable<Route.CreateSubject> {
        CreateSubjectScreenRoot(
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: CreateSubjectDestination,
    navController: NavController,
) {
    when (destination) {
        CreateSubjectDestination.Back -> navController.popBackStack()
    }
}
