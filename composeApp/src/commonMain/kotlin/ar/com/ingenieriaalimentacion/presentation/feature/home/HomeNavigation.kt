package ar.com.ingenieriaalimentacion.presentation.feature.home

// NOTE: unlike every sibling feature's *Navigation.kt, `homeScreen`/this file's `navigate` are
// NOT wired into any NavHost — Route.Home is actually composed with its own separate dispatch
// inline in app/AuthedShell.kt (Home lives on AuthedShell's inner NavHost, not the outer one
// these functions assume). Keep this file's `when` in sync with HomeDestination regardless, since
// it's still part of this package's public API surface, but AuthedShell.kt is the one that runs.

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_intake.navigateToCreateFoodIntake
import ar.com.ingenieriaalimentacion.presentation.feature.measurement.create_measurement.navigateToCreateMeasurement
import ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject.navigateToCreateSubject
import ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_list.navigateToSurveyList
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToHome(navOptions: NavOptions? = null) =
    navigate(Route.Home, navOptions)

fun NavGraphBuilder.homeScreen(navController: NavController) {
    composable<Route.Home> {
        HomeScreenRoot(
            navigate = { destination ->
                navigate(destination, navController)
            },
        )
    }
}

internal fun navigate(
    destination: HomeDestination,
    navController: NavController,
) {
    when (destination) {
        HomeDestination.Back -> navController.popBackStack()
        HomeDestination.NavigateToCreateSubject -> navController.navigateToCreateSubject()
        HomeDestination.NavigateToCreateMeasurement -> navController.navigateToCreateMeasurement()
        HomeDestination.NavigateToHistory -> navController.navigate(Route.History)
        HomeDestination.NavigateToSurveyList -> navController.navigateToSurveyList()
        HomeDestination.NavigateToCreateFoodIntake -> navController.navigateToCreateFoodIntake()
    }
}
