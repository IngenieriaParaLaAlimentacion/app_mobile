package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import ar.com.ingenieriaalimentacion.presentation.navigation.RefreshOnNavResult
import ar.com.ingenieriaalimentacion.presentation.navigation.SURVEY_RESPONSE_EDITED_RESULT
import com.komoui.components.Spinner
import com.komoui.themes.styles
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SurveyResponseDetailScreenRoot(
    route: Route.SurveyResponseDetail,
    savedStateHandle: SavedStateHandle?,
    viewModel: SurveyResponseDetailViewModel = koinViewModel { parametersOf(route) },
    navigate: (SurveyResponseDetailDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    RefreshOnNavResult(
        savedStateHandle = savedStateHandle,
        key = SURVEY_RESPONSE_EDITED_RESULT,
    ) {
        viewModel.refresh()
    }

    SurveyResponseDetailScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun SurveyResponseDetailScreen(
    state: SurveyResponseDetailState,
    onAction: (SurveyResponseDetailAction) -> Unit,
) {
    val styles = MaterialTheme.styles

    AlScaffold {
        Column(modifier = Modifier.fillMaxSize()) {
            AlBackTopAppBar(
                onBackClick = { onAction(SurveyResponseDetailAction.BackClick) },
                title = state.surveyName.ifBlank { "Respuesta" },
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
                    AlButton(onClick = { onAction(SurveyResponseDetailAction.RetryLoad) }) { Text("Reintentar") }
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
                            .verticalScroll(rememberScrollState())
                            .padding(top = AlTheme.spacing.lg, bottom = AlTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = state.establishmentLabel,
                                style = AlTheme.typography.body,
                                color = AlTheme.colors.textBase,
                            )
                            Text(
                                text = state.eventAtLabel,
                                style = AlTheme.typography.caption,
                                color = AlTheme.colors.textMuted,
                            )
                        }

                        state.answeredQuestions.forEach { answered ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = answered.questionText,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = styles.foreground,
                                )
                                Text(
                                    text = answered.displayValue,
                                    style = AlTheme.typography.body,
                                    color = AlTheme.colors.textBase,
                                )
                            }
                        }
                    }

                    if (state.canEdit) {
                        AlButton(
                            onClick = { onAction(SurveyResponseDetailAction.EditClick) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = AlTheme.spacing.lg),
                            fullWidth = true,
                        ) {
                            Text("Editar")
                        }
                    }
                }
            }
        }
    }
}
