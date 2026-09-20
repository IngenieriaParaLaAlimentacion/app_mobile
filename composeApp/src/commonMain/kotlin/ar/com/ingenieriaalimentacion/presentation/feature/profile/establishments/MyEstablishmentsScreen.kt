package ar.com.ingenieriaalimentacion.presentation.feature.profile.establishments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.banner.AlBannerVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlIconButton
import ar.com.ingenieriaalimentacion.core.design_system.components.card.AlInfoCard
import ar.com.ingenieriaalimentacion.core.design_system.components.dialog.AlConfirmDialog
import ar.com.ingenieriaalimentacion.core.design_system.components.pill.AlPill
import ar.com.ingenieriaalimentacion.core.design_system.components.pill.AlPillTone
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.toast.AlToast
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.presentation.navigation.ESTABLISHMENT_REQUESTED_RESULT
import ar.com.ingenieriaalimentacion.presentation.navigation.RefreshOnNavResult
import com.komoui.components.Spinner
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_add_line
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_exit_door_line
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MyEstablishmentsScreenRoot(
    savedStateHandle: SavedStateHandle?,
    viewModel: MyEstablishmentsViewModel = koinViewModel(),
    navigate: (MyEstablishmentsDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    // Refresh when RequestEstablishment pops after a successful submission, so the newly
    // requested access appears. Both screens live on the outer nav, so this screen's own
    // entry is the one RequestEstablishment writes to. See CLAUDE.md "Navigate-back-with-result".
    RefreshOnNavResult(
        savedStateHandle = savedStateHandle,
        key = ESTABLISHMENT_REQUESTED_RESULT,
    ) {
        viewModel.refresh()
    }

    MyEstablishmentsScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun MyEstablishmentsScreen(
    state: MyEstablishmentsState,
    onAction: (MyEstablishmentsAction) -> Unit,
) {
    AlScaffold {
        Column(modifier = Modifier.fillMaxSize()) {
            AlBackTopAppBar(
                onBackClick = { onAction(MyEstablishmentsAction.BackClick) },
                title = "Mis establecimientos",
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> Spinner(
                        modifier = Modifier.align(Alignment.Center),
                        size = 28.dp,
                    )

                    state.loadError -> Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.md),
                    ) {
                        Text(
                            text = "No se pudieron cargar los establecimientos.",
                            style = AlTheme.typography.body,
                            color = AlTheme.colors.textMuted,
                        )
                        AlButton(onClick = { onAction(MyEstablishmentsAction.RetryClick) }) {
                            Text("Reintentar")
                        }
                    }

                    else -> MyEstablishmentsContent(
                        establishments = state.establishments,
                        onRequestAccess = { onAction(MyEstablishmentsAction.RequestAccessClick) },
                        onWithdrawClick = { id -> onAction(MyEstablishmentsAction.WithdrawClick(id)) },
                    )
                }

                AlToast(
                    message = state.successMessage ?: state.withdrawError?.toUserMessage(),
                    variant = if (state.successMessage != null) AlBannerVariant.Success else AlBannerVariant.Danger,
                    onDismiss = { onAction(MyEstablishmentsAction.ToastDismissed) },
                )
            }
        }

        AlConfirmDialog(
            open = state.pendingWithdraw != null,
            title = "Darse de baja",
            description = "¿Seguro que querés darte de baja de " +
                "${state.pendingWithdraw?.name.orEmpty()}? Vas a perder el acceso a sus datos.",
            confirmText = "Darme de baja",
            loading = state.isWithdrawing,
            onConfirm = { onAction(MyEstablishmentsAction.ConfirmWithdraw) },
            onDismiss = { onAction(MyEstablishmentsAction.DismissWithdrawConfirm) },
        )
    }
}

@Composable
private fun MyEstablishmentsContent(
    establishments: List<EstablishmentWithStatus>,
    onRequestAccess: () -> Unit,
    onWithdrawClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AlTheme.spacing.lg, vertical = AlTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.lg),
    ) {
        if (establishments.isEmpty()) {
            Text(
                text = "Todavía no tenés establecimientos aceptados.",
                style = AlTheme.typography.body,
                color = AlTheme.colors.textMuted,
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
            ) {
                establishments.forEach { establishment ->
                    AlInfoCard(
                        title = establishment.name,
                        description = establishment.formattedAddress.normalizeAddress(),
                        trailing = {
                            EstablishmentRowTrailing(
                                status = establishment.status,
                                onWithdrawClick = { onWithdrawClick(establishment.id) },
                            )
                        },
                    )
                }
            }
        }

        AlInfoCard(
            title = "Solicitar acceso",
            leadingIcon = Res.drawable.ic_mingcute_add_line,
            onClick = onRequestAccess,
        )
    }
}

/** Status pill plus, for approved/pending relations only, the "darse de baja" action —
 *  rejected relations have nothing left to withdraw from. */
@Composable
private fun EstablishmentRowTrailing(
    status: EstablishmentStatus,
    onWithdrawClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs),
    ) {
        EstablishmentStatusPill(status)
        if (status == EstablishmentStatus.Approved || status == EstablishmentStatus.Pending) {
            AlIconButton(
                icon = Res.drawable.ic_mingcute_exit_door_line,
                contentDescription = "Darse de baja",
                tint = AlTheme.colors.danger,
                onClick = onWithdrawClick,
            )
        }
    }
}

/** Status pill shown at the end of an establishment card. Approved (and the
 *  defensive Unknown) render nothing — only in-flight/rejected states get a pill.
 *  Withdrawn/Revoked never reach here: [ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyEstablishmentsUseCase]
 *  filters them out of the list entirely. */
@Composable
private fun EstablishmentStatusPill(status: EstablishmentStatus) {
    when (status) {
        EstablishmentStatus.Pending -> AlPill(text = "Pendiente", tone = AlPillTone.Warning)
        EstablishmentStatus.Rejected -> AlPill(text = "Rechazado", tone = AlPillTone.Danger)
        EstablishmentStatus.Approved,
        EstablishmentStatus.Withdrawn,
        EstablishmentStatus.Revoked,
        EstablishmentStatus.Unknown,
        -> Unit
    }
}

/** Collapses the multi-line `formatted_address` into a single clean line, dropping
 *  embedded newlines/indentation; returns null when blank so the card hides it. */
private fun String.normalizeAddress(): String? =
    replace(Regex("\\s+"), " ").trim().takeIf { it.isNotBlank() }
