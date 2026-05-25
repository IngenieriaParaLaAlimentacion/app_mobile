package ar.com.ingenieriaalimentacion.presentation.feature.auth.forgot_password

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.komoui.components.Button
import com.komoui.components.Input
import com.komoui.themes.styles
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ForgotPasswordScreenRoot(
    viewModel: ForgotPasswordViewModel = koinViewModel(),
    navigate: (ForgotPasswordDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    ForgotPasswordScreen(
        state = state,
        onAction = viewModel::onAction,
    )
}

@Composable
fun ForgotPasswordScreen(
    state: ForgotPasswordState,
    onAction: (ForgotPasswordAction) -> Unit,
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
            .padding(horizontal = 24.dp),
    ) {
        if (state.isSent) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(top = 24.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Revisá tu email",
                    style = MaterialTheme.typography.headlineMedium,
                    color = styles.foreground,
                )
                Text(
                    text = "Si existe una cuenta con ese email, te enviamos un enlace " +
                        "para recuperar tu contraseña. Revisá tu bandeja de entrada y la carpeta de spam.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = styles.mutedForeground,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { onAction(ForgotPasswordAction.Back) },
                    modifier = Modifier.fillMaxWidth(),
                    fullWidth = true,
                ) {
                    Text("Volver")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(top = 24.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Recuperar contraseña",
                    style = MaterialTheme.typography.headlineMedium,
                    color = styles.foreground,
                )
                Text(
                    text = "Ingresá tu email y te enviaremos un enlace para recuperar tu contraseña.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = styles.mutedForeground,
                )

                ForgotPasswordField(
                    label = "Email",
                    value = state.email,
                    onValueChange = { onAction(ForgotPasswordAction.EmailChanged(it)) },
                    error = state.fieldError,
                    enabled = !state.isSubmitting,
                    scrollState = scrollState,
                    isImeVisible = isImeVisible,
                    placeholder = "tu@email.com",
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.submitError?.let { error ->
                    Text(
                        text = error,
                        color = styles.destructive,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(
                    onClick = { onAction(ForgotPasswordAction.Submit) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.canSubmit,
                    loading = state.isSubmitting,
                    fullWidth = true,
                ) {
                    Text("Enviar enlace")
                }
            }
        }
    }
}

@Composable
private fun ForgotPasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    enabled: Boolean,
    scrollState: ScrollState,
    isImeVisible: Boolean,
    placeholder: String = "",
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            supportingText = error?.let {
                { Text(text = it, color = styles.destructive, style = MaterialTheme.typography.bodySmall) }
            },
        )
    }
}
