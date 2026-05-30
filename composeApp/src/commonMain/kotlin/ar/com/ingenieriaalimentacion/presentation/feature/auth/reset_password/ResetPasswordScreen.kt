package ar.com.ingenieriaalimentacion.presentation.feature.auth.reset_password

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.header.AlScreenHeader
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlPasswordInput
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import com.komoui.themes.styles
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

@Composable
fun ResetPasswordScreenRoot(
    route: Route.ResetPassword,
    viewModel: ResetPasswordViewModel = koinViewModel(parameters = { parametersOf(route) }),
    navigate: (ResetPasswordDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    ResetPasswordScreen(
        state = state,
        onAction = viewModel::onAction,
    )
}

@Composable
fun ResetPasswordScreen(
    state: ResetPasswordState,
    onAction: (ResetPasswordAction) -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(top = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AlScreenHeader(
                title = "Nueva contraseña",
                subtitle = "Ingresá una nueva contraseña para tu cuenta. " +
                    "Una vez confirmada, vas a poder ingresar con ella.",
            )

            AlPasswordInput(
                label = "Nueva contraseña",
                value = state.newPassword,
                onValueChange = { onAction(ResetPasswordAction.NewPasswordChanged(it)) },
                error = state.fieldErrors.newPassword,
                enabled = !state.isSubmitting,
                scrollState = scrollState,
                isImeVisible = isImeVisible,
                placeholder = "Mínimo 8 caracteres",
            )

            AlPasswordInput(
                label = "Repetir contraseña",
                value = state.confirmPassword,
                onValueChange = { onAction(ResetPasswordAction.ConfirmPasswordChanged(it)) },
                error = state.fieldErrors.confirmPassword,
                enabled = !state.isSubmitting,
                scrollState = scrollState,
                isImeVisible = isImeVisible,
                placeholder = "Volvé a ingresarla",
            )
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
                onClick = { onAction(ResetPasswordAction.Submit) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSubmit,
                loading = state.isSubmitting,
                fullWidth = true,
            ) {
                Text("Guardar contraseña")
            }
        }
    }
}
