package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.app.LocalOuterNavController
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SurveyResponseHistoryContentRoot(
    modifier: Modifier = Modifier,
    viewModel: SurveyResponseHistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val outerNav = LocalOuterNavController.current

    LaunchedEffect(state.navigateToResponseId) {
        state.navigateToResponseId?.let { id ->
            outerNav.navigate(Route.SurveyResponseDetail(id))
            viewModel.clearNavigation()
        }
    }

    SurveyResponseHistoryContent(state = state, onAction = viewModel::onAction, modifier = modifier)
}
