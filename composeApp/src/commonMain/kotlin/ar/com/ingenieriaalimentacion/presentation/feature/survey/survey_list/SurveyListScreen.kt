package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_list

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
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.card.AlInfoCard
import ar.com.ingenieriaalimentacion.core.design_system.components.list.AlListRow
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import com.komoui.components.Spinner
import com.komoui.themes.styles
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_time
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SurveyListScreenRoot(
    viewModel: SurveyListViewModel = koinViewModel(),
    navigate: (SurveyListDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    SurveyListScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun SurveyListScreen(
    state: SurveyListState,
    onAction: (SurveyListAction) -> Unit,
) {
    val styles = MaterialTheme.styles
    val listState = rememberLazyListState()
    val currentState by rememberUpdatedState(state)

    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= currentState.surveys.lastIndex - LOAD_MORE_THRESHOLD
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && currentState.canLoadMore) onAction(SurveyListAction.LoadMore)
        }
    }

    AlScaffold {
        Column(modifier = Modifier.fillMaxSize()) {
            AlBackTopAppBar(
                onBackClick = { onAction(SurveyListAction.BackClick) },
                title = "Encuestas",
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
                            text = "No se pudieron cargar las encuestas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = styles.mutedForeground,
                        )
                        AlButton(onClick = { onAction(SurveyListAction.RetryClick) }) {
                            Text("Reintentar")
                        }
                    }

                    state.surveys.isEmpty() -> Text(
                        text = "No hay encuestas disponibles.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = styles.mutedForeground,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    else -> Column(modifier = Modifier.fillMaxSize()) {
                        if (state.fromCache) {
                            AlInfoCard(
                                title = "Sin conexión",
                                description = "Mostrando encuestas guardadas",
                                leadingIcon = Res.drawable.ic_mingcute_time,
                                leadingIconTint = AlTheme.colors.textMuted,
                                modifier = Modifier.padding(top = AlTheme.spacing.md),
                                trailing = {
                                    AlButton(
                                        onClick = { onAction(SurveyListAction.RetryClick) },
                                        variant = AlButtonVariant.Outline,
                                    ) { Text("Reintentar") }
                                },
                            )
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(items = state.surveys, key = { it.id }) { survey ->
                                AlListRow(
                                    horizontalPadding = 0.dp,
                                    title = survey.name,
                                    subtitle = "${survey.questionCount} preguntas",
                                    onClick = { onAction(SurveyListAction.SurveyClick(survey.id)) },
                                )
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
}

private const val LOAD_MORE_THRESHOLD = 3
