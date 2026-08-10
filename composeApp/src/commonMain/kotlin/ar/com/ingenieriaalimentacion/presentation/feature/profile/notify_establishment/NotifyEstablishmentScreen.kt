package ar.com.ingenieriaalimentacion.presentation.feature.profile.notify_establishment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlInput
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NotifyEstablishmentScreenRoot(
    viewModel: NotifyEstablishmentViewModel = koinViewModel(),
    navigate: (NotifyEstablishmentDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    NotifyEstablishmentScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun NotifyEstablishmentScreen(
    state: NotifyEstablishmentState,
    onAction: (NotifyEstablishmentAction) -> Unit,
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
                onBackClick = { onAction(NotifyEstablishmentAction.BackClick) },
                title = "Notificar comedor",
                enabled = !state.isSubmitting,
            )

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
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Contanos qué comedor no encontraste y un administrador " +
                            "lo va a revisar y crear. Todos los campos son opcionales.",
                        style = AlTheme.typography.body,
                        color = AlTheme.colors.textMuted,
                    )

                    AlInput(
                        label = "Nombre",
                        value = state.name.value,
                        onValueChange = { onAction(NotifyEstablishmentAction.NameChanged(it)) },
                        onFocusChange = { onAction(NotifyEstablishmentAction.NameFocusChanged(it)) },
                        error = state.name.displayError,
                        enabled = !state.isSubmitting,
                        scrollState = scrollState,
                        isImeVisible = isImeVisible,
                        placeholder = "Nombre del comedor",
                    )

                    AlInput(
                        label = "Dirección",
                        value = state.address.value,
                        onValueChange = { onAction(NotifyEstablishmentAction.AddressChanged(it)) },
                        onFocusChange = { onAction(NotifyEstablishmentAction.AddressFocusChanged(it)) },
                        error = state.address.displayError,
                        enabled = !state.isSubmitting,
                        scrollState = scrollState,
                        isImeVisible = isImeVisible,
                        placeholder = "Calle y número",
                    )

                    AlInput(
                        label = "Ciudad",
                        value = state.city.value,
                        onValueChange = { onAction(NotifyEstablishmentAction.CityChanged(it)) },
                        onFocusChange = { onAction(NotifyEstablishmentAction.CityFocusChanged(it)) },
                        error = state.city.displayError,
                        enabled = !state.isSubmitting,
                        scrollState = scrollState,
                        isImeVisible = isImeVisible,
                        placeholder = "Ciudad",
                    )

                    AlInput(
                        label = "Provincia",
                        value = state.province.value,
                        onValueChange = { onAction(NotifyEstablishmentAction.ProvinceChanged(it)) },
                        onFocusChange = { onAction(NotifyEstablishmentAction.ProvinceFocusChanged(it)) },
                        error = state.province.displayError,
                        enabled = !state.isSubmitting,
                        scrollState = scrollState,
                        isImeVisible = isImeVisible,
                        placeholder = "Provincia",
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = AlTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.submitError?.let { error ->
                        Text(
                            text = error.toUserMessage(),
                            color = AlTheme.colors.danger,
                            style = AlTheme.typography.body,
                        )
                    }

                    AlButton(
                        onClick = { onAction(NotifyEstablishmentAction.SubmitClick) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.canSubmit,
                        loading = state.isSubmitting,
                        fullWidth = true,
                    ) {
                        Text("Enviar aviso")
                    }
                }
            }
        }
    }
}
