package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_number_fix

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavGraphBuilder.surveyResponseNumberFixScreen(navController: NavController) {
    composable<Route.SurveyResponseNumberFix> { entry ->
        SurveyResponseNumberFixScreenRoot(
            route = entry.toRoute(),
            navigate = { navController.popBackStack() },
        )
    }
}
