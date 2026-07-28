package ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ar.com.ingenieriaalimentacion.presentation.feature.subject.select_subject.SELECTED_SUBJECT_RESULT
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToCreateSubject(
    prefillDni: String = "",
    returnsResult: Boolean = false,
    navOptions: NavOptions? = null,
) = navigate(Route.CreateSubject(prefillDni, returnsResult), navOptions)

fun NavGraphBuilder.createSubjectScreen(navController: NavController) {
    composable<Route.CreateSubject> { entry ->
        CreateSubjectScreenRoot(
            route = entry.toRoute<Route.CreateSubject>(),
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

        // Reached only from the picker, so the stack is
        // CreateMeasurement → SelectSubject → CreateSubject. Drop this screen, hand the new
        // subject to the measurement form, then drop the picker too — the relevador lands back
        // in the form with the subject already selected instead of re-searching for it.
        is CreateSubjectDestination.Created -> {
            navController.popBackStack()
            navController.previousBackStackEntry
                ?.savedStateHandle?.set(SELECTED_SUBJECT_RESULT, destination.subjectJson)
            navController.popBackStack()
        }
    }
}
