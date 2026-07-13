package ar.com.ingenieriaalimentacion.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.feature.auth.forgot_password.forgotPasswordScreen
import ar.com.ingenieriaalimentacion.presentation.feature.auth.login.loginScreen
import ar.com.ingenieriaalimentacion.presentation.feature.auth.reset_password.resetPasswordScreen
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.select_establishment.selectEstablishmentScreen
import ar.com.ingenieriaalimentacion.presentation.feature.get_started.getStartedScreen
import ar.com.ingenieriaalimentacion.presentation.feature.measurement.create_measurement.createMeasurementScreen
import ar.com.ingenieriaalimentacion.presentation.feature.profile.edit.editProfileScreen
import ar.com.ingenieriaalimentacion.presentation.feature.profile.establishments.myEstablishmentsScreen
import ar.com.ingenieriaalimentacion.presentation.feature.profile.request_establishment.requestEstablishmentScreen
import ar.com.ingenieriaalimentacion.presentation.feature.register.registerScreen
import ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject.createSubjectScreen
import ar.com.ingenieriaalimentacion.presentation.feature.subject.select_subject.selectSubjectScreen
import ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_list.surveyListScreen
import ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_detail.surveyResponseDetailScreen
import ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_edit.surveyResponseEditScreen
import ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_form.surveyResponseFormScreen
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

@Composable
fun AppNavHost(
    navController: NavHostController,
    startScreen: Route,
) {
    CompositionLocalProvider(LocalOuterNavController provides navController) {
        NavHost(
            navController = navController,
            startDestination = startScreen,
        ) {
            getStartedScreen(navController)
            registerScreen(navController)
            loginScreen(navController)
            selectEstablishmentScreen(navController)
            createSubjectScreen(navController)
            createMeasurementScreen(navController)
            selectSubjectScreen(navController)
            editProfileScreen(navController)
            requestEstablishmentScreen(navController)
            myEstablishmentsScreen(navController)
            resetPasswordScreen(navController)
            forgotPasswordScreen(navController)
            surveyListScreen(navController)
            surveyResponseFormScreen(navController)
            surveyResponseDetailScreen(navController)
            surveyResponseEditScreen(navController)
            composable<Route.AuthedShell> {
                AuthedShell(
                    onSignOut = {
                        navController.navigate(Route.GetStarted) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}
