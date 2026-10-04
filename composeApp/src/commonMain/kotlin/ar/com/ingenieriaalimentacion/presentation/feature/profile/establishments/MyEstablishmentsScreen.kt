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
import ar.com.ingenieriaalimentacion.core.design_system.components.banner.AlInlineBanner
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
import ar.com.ingenieriaalimentacion.domain.model.auth.EstablishmentLinkMode
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithdrawalError
import ar.com.ingenieriaalimentacion.presentation.navigation.ESTABLISHMENT_REQUESTED_RESULT
import ar.com.ingenieriaalimentacion.presentation.navigation.RefreshOnNavResult
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import com.komoui.components.Spinner
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_add_line
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_exit_door_line
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MyEstablishmentsScreenRoot(
    route: Route.MyEstablishments,
    savedStateHandle: SavedStateHandle?,
    viewModel: MyEstablishmentsViewModel = koinViewModel { parametersOf(route) },
    navigate: (MyEstablishmentsDestination) -> Unit,
    onEstablishmentsChanged: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.establishmentsChanged) {
        if (state.establishmentsChanged) onEstablishmentsChanged()
    }

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
        viewModel.onEstablishmentsRequested()
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
                        mode = state.mode,
                        establishments = state.establishments,
                        withdrawError = state.withdrawError,
                        onRequestAccess = { onAction(MyEstablishmentsAction.RequestAccessClick) },
                        onWithdrawClick = { id -> onAction(MyEstablishmentsAction.WithdrawClick(id)) },
                        onDismissError = { onAction(MyEstablishmentsAction.ToastDismissed) },
                    )
                }

                AlToast(
                    message = state.successMessage,
                    onDismiss = { onAction(MyEstablishmentsAction.ToastDismissed) },
                )
            }
        }

        val copy = state.mode.linkCopy()
        AlConfirmDialog(
            open = state.pendingWithdraw != null,
            title = copy.withdrawTitle,
            description = "¿Seguro que querés ${copy.withdrawVerb} de " +
                "${state.pendingWithdraw?.name.orEmpty()}? Vas a perder el acceso a sus datos.",
            confirmText = copy.withdrawConfirm,
            loading = state.isWithdrawing,
            onConfirm = { onAction(MyEstablishmentsAction.ConfirmWithdraw) },
            onDismiss = { onAction(MyEstablishmentsAction.DismissWithdrawConfirm) },
        )
    }
}

@Composable
private fun MyEstablishmentsContent(
    mode: EstablishmentLinkMode,
    establishments: List<EstablishmentWithStatus>,
    withdrawError: EstablishmentWithdrawalError?,
    onRequestAccess: () -> Unit,
    onWithdrawClick: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AlTheme.spacing.lg, vertical = AlTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.lg),
    ) {
        if (withdrawError != null) {
            AlInlineBanner(
                message = withdrawError.toUserMessage(),
                variant = AlBannerVariant.Danger,
                onDismiss = onDismissError,
            )
        }

        val copy = mode.linkCopy()
        if (establishments.isEmpty()) {
            Text(
                text = copy.emptyText,
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
                                withdrawLabel = copy.withdrawTitle,
                                onWithdrawClick = { onWithdrawClick(establishment.id) },
                            )
                        },
                    )
                }
            }
        }

        AlInfoCard(
            title = copy.linkCta,
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
    withdrawLabel: String,
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
                contentDescription = withdrawLabel,
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

/** Role-dependent wording: an admin subscribes/unsubscribes, everyone else requests
 *  access and "se da de baja". */
private data class LinkCopy(
    val linkCta: String,
    val emptyText: String,
    val withdrawTitle: String,
    val withdrawVerb: String,
    val withdrawConfirm: String,
)

private fun EstablishmentLinkMode.linkCopy(): LinkCopy = when (this) {
    EstablishmentLinkMode.Subscribe -> LinkCopy(
        linkCta = "Suscribirme",
        emptyText = "Todavía no estás suscripto a ningún establecimiento.",
        withdrawTitle = "Desuscribirme",
        withdrawVerb = "desuscribirte",
        withdrawConfirm = "Desuscribirme",
    )
    EstablishmentLinkMode.RequestAccess -> LinkCopy(
        linkCta = "Solicitar acceso",
        emptyText = "Todavía no tenés establecimientos aceptados.",
        withdrawTitle = "Darse de baja",
        withdrawVerb = "darte de baja",
        withdrawConfirm = "Darme de baja",
    )
}
