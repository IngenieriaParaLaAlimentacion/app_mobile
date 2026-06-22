package ar.com.ingenieriaalimentacion.presentation.feature.subject.select_subject

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

/** Shared `savedStateHandle` key the picker writes its result under and the caller observes. */
const val SELECTED_SUBJECT_RESULT = "selected_subject_result"

fun NavController.navigateToSelectSubject(navOptions: NavOptions? = null) =
    navigate(Route.SelectSubject, navOptions)

fun NavGraphBuilder.selectSubjectScreen(navController: NavController) {
    composable<Route.SelectSubject> {
        SelectSubjectScreenRoot(
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: SelectSubjectDestination,
    navController: NavController,
) {
    when (destination) {
        SelectSubjectDestination.Back -> navController.popBackStack()
        is SelectSubjectDestination.Confirm -> {
            navController.previousBackStackEntry
                ?.savedStateHandle?.set(SELECTED_SUBJECT_RESULT, destination.subjectJson)
            navController.popBackStack()
        }
    }
}
