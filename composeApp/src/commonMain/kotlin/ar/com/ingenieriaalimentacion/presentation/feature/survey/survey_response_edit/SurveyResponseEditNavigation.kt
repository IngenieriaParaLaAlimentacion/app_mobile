package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_edit

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import ar.com.ingenieriaalimentacion.presentation.navigation.SURVEY_RESPONSE_EDITED_RESULT

fun NavController.navigateToSurveyResponseEdit(surveyId: String, responseId: String) =
    navigate(Route.SurveyResponseEdit(surveyId, responseId))

fun NavGraphBuilder.surveyResponseEditScreen(navController: NavController) {
    composable<Route.SurveyResponseEdit> { entry ->
        val route = entry.toRoute<Route.SurveyResponseEdit>()
        SurveyResponseEditScreenRoot(
            route = route,
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: SurveyResponseEditDestination,
    navController: NavController,
) {
    when (destination) {
        SurveyResponseEditDestination.Back -> navController.popBackStack()
        SurveyResponseEditDestination.Saved -> {
            // SurveyResponseDetail (the previous entry) refreshes once it sees this flag.
            navController.previousBackStackEntry?.savedStateHandle?.set(SURVEY_RESPONSE_EDITED_RESULT, true)
            navController.popBackStack()
        }
    }
}
