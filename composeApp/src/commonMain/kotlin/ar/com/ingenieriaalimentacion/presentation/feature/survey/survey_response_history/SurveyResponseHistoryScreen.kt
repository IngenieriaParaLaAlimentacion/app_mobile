package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.list.AlListRow
import ar.com.ingenieriaalimentacion.core.design_system.components.pill.AlPill
import ar.com.ingenieriaalimentacion.core.design_system.components.pill.AlPillTone
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary
import com.komoui.components.Spinner
import com.komoui.themes.styles
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SurveyResponseHistoryScreenRoot(
    viewModel: SurveyResponseHistoryViewModel = koinViewModel(),
    navigate: (SurveyResponseHistoryDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    SurveyResponseHistoryScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun SurveyResponseHistoryScreen(
    state: SurveyResponseHistoryState,
    onAction: (SurveyResponseHistoryAction) -> Unit,
) {
    val styles = MaterialTheme.styles
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

    AlScaffold {
        Column(modifier = Modifier.fillMaxSize()) {
            AlBackTopAppBar(
                onBackClick = { onAction(SurveyResponseHistoryAction.BackClick) },
                title = "Mis respuestas",
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AlTheme.spacing.lg),
            ) {
                when {
                    state.isLoading -> Spinner(
                        modifier = Modifier.align(Alignment.Center),
                        size = 28.dp,
                    )

                    state.loadError -> Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "No se pudieron cargar tus respuestas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = styles.mutedForeground,
                        )
                        AlButton(onClick = { onAction(SurveyResponseHistoryAction.RetryLoad) }) {
                            Text("Reintentar")
                        }
                    }

                    state.items.isEmpty() -> Text(
                        text = "Todavía no respondiste ninguna encuesta.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = styles.mutedForeground,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
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
            horizontalPadding = 0.dp,
            title = item.surveyName,
            subtitle = item.establishmentName,
            onClick = { onAction(SurveyResponseHistoryAction.ResponseClick(item.id)) },
            trailing = { AlPill(text = label, tone = tone) },
        )
        if (item.syncState == SyncState.Failed) {
            AlButton(onClick = { onAction(SurveyResponseHistoryAction.RetryUploadClick(item.id)) }) {
                Text("Reintentar")
            }
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 3
