package ar.com.ingenieriaalimentacion.presentation.feature.get_started

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun GetStartedScreenRoot(
    viewModel: GetStartedViewModel = koinViewModel(),
    navigate: (GetStartedDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    GetStartedScreen(
        state = state,
        onAction = viewModel::onAction,
    )
}

@Composable
fun GetStartedScreen(
    state: GetStartedState,
    onAction: (GetStartedAction) -> Unit,
) {
    AlScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AlTheme.spacing.lg)
                .padding(bottom = AlTheme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = state.title,
                    style = AlTheme.typography.title,
                    color = AlTheme.colors.textBase,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(AlTheme.spacing.sm))
                Text(
                    text = state.subtitle,
                    style = AlTheme.typography.body,
                    color = AlTheme.colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
            AlButton(
                onClick = { onAction(GetStartedAction.SignUpClick) },
                modifier = Modifier.fillMaxWidth(),
                fullWidth = true,
            ) {
                Text(state.signUpLabel)
            }
            Spacer(modifier = Modifier.height(AlTheme.spacing.sm))
            AlButton(
                onClick = { onAction(GetStartedAction.LogInClick) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.isLoginEnabled,
                fullWidth = true,
                variant = AlButtonVariant.Secondary,
            ) {
                Text(state.logInLabel)
            }
        }
    }
}
