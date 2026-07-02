package ar.com.ingenieriaalimentacion.presentation.feature.measurement.uploads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.components.EmptyState
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlIconButton
import ar.com.ingenieriaalimentacion.core.design_system.components.card.AlCard
import ar.com.ingenieriaalimentacion.core.design_system.components.dialog.AlConfirmDialog
import ar.com.ingenieriaalimentacion.core.design_system.components.dialog.AlConfirmTone
import ar.com.ingenieriaalimentacion.core.design_system.components.pill.AlPill
import ar.com.ingenieriaalimentacion.core.design_system.components.pill.AlPillTone
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_delete
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun MeasurementUploadsContent(
    state: MeasurementUploadsState,
    onAction: (MeasurementUploadsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<MeasurementUploadUi?>(null) }
    val listState = rememberLazyListState()
    val currentState by rememberUpdatedState(state)

    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= currentState.uploads.lastIndex - LOAD_MORE_THRESHOLD
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && currentState.canLoadMore) onAction(MeasurementUploadsAction.LoadMore)
        }
    }

    when {
        state.isLoading -> Box(modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = AlTheme.colors.accent)
        }

        state.isEmpty -> EmptyState(
            title = "Sin mediciones todavía",
            caption = "Las mediciones que cargues aparecerán acá con su estado de sincronización.",
            modifier = modifier,
        )

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(AlTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
        ) {
            items(state.uploads, key = { it.localId }) { upload ->
                MeasurementUploadRow(
                    upload = upload,
                    onAction = onAction,
                    onRequestDelete = { pendingDelete = it },
                )
            }
            if (state.isLoadingMore) {
                item {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AlTheme.spacing.md),
                        color = AlTheme.colors.accent,
                    )
                }
            }
        }
    }

    AlConfirmDialog(
        open = pendingDelete != null,
        title = "Eliminar medición",
        description = pendingDelete?.let { "Se eliminará la medición de ${it.title}." } ?: "",
        confirmText = "Eliminar",
        cancelText = "Cancelar",
        confirmTone = AlConfirmTone.Destructive,
        onConfirm = {
            pendingDelete?.let { onAction(MeasurementUploadsAction.Delete(it.localId)) }
            pendingDelete = null
        },
        onDismiss = { pendingDelete = null },
    )
}

@Composable
private fun MeasurementUploadRow(
    upload: MeasurementUploadUi,
    onAction: (MeasurementUploadsAction) -> Unit,
    onRequestDelete: (MeasurementUploadUi) -> Unit,
) {
    AlCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
            // Identity row: name leads, status recedes, delete sits behind a quiet icon.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs),
                ) {
                    Text(
                        text = upload.title,
                        style = AlTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                        color = AlTheme.colors.textBase,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = upload.identity,
                        style = AlTheme.typography.caption,
                        color = AlTheme.colors.textMuted,
                    )
                }
                if (upload.showSpinner) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = AlTheme.colors.textMuted,
                    )
                }
                AlPill(text = upload.badgeLabel, tone = upload.badgeTone)
                if (upload.canDelete) {
                    AlIconButton(
                        icon = Res.drawable.ic_mingcute_delete,
                        contentDescription = "Eliminar",
                        onClick = { onRequestDelete(upload) },
                        tint = AlTheme.colors.textBase,
                    )
                }
            }

            // Metrics group: the clinical reading (IMC + category) and the raw
            // weight/height belong together as one block; the date is metadata
            // and recedes to the right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs),
                ) {
                    if (upload.bmiValue != null) {
                        // Synced: IMC + its category lead, weight/height support beneath.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
                        ) {
                            Text(
                                text = "IMC ${upload.bmiValue}",
                                style = AlTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                                color = AlTheme.colors.textBase,
                            )
                            upload.bmiCategory?.let { category ->
                                AlPill(
                                    text = category,
                                    tone = upload.bmiCategoryTone ?: AlPillTone.Neutral,
                                )
                            }
                        }
                        Text(
                            text = upload.measurements,
                            style = AlTheme.typography.caption,
                            color = AlTheme.colors.textMuted,
                        )
                    } else {
                        // No reading yet: the raw weight/height is the headline.
                        Text(
                            text = upload.measurements,
                            style = AlTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                            color = AlTheme.colors.textBase,
                        )
                    }
                }
                Text(
                    text = upload.measuredAtLabel,
                    style = AlTheme.typography.caption,
                    color = AlTheme.colors.textMuted,
                )
            }

            upload.errorMessage?.let {
                Text(
                    text = it,
                    style = AlTheme.typography.caption,
                    color = AlTheme.colors.danger,
                )
            }

            if (upload.canRetry) {
                AlButton(
                    onClick = { onAction(MeasurementUploadsAction.Retry(upload.localId)) },
                    variant = AlButtonVariant.Outline,
                ) {
                    Text("Reintentar")
                }
            }
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 3
