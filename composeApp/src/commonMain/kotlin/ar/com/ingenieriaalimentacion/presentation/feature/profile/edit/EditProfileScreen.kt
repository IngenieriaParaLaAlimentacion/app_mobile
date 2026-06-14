package ar.com.ingenieriaalimentacion.presentation.feature.profile.edit

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
import ar.com.ingenieriaalimentacion.presentation.feature.profile.view.roleLabel
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun EditProfileScreenRoot(
    route: Route.EditProfile,
    viewModel: EditProfileViewModel = koinViewModel { parametersOf(route) },
    navigate: (EditProfileDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    EditProfileScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun EditProfileScreen(
    state: EditProfileState,
    onAction: (EditProfileAction) -> Unit,
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
                onBackClick = { onAction(EditProfileAction.BackClick) },
                title = "Editar perfil",
                enabled = !state.isSaving,
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
                    verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.md),
                ) {
                    AlInput(
                        label = "Nombre",
                        value = state.name,
                        onValueChange = { onAction(EditProfileAction.NameChanged(it)) },
                        enabled = !state.isSaving,
                        scrollState = scrollState,
                        isImeVisible = isImeVisible,
                        placeholder = "Tu nombre",
                    )

                    AlInput(
                        label = "Apellido",
                        value = state.lastName,
                        onValueChange = { onAction(EditProfileAction.LastNameChanged(it)) },
                        enabled = !state.isSaving,
                        scrollState = scrollState,
                        isImeVisible = isImeVisible,
                        placeholder = "Tu apellido",
                    )

                    ReadOnlyRow(label = "Email", value = state.email)
                    ReadOnlyRow(label = "Rol", value = roleLabel(state.role))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = AlTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.saveError?.let { error ->
                        Text(
                            text = error.toUserMessage(),
                            style = AlTheme.typography.body,
                            color = AlTheme.colors.danger,
                        )
                    }

                    AlButton(
                        onClick = { onAction(EditProfileAction.SaveClick) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.canSave,
                        loading = state.isSaving,
                        fullWidth = true,
                    ) {
                        Text("Guardar")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs)) {
        Text(
            text = label,
            style = AlTheme.typography.caption,
            color = AlTheme.colors.textMuted,
        )
        Text(
            text = value,
            style = AlTheme.typography.body,
            color = AlTheme.colors.textMuted,
        )
    }
}
