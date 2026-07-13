package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import ar.com.ingenieriaalimentacion.core.design_system.components.EmptyState
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.list.AlListRow
import ar.com.ingenieriaalimentacion.core.design_system.components.pill.AlPill
import ar.com.ingenieriaalimentacion.core.design_system.components.pill.AlPillTone
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary
import com.komoui.components.Spinner
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun SurveyResponseHistoryContent(
    state: SurveyResponseHistoryState,
    onAction: (SurveyResponseHistoryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentState by rememberUpdatedState(state)

    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= currentState.items.lastIndex - LOAD_MORE_THRESHOLD
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && currentState.canLoadMore) onAction(SurveyResponseHistoryAction.LoadMore)
        }
    }

    when {
        state.isLoading -> Box(modifier.fillMaxSize(), Alignment.Center) {
            Spinner(size = 28.dp)
        }

        state.isEmpty -> EmptyState(
            title = "Sin respuestas todavía",
            caption = "Las encuestas que respondas aparecerán acá con su estado de sincronización.",
            modifier = modifier,
        )

        else -> LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
        ) {
            items(items = state.items, key = { it.id }) { item ->
                SurveyResponseHistoryRow(item = item, onAction = onAction)
            }
            if (state.isLoadingMore) {
                item {
                    Spinner(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        size = 24.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SurveyResponseHistoryRow(
    item: SurveyResponseSummary,
    onAction: (SurveyResponseHistoryAction) -> Unit,
) {
    val (label, tone) = when (item.syncState) {
        SyncState.Pending -> "Pendiente" to AlPillTone.Warning
        SyncState.Syncing -> "Sincronizando" to AlPillTone.Neutral
        SyncState.Synced -> "Sincronizado" to AlPillTone.Success
        SyncState.Failed -> "Error" to AlPillTone.Danger
        SyncState.Merged -> "Sincronizado" to AlPillTone.Success // unreachable for survey responses
    }
    Column {
        AlListRow(
            horizontalPadding = AlTheme.spacing.md,
            title = item.surveyName,
            subtitle = item.establishmentName,
            onClick = { onAction(SurveyResponseHistoryAction.ResponseClick(item.id)) },
            trailing = { AlPill(text = label, tone = tone) },
        )
        if (item.syncState == SyncState.Failed) {
            AlButton(
                onClick = { onAction(SurveyResponseHistoryAction.RetryUploadClick(item.id)) },
                modifier = Modifier.padding(horizontal = AlTheme.spacing.md, vertical = AlTheme.spacing.xs),
            ) {
                Text("Reintentar")
            }
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 3
