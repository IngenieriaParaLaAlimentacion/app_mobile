package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_number_fix

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlInput
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import androidx.compose.foundation.text.KeyboardOptions
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable fun SurveyResponseNumberFixScreenRoot(route: Route.SurveyResponseNumberFix, navigate: (SurveyResponseNumberFixDestination) -> Unit, viewModel: SurveyResponseNumberFixViewModel = koinViewModel { parametersOf(route) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.destination) { state.destination?.let { navigate(it); viewModel.clearDestination() } }
    AlScaffold { Column(Modifier.fillMaxSize()) {
        AlBackTopAppBar(title = "Corregir número", onBackClick = { viewModel.onAction(SurveyResponseNumberFixAction.Back) })
        Column(Modifier.padding(AlTheme.spacing.lg)) {
            Text(state.surveyName, style = AlTheme.typography.bodyStrong)
            AlInput(value = state.number.value, onValueChange = { viewModel.onAction(SurveyResponseNumberFixAction.NumberChanged(it)) }, onFocusChange = { viewModel.onAction(SurveyResponseNumberFixAction.NumberFocusChanged(it)) }, label = "Número de respuesta", required = true, error = state.number.displayError, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            state.submitError?.let { Text(it, color = AlTheme.colors.danger) }
            AlButton(onClick = { viewModel.onAction(SurveyResponseNumberFixAction.Submit) }, modifier = Modifier.fillMaxWidth(), enabled = state.canSubmit, loading = state.isSubmitting, fullWidth = true) { Text("Guardar y reenviar") }
        }
    } }
}
