package ar.com.ingenieriaalimentacion.presentation.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreenRoot(
    viewModel: HomeViewModel = koinViewModel(),
    navigate: (HomeDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    HomeScreen(
        state = state,
        onAction = viewModel::onAction,
    )
}

@Composable
fun HomeScreen(
    state: HomeState,
    onAction: (HomeAction) -> Unit,
) {
    AlScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AlTheme.spacing.lg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = state.title,
                style = AlTheme.typography.title,
                color = AlTheme.colors.textBase,
            )
            Text(
                text = state.subtitle,
                style = AlTheme.typography.body,
                color = AlTheme.colors.textMuted,
            )
            Spacer(modifier = Modifier.height(AlTheme.spacing.md))
            AlButton(
                onClick = { onAction(HomeAction.CreateSubjectClick) },
            ) {
                Text("Cargar sujeto")
            }
            Spacer(modifier = Modifier.height(AlTheme.spacing.sm))
            AlButton(
                onClick = { onAction(HomeAction.LoadMeasurementClick) },
            ) {
                Text("Cargar medición")
            }
        }
    }
}
