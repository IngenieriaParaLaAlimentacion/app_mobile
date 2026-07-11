package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_history

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToSurveyResponseHistory(navOptions: NavOptions? = null) =
    navigate(Route.SurveyResponseHistory, navOptions)

fun NavGraphBuilder.surveyResponseHistoryScreen(navController: NavController) {
    composable<Route.SurveyResponseHistory> {
        SurveyResponseHistoryScreenRoot(
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: SurveyResponseHistoryDestination,
    navController: NavController,
) {
    when (destination) {
        SurveyResponseHistoryDestination.Back -> navController.popBackStack()
        is SurveyResponseHistoryDestination.SurveyResponseDetail ->
            navController.navigate(Route.SurveyResponseDetail(destination.responseId))
    }
}
