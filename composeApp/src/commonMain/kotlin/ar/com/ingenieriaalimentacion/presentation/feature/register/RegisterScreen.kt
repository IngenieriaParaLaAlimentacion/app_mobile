package ar.com.ingenieriaalimentacion.presentation.feature.register

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment
import com.komoui.components.Button
import com.komoui.components.Input
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
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Crear cuenta",
            style = MaterialTheme.typography.headlineMedium,
            color = styles.foreground,
        )
        Text(
            text = "Registrate como responsable de establecimiento. Tu cuenta quedará pendiente de aprobación.",
            style = MaterialTheme.typography.bodyMedium,
            color = styles.mutedForeground,
        )

        RegisterField(
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

        RegisterField(
            label = "Contraseña",
            value = state.password,
            onValueChange = { onAction(RegisterAction.PasswordChanged(it)) },
            error = state.fieldErrors.password,
            enabled = !state.isSubmitting,
            scrollState = scrollState,
            isImeVisible = isImeVisible,
            placeholder = "Mínimo 8 caracteres",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        )

        RegisterField(
            label = "Nombre",
            value = state.name,
            onValueChange = { onAction(RegisterAction.NameChanged(it)) },
            error = state.fieldErrors.name,
            enabled = !state.isSubmitting,
            scrollState = scrollState,
            isImeVisible = isImeVisible,
            placeholder = "Tu nombre",
        )

        RegisterField(
            label = "Apellido",
            value = state.lastName,
            onValueChange = { onAction(RegisterAction.LastNameChanged(it)) },
            error = state.fieldErrors.lastName,
            enabled = !state.isSubmitting,
            scrollState = scrollState,
            isImeVisible = isImeVisible,
            placeholder = "Tu apellido",
        )

        Text(
            text = "Establecimientos",
            style = MaterialTheme.typography.labelLarge,
            color = styles.foreground,
        )
        val selectedNames = state.selectedEstablishments.map { it.name }
        val triggerLabel = when {
            selectedNames.isEmpty() -> "Seleccionar establecimiento"
            selectedNames.size == 1 -> selectedNames.first()
            else -> "${selectedNames.size} establecimientos seleccionados"
        }
        val triggerColor = if (selectedNames.isEmpty()) styles.mutedForeground else styles.foreground
        val triggerBorder = if (state.fieldErrors.establishments != null) styles.destructive else styles.border
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, triggerBorder, RoundedCornerShape(8.dp))
                .clickable(enabled = !state.isSubmitting) {
                    onAction(RegisterAction.OpenEstablishmentSelector)
                }
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = triggerLabel,
                color = triggerColor,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "›",
                color = styles.mutedForeground,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.fieldErrors.establishments?.let { error ->
            Text(text = error, color = styles.destructive, style = MaterialTheme.typography.bodySmall)
        }

        state.submitError?.let { error ->
            Text(text = error, color = styles.destructive, style = MaterialTheme.typography.bodyMedium)
        }

        Button(
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

/**
 * Labeled KomoUI [Input] that scrolls itself fully into view when focused while the IME is open.
 * Uses the [ScrollState] pattern from CLAUDE.md (not `BringIntoViewRequester`, unreliable on iOS).
 */
@Composable
private fun RegisterField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    enabled: Boolean,
    scrollState: ScrollState,
    isImeVisible: Boolean,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val styles = MaterialTheme.styles
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var fieldScrollOffset by remember { mutableStateOf(0) }

    LaunchedEffect(isFocused, isImeVisible) {
        if (isFocused && isImeVisible) {
            scrollState.animateScrollTo(fieldScrollOffset)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                fieldScrollOffset = coordinates.boundsInRoot().top.toInt() + scrollState.value
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = styles.foreground)
        Input(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder,
            enabled = enabled,
            isError = error != null,
            singleLine = true,
            interactionSource = interactionSource,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            supportingText = error?.let {
                { Text(text = it, color = styles.destructive, style = MaterialTheme.typography.bodySmall) }
            },
        )
    }
}
