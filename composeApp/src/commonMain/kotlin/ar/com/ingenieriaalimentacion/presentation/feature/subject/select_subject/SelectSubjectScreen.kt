package ar.com.ingenieriaalimentacion.presentation.feature.subject.select_subject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlInput
import ar.com.ingenieriaalimentacion.core.design_system.components.list.AlListRow
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import com.komoui.components.Spinner
import com.komoui.themes.styles
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SelectSubjectScreenRoot(
    viewModel: SelectSubjectViewModel = koinViewModel(),
    navigate: (SelectSubjectDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    SelectSubjectScreen(
        state = state,
        onAction = viewModel::onAction,
    )
}

@Composable
fun SelectSubjectScreen(
    state: SelectSubjectState,
    onAction: (SelectSubjectAction) -> Unit,
) {
    val styles = MaterialTheme.styles
    val listState = rememberLazyListState()
    val currentState by rememberUpdatedState(state)

    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= currentState.results.lastIndex - LOAD_MORE_THRESHOLD
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && currentState.canLoadMore) onAction(SelectSubjectAction.LoadMore)
        }
    }

    AlScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            AlBackTopAppBar(
                onBackClick = { onAction(SelectSubjectAction.BackClick) },
                title = "Sujeto",
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            ) {
                AlInput(
                    value = state.query,
                    onValueChange = { onAction(SelectSubjectAction.QueryChanged(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    placeholder = "Buscar por nombre o DNI",
                    singleLine = true,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 16.dp),
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
                                text = "No se pudieron cargar los sujetos.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = styles.mutedForeground,
                            )
                            AlButton(onClick = { onAction(SelectSubjectAction.RetryClick) }) {
                                Text("Reintentar")
                            }
                        }

                        state.results.isEmpty() -> Text(
                            text = "Sin resultados",
                            style = MaterialTheme.typography.bodyMedium,
                            color = styles.mutedForeground,
                            modifier = Modifier.align(Alignment.Center),
                        )

                        else -> LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(items = state.results, key = { it.id }) { subject ->
                                AlListRow(
                                    title = "${subject.name} ${subject.lastName}",
                                    subtitle = "DNI ${subject.dni} · ${sexLabel(subject.sex)}",
                                    onClick = {
                                        onAction(SelectSubjectAction.SubjectClicked(subject))
                                    },
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

private fun sexLabel(sex: Sex): String = when (sex) {
    Sex.Male -> "Masculino"
    Sex.Female -> "Femenino"
}

private const val LOAD_MORE_THRESHOLD = 3
