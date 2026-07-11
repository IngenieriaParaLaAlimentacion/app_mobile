package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_form

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToSurveyResponseForm(surveyId: String) =
    navigate(Route.SurveyResponseForm(surveyId))

fun NavGraphBuilder.surveyResponseFormScreen(navController: NavController) {
    composable<Route.SurveyResponseForm> { entry ->
        val route = entry.toRoute<Route.SurveyResponseForm>()
        SurveyResponseFormScreenRoot(
            route = route,
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: SurveyResponseFormDestination,
    navController: NavController,
) {
    when (destination) {
        SurveyResponseFormDestination.Back -> navController.popBackStack()
    }
}
