package ar.com.ingenieriaalimentacion.presentation.feature.measurement.create_measurement

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary
import ar.com.ingenieriaalimentacion.presentation.feature.subject.select_subject.SELECTED_SUBJECT_RESULT
import ar.com.ingenieriaalimentacion.presentation.feature.subject.select_subject.navigateToSelectSubject
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import kotlinx.serialization.json.Json

fun NavController.navigateToCreateMeasurement(navOptions: NavOptions? = null) =
    navigate(Route.CreateMeasurement, navOptions)

fun NavGraphBuilder.createMeasurementScreen(navController: NavController) {
    composable<Route.CreateMeasurement> { entry ->
        val pickedJson by entry.savedStateHandle
            .getStateFlow<String?>(SELECTED_SUBJECT_RESULT, null)   // reused Historia 1 key
            .collectAsStateWithLifecycle()
        val subject = pickedJson?.let {
            runCatching { Json.decodeFromString<SubjectSummary>(it) }.getOrNull()
        }
        MeasurementScreenRoot(
            pickedSubjectResult = subject,
            onResultConsumed = { entry.savedStateHandle[SELECTED_SUBJECT_RESULT] = null },
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: MeasurementDestination,
    navController: NavController,
) {
    when (destination) {
        MeasurementDestination.Back -> navController.popBackStack()
        MeasurementDestination.NavigateToSelectSubject -> navController.navigateToSelectSubject()
    }
}
