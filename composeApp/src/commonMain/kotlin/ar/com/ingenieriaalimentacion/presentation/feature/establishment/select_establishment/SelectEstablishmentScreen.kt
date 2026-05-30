package ar.com.ingenieriaalimentacion.presentation.feature.establishment.select_establishment

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import com.komoui.components.Button
import com.komoui.components.Input
import com.komoui.components.Spinner
import com.komoui.themes.styles
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SelectEstablishmentScreenRoot(
    route: Route.SelectEstablishment,
    viewModel: SelectEstablishmentViewModel = koinViewModel { parametersOf(route) },
    navigate: (SelectEstablishmentDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    SelectEstablishmentScreen(
        state = state,
        onAction = viewModel::onAction,
    )
}

@Composable
fun SelectEstablishmentScreen(
    state: SelectEstablishmentState,
    onAction: (SelectEstablishmentAction) -> Unit,
) {
    val styles = MaterialTheme.styles

    AlScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            AlBackTopAppBar(
                onBackClick = { onAction(SelectEstablishmentAction.BackClick) },
                title = "Establecimiento",
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            ) {
                Input(
                    value = state.query,
                    onValueChange = { onAction(SelectEstablishmentAction.QueryChanged(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    placeholder = "Buscar establecimiento",
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
                                text = "No se pudieron cargar los establecimientos.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = styles.mutedForeground,
                            )
                            Button(onClick = { onAction(SelectEstablishmentAction.RetryClick) }) {
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
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(items = state.results, key = { it.id }) { establishment ->
                                EstablishmentCard(
                                    establishment = establishment,
                                    selected = state.isSelected(establishment.id),
                                    onClick = {
                                        onAction(SelectEstablishmentAction.EstablishmentToggled(establishment))
                                    },
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = { onAction(SelectEstablishmentAction.ConfirmClick) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    fullWidth = true,
                ) {
                    Text(if (state.selectedCount > 0) "Listo (${state.selectedCount})" else "Listo")
                }
            }
        }
    }
}

@Composable
private fun EstablishmentCard(
    establishment: Establishment,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val styles = MaterialTheme.styles
    val borderColor = if (selected) AlTheme.colors.accent else styles.border

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = establishment.name,
                style = MaterialTheme.typography.bodyLarge,
                color = styles.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = establishment.address,
                style = MaterialTheme.typography.bodyMedium,
                color = styles.mutedForeground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.titleMedium,
                color = AlTheme.colors.accent,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
