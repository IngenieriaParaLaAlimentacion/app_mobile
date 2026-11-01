package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlInput
import ar.com.ingenieriaalimentacion.core.design_system.components.input.alInputScroll
import androidx.compose.foundation.text.KeyboardOptions
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.presentation.feature.survey.common.SurveyQuestionForm
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import com.komoui.components.Spinner
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SurveyResponseEditScreenRoot(
    route: Route.SurveyResponseEdit,
    viewModel: SurveyResponseEditViewModel = koinViewModel { parametersOf(route) },
    navigate: (SurveyResponseEditDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    SurveyResponseEditScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun SurveyResponseEditScreen(
    state: SurveyResponseEditState,
    onAction: (SurveyResponseEditAction) -> Unit,
) {
    val scrollState = rememberScrollState()
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    AlScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            AlBackTopAppBar(
                onBackClick = { onAction(SurveyResponseEditAction.BackClick) },
                title = state.surveyName.ifBlank { "Editar respuesta" },
                enabled = !state.isSubmitting,
            )

            when {
                state.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Spinner(size = 28.dp)
                }

                state.loadError -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(AlTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "No se pudo cargar la respuesta.",
                        style = AlTheme.typography.body,
                        color = AlTheme.colors.textMuted,
                    )
                    AlButton(onClick = { onAction(SurveyResponseEditAction.RetryLoad) }) { Text("Reintentar") }
                }

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AlTheme.spacing.lg),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .alInputScroll(scrollState)
                            .padding(top = AlTheme.spacing.lg, bottom = AlTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = state.targetLabel,
                            style = AlTheme.typography.body,
                            color = AlTheme.colors.textMuted,
                        )

                        AlInput(
                            value = state.responseNumber.value,
                            onValueChange = { onAction(SurveyResponseEditAction.ResponseNumberChanged(it)) },
                            onFocusChange = { onAction(SurveyResponseEditAction.ResponseNumberFocusChanged(it)) },
                            label = "Número de respuesta", required = state.requiresResponseNumber == true,
                            error = state.responseNumber.displayError, enabled = !state.isSubmitting,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            scrollState = scrollState, isImeVisible = isImeVisible,
                        )

                        SurveyQuestionForm(
                            questions = state.questions,
                            answers = state.answers,
                            onAnswerChanged = { questionId, fieldState ->
                                onAction(SurveyResponseEditAction.AnswerChanged(questionId, fieldState))
                            },
                            enabled = !state.isSubmitting,
                            scrollState = scrollState,
                            isImeVisible = isImeVisible,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = AlTheme.spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        state.submitError?.let { error ->
                            Text(text = error, color = AlTheme.colors.danger, style = AlTheme.typography.body)
                        }
                        AlButton(
                            onClick = { onAction(SurveyResponseEditAction.Submit) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.canSubmit,
                            loading = state.isSubmitting,
                            fullWidth = true,
                        ) {
                            Text("Guardar cambios")
                        }
                    }
                }
            }
        }
    }
}
