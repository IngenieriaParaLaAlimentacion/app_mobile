package ar.com.ingenieriaalimentacion.presentation.feature.profile.request_establishment

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.card.AlSelectableCard
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlInput
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import com.komoui.components.Spinner
import org.koin.compose.viewmodel.koinViewModel

private const val LOAD_MORE_THRESHOLD = 3

@Composable
fun RequestEstablishmentScreenRoot(
    viewModel: RequestEstablishmentViewModel = koinViewModel(),
    navigate: (RequestEstablishmentDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    RequestEstablishmentScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun RequestEstablishmentScreen(
    state: RequestEstablishmentState,
    onAction: (RequestEstablishmentAction) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState, state.results.size, state.canLoadMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                if (last != null && last >= state.results.size - LOAD_MORE_THRESHOLD && state.canLoadMore) {
                    onAction(RequestEstablishmentAction.LoadMore)
                }
            }
    }

    AlScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            AlBackTopAppBar(
                onBackClick = { onAction(RequestEstablishmentAction.BackClick) },
                title = "Solicitar acceso",
                enabled = !state.isSubmitting,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AlTheme.spacing.lg),
            ) {
                AlInput(
                    value = state.query,
                    onValueChange = { onAction(RequestEstablishmentAction.QueryChanged(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AlTheme.spacing.md),
                    placeholder = "Buscar establecimiento",
                    singleLine = true,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = AlTheme.spacing.md),
                ) {
                    when {
                        state.isLoading -> Spinner(
                            modifier = Modifier.align(Alignment.Center),
                            size = 28.dp,
                        )

                        state.loadError -> Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
                        ) {
                            Text(
                                text = "No se pudieron cargar los establecimientos.",
                                style = AlTheme.typography.body,
                                color = AlTheme.colors.textMuted,
                            )
                            AlButton(onClick = { onAction(RequestEstablishmentAction.RetryClick) }) {
                                Text("Reintentar")
                            }
                        }

                        state.results.isEmpty() -> Text(
                            text = "Sin resultados",
                            style = AlTheme.typography.body,
                            color = AlTheme.colors.textMuted,
                            modifier = Modifier.align(Alignment.Center),
                        )

                        else -> LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
                        ) {
                            items(items = state.results, key = { it.id }) { establishment ->
                                AlSelectableCard(
                                    title = establishment.name,
                                    description = establishment.formattedAddress,
                                    selected = state.isSelected(establishment.id),
                                    onClick = {
                                        onAction(RequestEstablishmentAction.EstablishmentToggled(establishment.id))
                                    },
                                )
                            }
                            if (state.isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = AlTheme.spacing.md),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Spinner(size = 20.dp)
                                    }
                                }
                            }
                        }
                    }
                }

                state.submitError?.let { error ->
                    Text(
                        text = error.toUserMessage(),
                        style = AlTheme.typography.body,
                        color = AlTheme.colors.danger,
                        modifier = Modifier.padding(bottom = AlTheme.spacing.sm),
                    )
                }

                AlButton(
                    onClick = { onAction(RequestEstablishmentAction.SubmitClick) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AlTheme.spacing.lg),
                    enabled = state.canSubmit,
                    loading = state.isSubmitting,
                    fullWidth = true,
                ) {
                    Text(
                        if (state.selectedCount > 0) {
                            "Solicitar acceso (${state.selectedCount})"
                        } else {
                            "Solicitar acceso"
                        },
                    )
                }
            }
        }
    }
}
