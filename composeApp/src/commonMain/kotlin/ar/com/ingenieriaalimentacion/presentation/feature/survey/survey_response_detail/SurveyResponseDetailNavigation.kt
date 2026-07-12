package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_detail

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToSurveyResponseDetail(responseId: String) =
    navigate(Route.SurveyResponseDetail(responseId))

fun NavGraphBuilder.surveyResponseDetailScreen(navController: NavController) {
    composable<Route.SurveyResponseDetail> { entry ->
        val route = entry.toRoute<Route.SurveyResponseDetail>()
        SurveyResponseDetailScreenRoot(
            route = route,
            savedStateHandle = entry.savedStateHandle,
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: SurveyResponseDetailDestination,
    navController: NavController,
) {
    when (destination) {
        SurveyResponseDetailDestination.Back -> navController.popBackStack()
        is SurveyResponseDetailDestination.SurveyResponseEdit ->
            navController.navigate(Route.SurveyResponseEdit(destination.responseId))
    }
}
