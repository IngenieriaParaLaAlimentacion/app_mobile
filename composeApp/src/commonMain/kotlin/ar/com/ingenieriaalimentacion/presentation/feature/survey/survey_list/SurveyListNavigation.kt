package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_list

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToSurveyList(navOptions: NavOptions? = null) =
    navigate(Route.SurveyList, navOptions)

fun NavGraphBuilder.surveyListScreen(navController: NavController) {
    composable<Route.SurveyList> {
        SurveyListScreenRoot(
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: SurveyListDestination,
    navController: NavController,
) {
    when (destination) {
        SurveyListDestination.Back -> navController.popBackStack()
        is SurveyListDestination.SurveyResponseForm ->
            navController.navigate(Route.SurveyResponseForm(destination.surveyId))
    }
}
