package ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.edit_characterization

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.AlErrorState
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationForm
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import com.komoui.components.Spinner
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun EditCharacterizationScreenRoot(
    route: Route.EditCharacterization,
    viewModel: EditCharacterizationViewModel = koinViewModel { parametersOf(route) },
    navigate: (EditCharacterizationDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    EditCharacterizationScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun EditCharacterizationScreen(
    state: EditCharacterizationState,
    onAction: (EditCharacterizationAction) -> Unit,
) {
    AlScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            AlBackTopAppBar(
                onBackClick = { onAction(EditCharacterizationAction.BackClick) },
                title = "Ficha del establecimiento",
                enabled = !state.isSaving,
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> Spinner(modifier = Modifier.align(Alignment.Center), size = 28.dp)

                    state.loadError != null -> AlErrorState(
                        title = "No se pudo cargar la ficha",
                        caption = state.loadError.toUserMessage(),
                        onRetry = { onAction(EditCharacterizationAction.RetryClick) },
                    )

                    else -> EditCharacterizationContent(state = state, onAction = onAction)
                }
            }
        }
    }
}

@Composable
private fun EditCharacterizationContent(
    state: EditCharacterizationState,
    onAction: (EditCharacterizationAction) -> Unit,
) {
    val scrollState = rememberScrollState()
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AlTheme.spacing.lg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(top = AlTheme.spacing.lg, bottom = AlTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.lg),
        ) {
            Text(
                text = state.establishmentName,
                style = AlTheme.typography.title,
                color = AlTheme.colors.textBase,
            )
            CharacterizationForm(
                state = state.form,
                onAction = { onAction(EditCharacterizationAction.Form(it)) },
                enabled = !state.isSaving,
                scrollState = scrollState,
                isImeVisible = isImeVisible,
            )
        }

        HorizontalDivider(color = AlTheme.colors.border)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AlTheme.spacing.md, bottom = AlTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
        ) {
            val message = state.saveError?.toUserMessage()
                ?: INVALID_FIELDS_MESSAGE.takeIf { state.hasInvalidFields }
            message?.let {
                Text(text = it, color = AlTheme.colors.danger, style = AlTheme.typography.body)
            }
            AlButton(
                onClick = { onAction(EditCharacterizationAction.SaveClick) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canEdit,
                loading = state.isSaving,
                fullWidth = true,
            ) {
                Text("Guardar ficha")
            }
        }
    }
}
