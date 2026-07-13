package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.banner.AlBannerVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.banner.AlInlineBanner
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.card.AlCard
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.toast.AlToast
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import ar.com.ingenieriaalimentacion.presentation.navigation.RefreshOnNavResult
import ar.com.ingenieriaalimentacion.presentation.navigation.SURVEY_RESPONSE_EDITED_RESULT
import com.komoui.components.Spinner
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_location_line
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_time
import org.jetbrains.compose.resources.vectorResource
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

                else -> Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = AlTheme.spacing.lg)
                                .padding(top = AlTheme.spacing.lg, bottom = AlTheme.spacing.md),
                            verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.lg),
                        ) {
                            if (state.editSyncState == SyncState.Failed) {
                                Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                                    AlInlineBanner(
                                        message = state.editErrorMessage
                                            ?: "No se pudo guardar tu última edición.",
                                        variant = AlBannerVariant.Danger,
                                    )
                                    AlButton(
                                        onClick = { onAction(SurveyResponseDetailAction.RetryEditClick) },
                                        variant = AlButtonVariant.Outline,
                                    ) {
                                        Text("Reintentar edición")
                                    }
                                }
                            }

                            ResponseMetaHeader(
                                establishmentLabel = state.establishmentLabel,
                                eventAtLabel = state.eventAtLabel,
                            )

                            AnsweredQuestionsCard(answeredQuestions = state.answeredQuestions)
                        }

                        if (state.canEdit) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(AlTheme.colors.surfaceBase)
                                        .padding(horizontal = AlTheme.spacing.lg),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(AlTheme.borders.thin)
                                            .background(AlTheme.colors.border),
                                    )
                                    AlButton(
                                        onClick = { onAction(SurveyResponseDetailAction.EditClick) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = AlTheme.spacing.md, bottom = AlTheme.spacing.lg),
                                        fullWidth = true,
                                    ) {
                                        Text("Editar")
                                    }
                                }
                            }
                        }
                    }

                    AlToast(
                        message = state.successMessage,
                        onDismiss = { onAction(SurveyResponseDetailAction.DismissSuccess) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResponseMetaHeader(establishmentLabel: String, eventAtLabel: String) {
    Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_mingcute_location_line),
                contentDescription = null,
                tint = AlTheme.colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = establishmentLabel,
                style = AlTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                color = AlTheme.colors.textBase,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_mingcute_time),
                contentDescription = null,
                tint = AlTheme.colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = eventAtLabel,
                style = AlTheme.typography.caption,
                color = AlTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun AnsweredQuestionsCard(answeredQuestions: List<AnsweredQuestionUi>) {
    Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
        Text(
            text = "RESPUESTAS",
            style = AlTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
            color = AlTheme.colors.textMuted,
        )
        AlCard {
            answeredQuestions.forEachIndexed { index, answered ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AlTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs),
                ) {
                    Text(
                        text = answered.questionText,
                        style = AlTheme.typography.caption,
                        color = AlTheme.colors.textMuted,
                    )
                    Text(
                        text = answered.displayValue,
                        style = AlTheme.typography.body.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
                        color = AlTheme.colors.textBase,
                    )
                }
                if (index != answeredQuestions.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(AlTheme.borders.thin)
                            .background(AlTheme.colors.border),
                    )
                }
            }
        }
    }
}
