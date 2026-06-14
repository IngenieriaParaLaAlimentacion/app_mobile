package ar.com.ingenieriaalimentacion.presentation.feature.profile.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.app.LocalOuterNavController
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.dialog.AlConfirmDialog
import ar.com.ingenieriaalimentacion.core.design_system.components.list.AlListRow
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.profile.Profile
import ar.com.ingenieriaalimentacion.presentation.navigation.PROFILE_UPDATED_RESULT
import ar.com.ingenieriaalimentacion.presentation.navigation.RefreshOnNavResult
import com.komoui.components.Avatar
import com.komoui.components.Spinner
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_bank_line
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_edit
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_exit_line
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_mail_line
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ProfileScreenRoot(
    viewModel: ProfileViewModel = koinViewModel(),
    navigate: (ProfileDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    // Refresh when EditProfile pops after a successful PATCH. EditProfile is pushed on
    // the outer nav, so its result lands on the AuthedShell (outer) entry — read that,
    // not the Profile tab's inner entry. See CLAUDE.md "Navigate-back-with-result".
    RefreshOnNavResult(
        savedStateHandle = LocalOuterNavController.current.currentBackStackEntry?.savedStateHandle,
        key = PROFILE_UPDATED_RESULT,
    ) {
        viewModel.refresh()
    }

    ProfileScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun ProfileScreen(
    state: ProfileState,
    onAction: (ProfileAction) -> Unit,
) {
    AlScaffold {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.profile == null -> Spinner(
                    modifier = Modifier.align(Alignment.Center),
                    size = 28.dp,
                )

                state.loadError && state.profile == null -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.md),
                ) {
                    Text(
                        text = "No se pudo cargar el perfil.",
                        style = AlTheme.typography.body,
                        color = AlTheme.colors.textMuted,
                    )
                    AlButton(onClick = { onAction(ProfileAction.RetryClick) }) {
                        Text("Reintentar")
                    }
                }

                state.profile != null -> ProfileContent(
                    profile = state.profile,
                    showSignOutConfirm = state.showSignOutConfirm,
                    isSigningOut = state.isSigningOut,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun ProfileContent(
    profile: Profile,
    showSignOutConfirm: Boolean,
    isSigningOut: Boolean,
    onAction: (ProfileAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = AlTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.lg),
    ) {
        ProfileHeader(profile = profile)

        AccountSection(
            email = profile.email,
            onEdit = { onAction(ProfileAction.EditClick) },
            onMyEstablishments = if (profile.establishments != null) {
                { onAction(ProfileAction.MyEstablishmentsClick) }
            } else {
                null
            },
        )

        SecuritySection(onSignOut = { onAction(ProfileAction.SignOutClick) })
    }

    AlConfirmDialog(
        open = showSignOutConfirm,
        title = "Cerrar sesión",
        description = "¿Seguro que querés cerrar sesión? Vas a tener que volver a iniciar sesión para usar la app.",
        confirmText = "Cerrar sesión",
        loading = isSigningOut,
        onConfirm = { onAction(ProfileAction.ConfirmSignOut) },
        onDismiss = { onAction(ProfileAction.DismissSignOutConfirm) },
    )
}

@Composable
private fun SecuritySection(onSignOut: () -> Unit) {
    ProfileSection(title = "Seguridad") {
        AlListRow(
            title = "Cerrar sesión",
            titleColor = AlTheme.colors.danger,
            leadingIcon = Res.drawable.ic_mingcute_exit_line,
            leadingIconTint = AlTheme.colors.danger,
            onClick = onSignOut,
            showChevron = false,
            showDivider = false,
        )
    }
}

@Composable
private fun ProfileHeader(profile: Profile) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AlTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.md),
    ) {
        Avatar(
            model = null,
            size = 96.dp,
            fallbackText = initials(profile.name, profile.lastName),
            contentDescription = "${profile.name} ${profile.lastName}",
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs),
        ) {
            Text(
                text = "${profile.name} ${profile.lastName}",
                style = AlTheme.typography.title,
                color = AlTheme.colors.textBase,
                textAlign = TextAlign.Center,
            )
            Text(
                text = roleLabel(profile.role),
                style = AlTheme.typography.body,
                color = AlTheme.colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AccountSection(
    email: String,
    onEdit: () -> Unit,
    onMyEstablishments: (() -> Unit)?,
) {
    ProfileSection(title = "Mi cuenta") {
        AlListRow(
            title = "Email",
            value = email,
            leadingIcon = Res.drawable.ic_mingcute_mail_line,
        )
        AlListRow(
            title = "Editar perfil",
            leadingIcon = Res.drawable.ic_mingcute_edit,
            onClick = onEdit,
            showDivider = onMyEstablishments != null,
        )
        if (onMyEstablishments != null) {
            AlListRow(
                title = "Mis establecimientos",
                leadingIcon = Res.drawable.ic_mingcute_bank_line,
                onClick = onMyEstablishments,
                showDivider = false,
            )
        }
    }
}

/**
 * A muted section label above a flush stack of [AlListRow]s, giving the screen a
 * standard grouped-list layout shared by "Mi cuenta" and "Mis establecimientos".
 */
@Composable
private fun ProfileSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            modifier = Modifier.padding(
                horizontal = AlTheme.spacing.md,
                vertical = AlTheme.spacing.sm,
            ),
            style = AlTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = AlTheme.colors.textMuted,
        )
        content()
    }
}

/** First letters of name + last name, uppercased, for the avatar fallback. */
private fun initials(name: String, lastName: String): String {
    val first = name.trim().firstOrNull()?.uppercaseChar()
    val second = lastName.trim().firstOrNull()?.uppercaseChar()
    return listOfNotNull(first, second).joinToString("").ifEmpty { "?" }
}
