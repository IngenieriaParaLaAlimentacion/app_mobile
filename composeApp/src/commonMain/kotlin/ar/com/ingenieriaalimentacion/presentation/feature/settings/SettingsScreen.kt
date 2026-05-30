package ar.com.ingenieriaalimentacion.presentation.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreenRoot(
    viewModel: SettingsViewModel = koinViewModel(),
    onLoggedOut: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        when (state.destination) {
            SettingsDestination.LoggedOut -> {
                onLoggedOut()
                viewModel.clearDestination()
            }
            null -> Unit
        }
    }

    SettingsScreen(
        state = state,
        onAction = viewModel::onAction,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
) {
    AlScaffold {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AlButton(
                onClick = { onAction(SettingsAction.SignOutClick) },
                enabled = !state.isSigningOut,
            ) {
                Text("Logout")
            }
        }
    }
}
