package ar.com.ingenieriaalimentacion.presentation.feature.measurement.uploads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.components.EmptyState
import ar.com.ingenieriaalimentacion.core.design_system.components.badge.AlStatusBadge
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
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_check_line
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_delete
import org.jetbrains.compose.resources.vectorResource

@Composable
fun MeasurementUploadsContent(
    state: MeasurementUploadsState,
    onAction: (MeasurementUploadsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<MeasurementUploadUi?>(null) }

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
                if (upload.isSynced) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs),
                    ) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.ic_mingcute_check_line),
                            contentDescription = null,
                            tint = AlTheme.colors.accent,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = upload.badgeLabel,
                            style = AlTheme.typography.caption,
                            color = AlTheme.colors.textMuted,
                        )
                    }
                } else {
                    AlStatusBadge(text = upload.badgeLabel, tone = upload.badgeTone)
                }
                if (upload.canDelete) {
                    AlIconButton(
                        icon = Res.drawable.ic_mingcute_delete,
                        contentDescription = "Eliminar",
                        onClick = { onRequestDelete(upload) },
                        tint = AlTheme.colors.danger,
                    )
                }
            }

            // Clinical anchor: when synced with a BMI, the reading + its category lead;
            // raw inputs and the date recede on the right.
            if (upload.bmiValue != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = upload.measurements,
                            style = AlTheme.typography.caption,
                            color = AlTheme.colors.textMuted,
                        )
                        Text(
                            text = upload.measuredAtLabel,
                            style = AlTheme.typography.caption,
                            color = AlTheme.colors.textMuted,
                        )
                    }
                }
            } else {
                Text(
                    text = "${upload.measurements} · ${upload.measuredAtLabel}",
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
