package ar.com.ingenieriaalimentacion.presentation.feature.measurement.uploads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.components.EmptyState
import ar.com.ingenieriaalimentacion.core.design_system.components.badge.AlStatusBadge
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.pill.AlPill
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme

@Composable
fun MeasurementUploadsContent(
    state: MeasurementUploadsState,
    onAction: (MeasurementUploadsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> Box(modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = AlTheme.colors.accent)
        }

        state.isEmpty -> EmptyState(
            title = "Sin mediciones todavía",
            caption = "Las mediciones que cargues aparecerán acá con su estado de sincronización.",
            modifier = modifier,
        )

        else -> LazyColumn(modifier.fillMaxSize()) {
            items(state.uploads, key = { it.localId }) { upload ->
                MeasurementUploadRow(upload = upload, onAction = onAction)
            }
        }
    }
}

@Composable
private fun MeasurementUploadRow(
    upload: MeasurementUploadUi,
    onAction: (MeasurementUploadsAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AlTheme.spacing.md, vertical = AlTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
    ) {
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
                    text = upload.subtitle,
                    style = AlTheme.typography.caption,
                    color = AlTheme.colors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (upload.showSpinner) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = AlTheme.colors.textMuted,
                )
            }
            AlStatusBadge(text = upload.badgeLabel, tone = upload.badgeTone)
        }

        upload.bmiLabel?.let {
            Text(
                text = it,
                style = AlTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                color = AlTheme.colors.textBase,
            )
        }

        upload.alerts.forEach { alert ->
            Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs)) {
                AlPill(text = alert.label, tone = alert.tone)
                Text(
                    text = alert.detail,
                    style = AlTheme.typography.caption,
                    color = AlTheme.colors.textMuted,
                )
            }
        }

        if (upload.canRetry || upload.canDelete) {
            Row(horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                if (upload.canRetry) {
                    AlButton(
                        onClick = { onAction(MeasurementUploadsAction.Retry(upload.localId)) },
                        variant = AlButtonVariant.Outline,
                    ) {
                        Text("Reintentar")
                    }
                }
                if (upload.canDelete) {
                    AlButton(
                        onClick = { onAction(MeasurementUploadsAction.Delete(upload.localId)) },
                        variant = AlButtonVariant.Destructive,
                    ) {
                        Text("Eliminar")
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AlTheme.borders.thin)
                .background(AlTheme.colors.border),
        )
    }
}
