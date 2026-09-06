package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_list

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.select_establishment.SELECTED_ESTABLISHMENTS_RESULT
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.select_establishment.navigateToSelectEstablishment
import ar.com.ingenieriaalimentacion.presentation.feature.subject.select_subject.SELECTED_SUBJECT_RESULT
import ar.com.ingenieriaalimentacion.presentation.feature.subject.select_subject.navigateToSelectSubject
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import kotlinx.serialization.json.Json

fun NavController.navigateToSurveyList(navOptions: NavOptions? = null) =
    navigate(Route.SurveyList, navOptions)

fun NavGraphBuilder.surveyListScreen(navController: NavController) {
    // The pickers pop themselves on confirm and write their result onto *this* entry's
    // savedStateHandle, so the resulting back stack is SurveyList → SurveyResponseForm with no
    // picker wedged between them. Same shape as MeasurementNavigation's subject-result consumer.
    composable<Route.SurveyList> { entry ->
        val subjectJson by entry.savedStateHandle
            .getStateFlow<String?>(SELECTED_SUBJECT_RESULT, null)
            .collectAsStateWithLifecycle()
        val establishmentsJson by entry.savedStateHandle
            .getStateFlow<String?>(SELECTED_ESTABLISHMENTS_RESULT, null)
            .collectAsStateWithLifecycle()

        SurveyListScreenRoot(
            pickedSubject = subjectJson?.let {
                runCatching { Json.decodeFromString<SubjectSummary>(it) }.getOrNull()
            },
            pickedEstablishment = establishmentsJson?.let {
                runCatching { Json.decodeFromString<List<Establishment>>(it) }.getOrNull()?.firstOrNull()
            },
            onResultConsumed = {
                entry.savedStateHandle[SELECTED_SUBJECT_RESULT] = null
                entry.savedStateHandle[SELECTED_ESTABLISHMENTS_RESULT] = null
            },
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
            navController.navigate(
                Route.SurveyResponseForm(
                    surveyId = destination.surveyId,
                    targetId = destination.targetId,
                    targetLabel = destination.targetLabel,
                ),
            )
        SurveyListDestination.SelectSubject -> navController.navigateToSelectSubject()
        SurveyListDestination.SelectEstablishment ->
            navController.navigateToSelectEstablishment(preselectedJson = "[]", singleSelect = true)
    }
}
