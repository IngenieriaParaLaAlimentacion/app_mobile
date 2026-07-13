package ar.com.ingenieriaalimentacion.presentation.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.EmptyState
import ar.com.ingenieriaalimentacion.core.design_system.components.badge.AlBadgeTone
import ar.com.ingenieriaalimentacion.core.design_system.components.badge.AlStatusBadge
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlCardIconButton
import ar.com.ingenieriaalimentacion.core.design_system.components.card.AlInfoCard
import ar.com.ingenieriaalimentacion.core.design_system.components.header.AlScreenHeader
import ar.com.ingenieriaalimentacion.core.design_system.components.list.AlListRow
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_alert_line
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_check_circle
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_doc_line
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_document_2_line
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_down_line
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_location_line
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
        Column(modifier = Modifier.fillMaxSize()) {
            EstablishmentTopBar(
                establishment = state.currentEstablishment,
                enabled = state.establishmentOptions.isNotEmpty(),
                onClick = { onAction(HomeAction.EstablishmentPickerOpen) },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(AlTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.lg),
            ) {
                AlScreenHeader(
                    title = state.greetingName?.let { "Hola, $it" } ?: "Hola",
                    subtitle = state.roleLabel,
                )

                SyncStatusCard(sync = state.sync, onAction = onAction)

                QuickActions(state = state, onAction = onAction)

                RecentActivity(recent = state.recent, onAction = onAction)
            }
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
private fun EstablishmentTopBar(
    establishment: EstablishmentWithStatus?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = AlTheme.spacing.lg, vertical = AlTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs, Alignment.CenterHorizontally),
    ) {
        Icon(
            imageVector = vectorResource(Res.drawable.ic_mingcute_location_line),
            contentDescription = null,
            tint = AlTheme.colors.textMuted,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = establishment?.name ?: "Elegí el establecimiento",
            style = AlTheme.typography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            color = if (establishment != null) AlTheme.colors.textBase else AlTheme.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (enabled) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_mingcute_down_line),
                contentDescription = null,
                tint = AlTheme.colors.textMuted,
                modifier = Modifier.size(16.dp),
            )
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
private fun QuickActions(state: HomeState, onAction: (HomeAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
    ) {
        QuickActionWithStat(
            modifier = Modifier.weight(1f),
            icon = Res.drawable.ic_mingcute_ruler_line,
            actionLabel = "Medición",
            statValue = state.todayMeasurements.toString(),
            statLabel = "Mediciones cargadas",
            onClick = { onAction(HomeAction.NewMeasurementClick) },
        )
        QuickActionWithStat(
            modifier = Modifier.weight(1f),
            icon = Res.drawable.ic_mingcute_user_3_line,
            actionLabel = "Sujeto",
            statValue = state.todaySubjects.toString(),
            statLabel = "Sujetos cargados",
            onClick = { onAction(HomeAction.NewSubjectClick) },
        )
        if (state.canRespondToSurveys) {
            QuickActionWithStat(
                modifier = Modifier.weight(1f),
                icon = Res.drawable.ic_mingcute_doc_line,
                actionLabel = "Encuesta",
                statValue = state.todaySurveyResponses.toString(),
                statLabel = "Encuestas respondidas",
                onClick = { onAction(HomeAction.RespondSurveyClick) },
            )
        }
    }
}

/**
 * One quick-action tile with its matching "today" stat directly beneath it, so each number reads
 * as a consequence of the action above it rather than competing with it for attention.
 */
@Composable
private fun QuickActionWithStat(
    icon: DrawableResource,
    actionLabel: String,
    statValue: String,
    statLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs),
    ) {
        AlCardIconButton(icon = icon, label = actionLabel, onClick = onClick)
        Text(statValue, style = AlTheme.typography.title, color = AlTheme.colors.textBase)
        Text(statLabel, style = AlTheme.typography.caption, color = AlTheme.colors.textMuted)
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
    RecentActivityUi.Kind.SurveyResponse -> Res.drawable.ic_mingcute_document_2_line
}

@Composable
private fun AlBadgeTone.color(): Color = when (this) {
    AlBadgeTone.Success -> AlTheme.colors.accent
    AlBadgeTone.Warning -> AlTheme.colors.warning
    AlBadgeTone.Danger -> AlTheme.colors.danger
    AlBadgeTone.Neutral -> AlTheme.colors.textMuted
}
