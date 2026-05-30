package ar.com.ingenieriaalimentacion.presentation.feature.auth.forgot_password

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
import androidx.compose.foundation.verticalScroll
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
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
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
            .imePadding(),
    ) {
        AlBackTopAppBar(
            onBackClick = { onAction(ForgotPasswordAction.Back) },
            enabled = !state.isSubmitting,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            if (state.isSent) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(top = AlTheme.spacing.md, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AlScreenHeader(
                        title = "Revisá tu email",
                        subtitle = "Si existe una cuenta con ese email, te enviamos un enlace " +
                            "para recuperar tu contraseña. Revisá tu bandeja de entrada y la carpeta de spam.",
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AlButton(
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
                        .padding(top = AlTheme.spacing.md, bottom = 16.dp),
                ) {
                    AlScreenHeader(
                        title = "Recuperar contraseña",
                        subtitle = "Ingresá tu email y te enviaremos un enlace para recuperar tu contraseña.",
                    )

                    Spacer(Modifier.height(AlTheme.spacing.lg))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.md),
                    ) {
                        AlInput(
                            label = "Email",
                            value = state.email,
                            onValueChange = { onAction(ForgotPasswordAction.EmailChanged(it)) },
                            error = state.fieldError,
                            enabled = !state.isSubmitting,
                            scrollState = scrollState,
                            isImeVisible = isImeVisible,
                            placeholder = "tu@email.com",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        )
                    }
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
                    AlButton(
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
}
