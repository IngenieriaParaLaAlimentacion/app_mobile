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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.EmptyState
import ar.com.ingenieriaalimentacion.core.design_system.components.badge.AlStatusBadge
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlIconButton
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_delete
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SubjectUploadsContentRoot(
    modifier: Modifier = Modifier,
    viewModel: SubjectUploadsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SubjectUploadsContent(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun SubjectUploadsContent(
    state: SubjectUploadsState,
    onAction: (SubjectUploadsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentState by rememberUpdatedState(state)

    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= currentState.uploads.lastIndex - LOAD_MORE_THRESHOLD
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && currentState.canLoadMore) onAction(SubjectUploadsAction.LoadMore)
        }
    }

    when {
        state.isLoading -> Box(modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = AlTheme.colors.accent)
        }

        state.isEmpty -> EmptyState(
            title = "Sin cargas todavía",
            caption = "Los sujetos que cargues aparecerán acá con su estado de sincronización.",
            modifier = modifier,
        )

        else -> LazyColumn(modifier.fillMaxSize(), state = listState) {
            items(state.uploads, key = { it.localId }) { upload ->
                SubjectUploadRow(upload = upload, onAction = onAction)
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
}

private const val LOAD_MORE_THRESHOLD = 3

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
                    style = AlTheme.typography.bodyStrong,
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
            if (upload.canDelete) {
                AlIconButton(
                    icon = Res.drawable.ic_mingcute_delete,
                    contentDescription = "Eliminar",
                    onClick = { onAction(SubjectUploadsAction.Delete(upload.localId)) },
                    tint = AlTheme.colors.textBase,
                )
            }
        }

        if (upload.canRetry) {
            Row(horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                AlButton(
                    onClick = { onAction(SubjectUploadsAction.Retry(upload.localId)) },
                    variant = AlButtonVariant.Outline,
                ) {
                    Text("Reintentar")
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
