package ar.com.ingenieriaalimentacion.presentation.feature.auth.verify_email

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.success.AlSuccessTick
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import com.komoui.components.Spinner
import com.komoui.themes.styles
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun VerifyEmailScreenRoot(
    route: Route.VerifyEmail,
    viewModel: VerifyEmailViewModel = koinViewModel(parameters = { parametersOf(route) }),
    navigate: (VerifyEmailDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    VerifyEmailScreen(
        state = state,
        onAction = viewModel::onAction,
    )
}

@Composable
fun VerifyEmailScreen(
    state: VerifyEmailState,
    onAction: (VerifyEmailAction) -> Unit,
) {
    val styles = MaterialTheme.styles
    val status = state.status

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(styles.background)
            .safeDrawingPadding()
            .padding(
                start = AlTheme.spacing.xl,
                end = AlTheme.spacing.xl,
                top = AlTheme.spacing.xl,
                bottom = AlTheme.spacing.lg,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (status) {
                VerifyEmailStatus.Verifying -> Spinner()
                VerifyEmailStatus.Verified -> AlSuccessTick()
                else -> Unit
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = status.title(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = styles.foreground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = status.body(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = styles.mutedForeground,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (status != VerifyEmailStatus.Verifying) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val canRetry = status == VerifyEmailStatus.ConnectionError
                if (canRetry) {
                    AlButton(
                        onClick = { onAction(VerifyEmailAction.Retry) },
                        modifier = Modifier.fillMaxWidth(),
                        fullWidth = true,
                    ) {
                        Text("Reintentar")
                    }
                }
                AlButton(
                    onClick = { onAction(VerifyEmailAction.Continue) },
                    modifier = Modifier.fillMaxWidth(),
                    fullWidth = true,
                    variant = if (canRetry) AlButtonVariant.Secondary else AlButtonVariant.Default,
                ) {
                    Text("Volver al inicio")
                }
            }
        }
    }
}

private fun VerifyEmailStatus.title(): String = when (this) {
    VerifyEmailStatus.Verifying -> "Confirmando tu email…"
    VerifyEmailStatus.Verified -> "¡Email confirmado!"
    VerifyEmailStatus.AlreadyUsed -> "Este enlace ya fue usado"
    VerifyEmailStatus.Expired -> "El enlace venció"
    VerifyEmailStatus.InvalidLink -> "El enlace no es válido"
    VerifyEmailStatus.ConnectionError -> "No pudimos conectarnos"
}

private fun VerifyEmailStatus.body(): String = when (this) {
    VerifyEmailStatus.Verifying -> "Esto tarda unos segundos."
    VerifyEmailStatus.Verified ->
        "Ya confirmamos tu dirección de email. Si tu cuenta todavía no fue aprobada, vas a " +
            "poder ingresar cuando un administrador la habilite."
    VerifyEmailStatus.AlreadyUsed ->
        "Si ya confirmaste tu email, no tenés que hacer nada más. Si tu cuenta todavía no fue " +
            "aprobada, vas a poder ingresar cuando un administrador la habilite."
    VerifyEmailStatus.Expired ->
        "Pasó demasiado tiempo desde que te enviamos este enlace y ya no sirve para " +
            "confirmar tu email."
    VerifyEmailStatus.InvalidLink ->
        "No pudimos confirmar tu email con este enlace. Abrilo de nuevo desde el mail que " +
            "te enviamos y fijate que esté completo."
    VerifyEmailStatus.ConnectionError ->
        "Verificá tu conexión e intentá nuevamente. Tu enlace sigue siendo válido."
}
