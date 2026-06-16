package ar.com.ingenieriaalimentacion.presentation.feature.subject.uploads

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.badge.AlStatusBadge
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SubjectUploadsScreenRoot(
    viewModel: SubjectUploadsViewModel = koinViewModel(),
    navigate: (SubjectUploadsDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    SubjectUploadsScreen(
        state = state,
        onAction = viewModel::onAction,
    )
}

@Composable
fun SubjectUploadsScreen(
    state: SubjectUploadsState,
    onAction: (SubjectUploadsAction) -> Unit,
) {
    AlScaffold {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(onSyncNow = { onAction(SubjectUploadsAction.SyncNow) })

            when {
                state.isEmpty -> EmptyState()
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.uploads, key = { it.localId }) { upload ->
                        SubjectUploadRow(upload = upload, onAction = onAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(onSyncNow: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AlTheme.spacing.md, vertical = AlTheme.spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs)) {
            Text(
                text = "Cargas pendientes",
                style = AlTheme.typography.title,
                color = AlTheme.colors.textBase,
            )
            Text(
                text = "Estado de sincronización de los sujetos cargados.",
                style = AlTheme.typography.caption,
                color = AlTheme.colors.textMuted,
            )
        }
        AlButton(
            onClick = onSyncNow,
            variant = AlButtonVariant.Outline,
        ) {
            Text("Sincronizar")
        }
    }
}

@Composable
private fun SubjectUploadRow(
    upload: SubjectUploadUi,
    onAction: (SubjectUploadsAction) -> Unit,
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

        if (upload.canRetry || upload.canDelete) {
            Row(horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                if (upload.canRetry) {
                    AlButton(
                        onClick = { onAction(SubjectUploadsAction.Retry(upload.localId)) },
                        variant = AlButtonVariant.Outline,
                    ) {
                        Text("Reintentar")
                    }
                }
                if (upload.canDelete) {
                    AlButton(
                        onClick = { onAction(SubjectUploadsAction.Delete(upload.localId)) },
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

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(AlTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
        ) {
            Text(
                text = "Sin subidas todavía",
                style = AlTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                color = AlTheme.colors.textBase,
            )
            Text(
                text = "Los sujetos que cargues aparecerán acá con su estado de sincronización.",
                style = AlTheme.typography.caption,
                color = AlTheme.colors.textMuted,
            )
        }
    }
}
