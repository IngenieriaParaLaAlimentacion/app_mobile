package ar.com.ingenieriaalimentacion.presentation.feature.register

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.header.AlScreenHeader
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlInput
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlPasswordInput
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlSelectField
import ar.com.ingenieriaalimentacion.core.design_system.components.input.alInputScroll
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment
import com.komoui.themes.styles
import kotlinx.serialization.json.Json
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RegisterScreenRoot(
    establishmentsResult: String?,
    onResultConsumed: () -> Unit,
    viewModel: RegisterViewModel = koinViewModel(),
    navigate: (RegisterDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    LaunchedEffect(establishmentsResult) {
        establishmentsResult?.let { json ->
            runCatching { Json.decodeFromString<List<Establishment>>(json) }
                .onSuccess { viewModel.onAction(RegisterAction.EstablishmentsSelected(it)) }
            onResultConsumed()
        }
    }

    RegisterScreen(
        state = state,
        onAction = viewModel::onAction,
    )
}

@Composable
fun RegisterScreen(
    state: RegisterState,
    onAction: (RegisterAction) -> Unit,
) {
    val styles = MaterialTheme.styles
    val scrollState = rememberScrollState()
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(styles.background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        AlBackTopAppBar(
            onBackClick = { onAction(RegisterAction.Back) },
            enabled = !state.isSubmitting,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .alInputScroll(scrollState)
                    .padding(top = AlTheme.spacing.md, bottom = 16.dp),
            ) {
                AlScreenHeader(
                    title = "Crear cuenta",
                    subtitle = "Registrate como responsable de establecimiento. " +
                        "Tu cuenta quedará pendiente de aprobación.",
                )

                Spacer(Modifier.height(AlTheme.spacing.lg))

                Column(
                    verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.md),
                ) {
                    AlInput(
                        label = "Email",
                        value = state.email,
                        onValueChange = { onAction(RegisterAction.EmailChanged(it)) },
                        error = state.fieldErrors.email,
                        enabled = !state.isSubmitting,
                        scrollState = scrollState,
                        isImeVisible = isImeVisible,
                        placeholder = "tu@email.com",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )

                    AlPasswordInput(
                        label = "Contraseña",
                        value = state.password,
                        onValueChange = { onAction(RegisterAction.PasswordChanged(it)) },
                        error = state.fieldErrors.password,
                        enabled = !state.isSubmitting,
                        scrollState = scrollState,
                        isImeVisible = isImeVisible,
                        placeholder = "Mínimo 8 caracteres",
                    )

                    AlInput(
                        label = "Nombre",
                        value = state.name,
                        onValueChange = { onAction(RegisterAction.NameChanged(it)) },
                        error = state.fieldErrors.name,
                        enabled = !state.isSubmitting,
                        scrollState = scrollState,
                        isImeVisible = isImeVisible,
                        placeholder = "Tu nombre",
                    )

                    AlInput(
                        label = "Apellido",
                        value = state.lastName,
                        onValueChange = { onAction(RegisterAction.LastNameChanged(it)) },
                        error = state.fieldErrors.lastName,
                        enabled = !state.isSubmitting,
                        scrollState = scrollState,
                        isImeVisible = isImeVisible,
                        placeholder = "Tu apellido",
                    )

                    val selectedNames = state.selectedEstablishments.map { it.name }
                    val selectionLabel = when {
                        selectedNames.isEmpty() -> ""
                        selectedNames.size == 1 -> selectedNames.first()
                        else -> "${selectedNames.size} establecimientos seleccionados"
                    }
                    AlSelectField(
                        label = "Establecimientos",
                        value = selectionLabel,
                        placeholder = "Seleccionar establecimiento (opcional)",
                        onClick = { onAction(RegisterAction.OpenEstablishmentSelector) },
                        enabled = !state.isSubmitting,
                    )

                    AlInput(
                        label = "Localidad",
                        value = state.locality.value,
                        onValueChange = { onAction(RegisterAction.LocalityChanged(it)) },
                        onFocusChange = { onAction(RegisterAction.LocalityFocusChanged(it)) },
                        error = state.locality.displayError,
                        enabled = !state.isSubmitting,
                        scrollState = scrollState,
                        isImeVisible = isImeVisible,
                        placeholder = "Tu localidad (opcional)",
                    )

                    AlInput(
                        label = "Descripción",
                        value = state.description.value,
                        onValueChange = { onAction(RegisterAction.DescriptionChanged(it)) },
                        onFocusChange = { onAction(RegisterAction.DescriptionFocusChanged(it)) },
                        error = state.description.displayError,
                        enabled = !state.isSubmitting,
                        singleLine = false,
                        scrollState = scrollState,
                        isImeVisible = isImeVisible,
                        placeholder = "Contanos sobre tu actividad (opcional)",
                    )

                    AlInput(
                        label = "Teléfono de contacto",
                        value = state.contactPhone.value,
                        onValueChange = { onAction(RegisterAction.ContactPhoneChanged(it)) },
                        onFocusChange = { onAction(RegisterAction.ContactPhoneFocusChanged(it)) },
                        error = state.contactPhone.displayError,
                        enabled = !state.isSubmitting,
                        scrollState = scrollState,
                        isImeVisible = isImeVisible,
                        placeholder = "+54 381 555 5555 (opcional)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = AlTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.submitError?.let { error ->
                    Text(text = error, color = styles.destructive, style = MaterialTheme.typography.bodyMedium)
                }
                AlButton(
                    onClick = { onAction(RegisterAction.Submit) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.canSubmit,
                    loading = state.isSubmitting,
                    fullWidth = true,
                ) {
                    Text("Crear cuenta")
                }
            }
        }
    }
}
