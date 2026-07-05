package ar.com.ingenieriaalimentacion.presentation.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.EmptyState
import ar.com.ingenieriaalimentacion.core.design_system.components.badge.AlBadgeTone
import ar.com.ingenieriaalimentacion.core.design_system.components.badge.AlStatusBadge
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.card.AlInfoCard
import ar.com.ingenieriaalimentacion.core.design_system.components.header.AlScreenHeader
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlSelectField
import ar.com.ingenieriaalimentacion.core.design_system.components.list.AlListRow
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_alert_line
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_check_circle
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_ruler_line
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_time
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_upload_line
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_user_3_line
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    onAction: (HomeAction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    AlScaffold {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(AlTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.lg),
        ) {
            AlScreenHeader(
                title = state.greetingName?.let { "Hola, $it" } ?: "Hola",
                subtitle = state.roleLabel,
            )

            AlSelectField(
                label = "Establecimiento actual",
                value = state.currentEstablishment?.name ?: "",
                placeholder = "Elegí el establecimiento",
                enabled = state.establishmentOptions.isNotEmpty(),
                onClick = { onAction(HomeAction.EstablishmentPickerOpen) },
            )

            SyncStatusCard(sync = state.sync, onAction = onAction)

            PrimaryActions(onAction = onAction)

            TodayStrip(state = state, onAction = onAction)

            RecentActivity(recent = state.recent, onAction = onAction)
        }

        if (state.showEstablishmentPicker) {
            ModalBottomSheet(
                onDismissRequest = { onAction(HomeAction.EstablishmentPickerDismiss) },
                sheetState = sheetState,
            ) {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    Text(
                        text = "Elegí el establecimiento",
                        style = AlTheme.typography.body,
                        color = AlTheme.colors.textBase,
                        modifier = Modifier.padding(horizontal = AlTheme.spacing.md, vertical = AlTheme.spacing.sm),
                    )
                    state.establishmentOptions.forEach { establishment ->
                        AlListRow(
                            title = establishment.name,
                            subtitle = establishment.formattedAddress,
                            onClick = { onAction(HomeAction.EstablishmentSelected(establishment)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatusCard(sync: SyncStatusUi, onAction: (HomeAction) -> Unit) {
    when (sync) {
        is SyncStatusUi.AllSynced -> AlInfoCard(
            title = "Todo sincronizado",
            description = sync.lastSyncedLabel,
            leadingIcon = Res.drawable.ic_mingcute_check_circle,
            leadingIconTint = AlTheme.colors.accent,
        )

        SyncStatusUi.OfflineClean -> AlInfoCard(
            title = "Sin conexión",
            description = "Todo guardado en el dispositivo",
            leadingIcon = Res.drawable.ic_mingcute_check_circle,
            leadingIconTint = AlTheme.colors.textMuted,
        )

        is SyncStatusUi.Syncing -> AlInfoCard(
            title = "Sincronizando…",
            description = "${sync.count} en proceso",
            leadingIcon = Res.drawable.ic_mingcute_upload_line,
            leadingIconTint = AlTheme.colors.accent,
        )

        is SyncStatusUi.PendingOnline -> AlInfoCard(
            title = "${sync.count} sin subir",
            description = "Tocá para sincronizar",
            leadingIcon = Res.drawable.ic_mingcute_time,
            leadingIconTint = AlTheme.colors.warning,
            trailing = {
                AlButton(
                    onClick = { onAction(HomeAction.SyncClick) },
                    variant = AlButtonVariant.Outline,
                ) { Text("Sincronizar") }
            },
        )

        is SyncStatusUi.PendingOffline -> AlInfoCard(
            title = "Sin conexión",
            description = "${sync.count} se subirán al reconectar",
            leadingIcon = Res.drawable.ic_mingcute_time,
            leadingIconTint = AlTheme.colors.textMuted,
        )

        is SyncStatusUi.Failed -> AlInfoCard(
            title = "${sync.failedCount} con error",
            description = if (sync.pendingCount > 0) {
                "${sync.pendingCount} sin subir además"
            } else {
                "Tocá Revisar para ver el detalle"
            },
            leadingIcon = Res.drawable.ic_mingcute_alert_line,
            leadingIconTint = AlTheme.colors.danger,
            trailing = {
                AlButton(
                    onClick = { onAction(HomeAction.SeeAllActivityClick) },
                    variant = AlButtonVariant.Outline,
                ) { Text("Revisar") }
            },
        )
    }
}

@Composable
private fun PrimaryActions(onAction: (HomeAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
        AlButton(
            onClick = { onAction(HomeAction.NewMeasurementClick) },
            fullWidth = true,
            variant = AlButtonVariant.Default,
        ) {
            ButtonIcon(Res.drawable.ic_mingcute_ruler_line)
            Spacer(Modifier.width(AlTheme.spacing.sm))
            Text("Nueva medición")
        }
        AlButton(
            onClick = { onAction(HomeAction.NewSubjectClick) },
            fullWidth = true,
            variant = AlButtonVariant.Outline,
        ) {
            ButtonIcon(Res.drawable.ic_mingcute_user_3_line)
            Spacer(Modifier.width(AlTheme.spacing.sm))
            Text("Nuevo sujeto")
        }
    }
}

@Composable
private fun ButtonIcon(icon: DrawableResource) {
    Icon(
        imageVector = vectorResource(icon),
        contentDescription = null,
        tint = LocalContentColor.current,
        modifier = Modifier.size(18.dp),
    )
}

@Composable
private fun TodayStrip(state: HomeState, onAction: (HomeAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
        SectionLabel("HOY")
        Row(horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
            StatCard(
                value = state.todayMeasurements.toString(),
                label = "Mediciones",
                container = AlTheme.colors.surfaceMuted,
                valueColor = AlTheme.colors.textBase,
            )
            StatCard(
                value = state.todaySubjects.toString(),
                label = "Sujetos",
                container = AlTheme.colors.surfaceMuted,
                valueColor = AlTheme.colors.textBase,
            )
            val hasAlerts = state.todayAlerts > 0
            StatCard(
                value = state.todayAlerts.toString(),
                label = "Alertas",
                container = if (hasAlerts) AlTheme.colors.dangerContainer else AlTheme.colors.surfaceMuted,
                valueColor = if (hasAlerts) AlTheme.colors.danger else AlTheme.colors.textBase,
                onClick = if (hasAlerts) {
                    { onAction(HomeAction.AlertsClick) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun RowScope.StatCard(
    value: String,
    label: String,
    container: Color,
    valueColor: Color,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(AlTheme.corners.lg))
            .background(container)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = AlTheme.spacing.md, horizontal = AlTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs),
    ) {
        Text(value, style = AlTheme.typography.title, color = valueColor)
        Text(label, style = AlTheme.typography.caption, color = AlTheme.colors.textMuted)
    }
}

@Composable
private fun RecentActivity(recent: List<RecentActivityUi>, onAction: (HomeAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("ACTIVIDAD RECIENTE")
            Text(
                text = "Ver todo",
                style = AlTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                color = AlTheme.colors.textBase,
                modifier = Modifier.clickable { onAction(HomeAction.SeeAllActivityClick) },
            )
        }
        if (recent.isEmpty()) {
            EmptyState(
                title = "Sin actividad",
                caption = "Tus cargas aparecerán acá",
                modifier = Modifier.padding(vertical = AlTheme.spacing.lg),
            )
        } else {
            recent.forEach { item ->
                AlListRow(
                    title = item.title,
                    subtitle = item.subtitle,
                    value = item.value,
                    leadingIcon = item.kind.icon(),
                    leadingIconTint = item.badgeTone.color(),
                    onClick = { onAction(HomeAction.SeeAllActivityClick) },
                    showChevron = false,
                    horizontalPadding = 0.dp,
                    trailing = {
                        item.badgeText?.let { AlStatusBadge(text = it, tone = item.badgeTone) }
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = AlTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
        color = AlTheme.colors.textMuted,
    )
}

private fun RecentActivityUi.Kind.icon(): DrawableResource = when (this) {
    RecentActivityUi.Kind.Subject -> Res.drawable.ic_mingcute_user_3_line
    RecentActivityUi.Kind.Measurement -> Res.drawable.ic_mingcute_ruler_line
}

@Composable
private fun AlBadgeTone.color(): Color = when (this) {
    AlBadgeTone.Success -> AlTheme.colors.accent
    AlBadgeTone.Warning -> AlTheme.colors.warning
    AlBadgeTone.Danger -> AlTheme.colors.danger
    AlBadgeTone.Neutral -> AlTheme.colors.textMuted
}
