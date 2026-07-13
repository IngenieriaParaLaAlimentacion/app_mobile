package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_form

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.banner.AlBannerVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.banner.AlInlineBanner
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlSelectField
import ar.com.ingenieriaalimentacion.core.design_system.components.list.AlListRow
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.toast.AlToast
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.presentation.feature.survey.common.SurveyQuestionForm
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import com.komoui.components.Spinner
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SurveyResponseFormScreenRoot(
    route: Route.SurveyResponseForm,
    viewModel: SurveyResponseFormViewModel = koinViewModel { parametersOf(route) },
    navigate: (SurveyResponseFormDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    SurveyResponseFormScreen(state = state, onAction = viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyResponseFormScreen(
    state: SurveyResponseFormState,
    onAction: (SurveyResponseFormAction) -> Unit,
) {
    val scrollState = rememberScrollState()
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val sheetState = rememberModalBottomSheetState()

    AlScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            AlBackTopAppBar(
                onBackClick = { onAction(SurveyResponseFormAction.BackClick) },
                title = state.surveyName.ifBlank { "Responder encuesta" },
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
                        text = "No se pudo cargar la encuesta.",
                        style = AlTheme.typography.body,
                        color = AlTheme.colors.textMuted,
                    )
                    AlButton(onClick = { onAction(SurveyResponseFormAction.RetryLoad) }) { Text("Reintentar") }
                }

                else -> SurveyResponseFormBody(
                    state = state,
                    onAction = onAction,
                    scrollState = scrollState,
                    isImeVisible = isImeVisible,
                )
            }
        }

        if (state.showEstablishmentPicker) {
            ModalBottomSheet(
                onDismissRequest = { onAction(SurveyResponseFormAction.EstablishmentPickerDismiss) },
                sheetState = sheetState,
            ) {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    Text(
                        text = "Elegí el establecimiento",
                        style = AlTheme.typography.body,
                        color = AlTheme.colors.textBase,
                        modifier = Modifier.padding(horizontal = AlTheme.spacing.md, vertical = AlTheme.spacing.sm),
                    )
                    state.establishmentOptions.forEach { establishment ->
                        AlListRow(
                            title = establishment.name,
                            subtitle = establishment.formattedAddress,
                            onClick = { onAction(SurveyResponseFormAction.EstablishmentSelected(establishment)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SurveyResponseFormBody(
    state: SurveyResponseFormState,
    onAction: (SurveyResponseFormAction) -> Unit,
    scrollState: ScrollState,
    isImeVisible: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = AlTheme.spacing.lg)
                    .padding(top = AlTheme.spacing.lg, bottom = AlTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.lg),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                    AlSelectField(
                        label = "Establecimiento",
                        value = state.selectedEstablishment?.name ?: "",
                        placeholder = "Elegí el establecimiento",
                        error = state.establishmentError,
                        required = true,
                        enabled = !state.isSubmitting && state.establishmentOptions.isNotEmpty(),
                        onClick = { onAction(SurveyResponseFormAction.EstablishmentPickerOpen) },
                    )
                    if (state.establishmentOptions.isEmpty()) {
                        Text(
                            text = "No tenés establecimientos aprobados.",
                            style = AlTheme.typography.caption,
                            color = AlTheme.colors.textMuted,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                    Text(
                        text = "PREGUNTAS",
                        style = AlTheme.typography.caption.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp,
                        ),
                        color = AlTheme.colors.textMuted,
                    )
                    SurveyQuestionForm(
                        questions = state.questions,
                        answers = state.answers,
                        onAnswerChanged = { questionId, fieldState ->
                            onAction(SurveyResponseFormAction.AnswerChanged(questionId, fieldState))
                        },
                        enabled = !state.isSubmitting,
                        scrollState = scrollState,
                        isImeVisible = isImeVisible,
                    )
                }
            }

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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AlTheme.spacing.md, bottom = AlTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
                ) {
                    state.submitError?.let { error ->
                        AlInlineBanner(message = error, variant = AlBannerVariant.Danger)
                    }
                    AlButton(
                        onClick = { onAction(SurveyResponseFormAction.Submit) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.canSubmit,
                        loading = state.isSubmitting,
                        fullWidth = true,
                    ) {
                        Text("Guardar")
                    }
                }
            }
        }

        AlToast(
            message = state.successMessage,
            onDismiss = { onAction(SurveyResponseFormAction.DismissSuccess) },
        )
    }
}
