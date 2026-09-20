package ar.com.ingenieriaalimentacion.presentation.feature.food.establishment_food_movement_history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.AlErrorState
import ar.com.ingenieriaalimentacion.core.design_system.components.EmptyState
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlAutocompleteInput
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlInput
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlSuggestion
import ar.com.ingenieriaalimentacion.core.design_system.components.input.DateVisualTransformation
import ar.com.ingenieriaalimentacion.core.design_system.components.list.AlListRow
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.segmented.AlSegmentedControl
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementSummary
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementDirection
import com.komoui.components.Spinner
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_package_2_line
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_package_line
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

@Composable
fun EstablishmentFoodMovementHistoryScreenRoot(
    viewModel: EstablishmentFoodMovementHistoryViewModel = koinViewModel(),
    navigate: (EstablishmentFoodMovementHistoryDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    EstablishmentFoodMovementHistoryScreen(state, viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstablishmentFoodMovementHistoryScreen(
    state: EstablishmentFoodMovementHistoryState,
    onAction: (EstablishmentFoodMovementHistoryAction) -> Unit,
) {
    AlScaffold {
        Column(Modifier.fillMaxSize()) {
            AlBackTopAppBar(
                title = "Movimientos",
                onBackClick = { onAction(EstablishmentFoodMovementHistoryAction.BackClick) },
            )
            state.establishment?.let {
                Text(
                    text = it.name,
                    style = AlTheme.typography.caption,
                    color = AlTheme.colors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = AlTheme.spacing.sm),
                )
                AlButton(
                    onClick = { onAction(EstablishmentFoodMovementHistoryAction.FilterOpen) },
                    variant = AlButtonVariant.Outline,
                    modifier = Modifier.padding(horizontal = AlTheme.spacing.lg),
                ) {
                    Text(if (state.hasActiveFilters) "Filtros activos" else "Filtrar")
                }
            }
            MovementBody(state, onAction, Modifier.weight(1f))
        }
        if (state.showEstablishmentPicker) EstablishmentPickerSheet(state, onAction)
        if (state.showFilters) MovementFilterSheet(state.filterDraft, onAction)
    }
}

@Composable
private fun MovementBody(
    state: EstablishmentFoodMovementHistoryState,
    onAction: (EstablishmentFoodMovementHistoryAction) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val currentState by rememberUpdatedState(state)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { last ->
                if (last >= currentState.items.size - 1 - LOAD_MORE_THRESHOLD && currentState.canLoadMore) {
                    onAction(EstablishmentFoodMovementHistoryAction.LoadMore)
                }
            }
    }
    when {
        state.needsEstablishment && state.establishmentOptions.isEmpty() -> EmptyState(
            title = "Todavía no tenés un establecimiento",
            caption = "Pedí acceso a un establecimiento desde tu perfil para ver sus movimientos.",
            modifier = modifier,
        )
        state.needsEstablishment -> AlErrorState(
            title = if (state.staleSelection) {
                "Ese establecimiento ya no está disponible"
            } else {
                "Elegí un establecimiento"
            },
            caption = "Elegí de cuál querés ver los movimientos.",
            retryLabel = "Elegir establecimiento",
            onRetry = { onAction(EstablishmentFoodMovementHistoryAction.EstablishmentPickerOpen) },
            modifier = modifier,
        )
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Spinner(size = 28.dp)
        }
        state.showsBlockingError -> MovementErrorState(state, onAction, modifier)
        state.isEmpty && state.hasActiveFilters -> Column(modifier) {
            EmptyState(
                title = "No hay movimientos con estos filtros",
                caption = "Probá cambiar o limpiar los filtros.",
                modifier = Modifier.weight(1f),
            )
            AlButton(
                onClick = { onAction(EstablishmentFoodMovementHistoryAction.FilterClear) },
                fullWidth = true,
                variant = AlButtonVariant.Outline,
                modifier = Modifier.padding(AlTheme.spacing.lg),
            ) { Text("Limpiar filtros") }
        }
        state.isEmpty -> EmptyState(
            title = "Todavía no hay movimientos",
            caption = "Los ingresos y egresos aparecerán acá.",
            modifier = modifier,
        )
        else -> LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
            itemsIndexed(
                items = state.items,
                key = { index, item -> "$index-${item.direction.wireValue}:${item.id}" },
            ) { _, item -> MovementRow(item) {
                onAction(EstablishmentFoodMovementHistoryAction.MovementClick(item.id))
            } }
            if (state.isLoadingMore) {
                item { Spinner(Modifier.fillMaxWidth().padding(16.dp), size = 24.dp) }
            }
        }
    }
}

@Composable
internal fun MovementRow(
    item: EstablishmentFoodMovementSummary,
    onClick: () -> Unit,
) {
    AlListRow(
        title = if (item.direction == EstablishmentFoodMovementDirection.In) "Ingreso" else "Egreso",
        subtitle = "${formatMovementDateTime(item.occurredAtMillis)} · ${item.responsible.fullName}",
        value = "${item.foodCount} alimento${if (item.foodCount == 1) "" else "s"}",
        leadingIcon = if (item.direction == EstablishmentFoodMovementDirection.In) {
            Res.drawable.ic_mingcute_package_line
        } else {
            Res.drawable.ic_mingcute_package_2_line
        },
        leadingIconTint = AlTheme.colors.accent,
        onClick = onClick,
    )
}

@Composable
private fun MovementErrorState(
    state: EstablishmentFoodMovementHistoryState,
    onAction: (EstablishmentFoodMovementHistoryAction) -> Unit,
    modifier: Modifier,
) = when (state.error) {
    EstablishmentFoodMovementHistoryError.AccessDenied -> AlErrorState(
        title = "No tenés acceso a este establecimiento",
        caption = "Elegí otro establecimiento o pedí acceso desde tu perfil.",
        retryLabel = "Elegir establecimiento",
        onRetry = { onAction(EstablishmentFoodMovementHistoryAction.EstablishmentPickerOpen) },
        modifier = modifier,
    )
    EstablishmentFoodMovementHistoryError.EstablishmentNotFound -> AlErrorState(
        title = "Ese establecimiento ya no existe",
        caption = "Elegí otro para ver sus movimientos.",
        retryLabel = "Elegir establecimiento",
        onRetry = { onAction(EstablishmentFoodMovementHistoryAction.EstablishmentPickerOpen) },
        modifier = modifier,
    )
    EstablishmentFoodMovementHistoryError.Terminal -> AlErrorState(
        title = "No pudimos mostrar los movimientos",
        caption = "Volvé a intentarlo más tarde.",
        onRetry = null,
        modifier = modifier,
    )
    else -> AlErrorState(
        title = "No pudimos cargar los movimientos",
        caption = if (!state.isOnline) {
            "Estás sin conexión. Los movimientos se consultan en línea."
        } else {
            "Revisá tu conexión e intentá de nuevo."
        },
        onRetry = { onAction(EstablishmentFoodMovementHistoryAction.RetryLoad) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovementFilterSheet(
    draft: MovementFilterDraft,
    onAction: (EstablishmentFoodMovementHistoryAction) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onAction(EstablishmentFoodMovementHistoryAction.FilterDismiss) },
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(AlTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.md),
        ) {
            Text("Filtrar movimientos", style = AlTheme.typography.bodyStrong)
            AlSegmentedControl(
                segments = listOf("Todos", "Ingresos", "Egresos"),
                selectedIndex = when (draft.direction) {
                    null -> 0
                    EstablishmentFoodMovementDirection.In -> 1
                    EstablishmentFoodMovementDirection.Out -> 2
                },
                onSelect = {
                    onAction(
                        EstablishmentFoodMovementHistoryAction.DirectionChanged(
                            when (it) {
                                1 -> EstablishmentFoodMovementDirection.In
                                2 -> EstablishmentFoodMovementDirection.Out
                                else -> null
                            },
                        ),
                    )
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                AlInput(
                    value = draft.from.value,
                    onValueChange = { onAction(EstablishmentFoodMovementHistoryAction.FromChanged(it)) },
                    onFocusChange = {
                        onAction(EstablishmentFoodMovementHistoryAction.FromFocusChanged(it))
                    },
                    label = "Desde",
                    placeholder = "DD/MM/AAAA",
                    error = draft.from.displayError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = DateVisualTransformation(),
                    modifier = Modifier.weight(1f),
                )
                AlInput(
                    value = draft.to.value,
                    onValueChange = { onAction(EstablishmentFoodMovementHistoryAction.ToChanged(it)) },
                    onFocusChange = {
                        onAction(EstablishmentFoodMovementHistoryAction.ToFocusChanged(it))
                    },
                    label = "Hasta",
                    placeholder = "DD/MM/AAAA",
                    error = draft.to.displayError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = DateVisualTransformation(),
                    modifier = Modifier.weight(1f),
                )
            }
            AlAutocompleteInput(
                value = draft.foodQuery,
                onValueChange = { onAction(EstablishmentFoodMovementHistoryAction.FoodQueryChanged(it)) },
                onFocusChange = { onAction(EstablishmentFoodMovementHistoryAction.FoodFocusChanged(it)) },
                label = "Alimento",
                placeholder = "Buscá por nombre",
                expanded = draft.foodFocused && draft.foodQuery.isNotBlank(),
                isLoading = draft.isSearchingFood,
                suggestions = draft.foodSuggestions.map { AlSuggestion(it.id, it.name) },
                onSuggestionClick = { suggestion ->
                    draft.foodSuggestions.firstOrNull { it.id == suggestion.id }?.let {
                        onAction(EstablishmentFoodMovementHistoryAction.FoodSelected(it))
                    }
                },
                errorText = "No se pudo buscar en el catálogo."
                    .takeIf { draft.foodSearchFailed },
                onRetry = { onAction(EstablishmentFoodMovementHistoryAction.FoodSearchRetry) },
                footer = if (draft.foodTotal > draft.foodSuggestions.size) {
                    "Mostrando ${draft.foodSuggestions.size} de ${draft.foodTotal}"
                } else {
                    null
                },
            )
            draft.foodError?.let {
                Text(it, style = AlTheme.typography.caption, color = AlTheme.colors.danger)
            }
            AlButton(
                onClick = { onAction(EstablishmentFoodMovementHistoryAction.FilterApply) },
                fullWidth = true,
            ) { Text("Aplicar") }
            AlButton(
                onClick = { onAction(EstablishmentFoodMovementHistoryAction.FilterClear) },
                fullWidth = true,
                variant = AlButtonVariant.Outline,
            ) { Text("Limpiar") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EstablishmentPickerSheet(
    state: EstablishmentFoodMovementHistoryState,
    onAction: (EstablishmentFoodMovementHistoryAction) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = {
            onAction(EstablishmentFoodMovementHistoryAction.EstablishmentPickerDismiss)
        },
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                text = if (state.staleSelection) {
                    "Elegí otro establecimiento"
                } else {
                    "Elegí el establecimiento"
                },
                style = AlTheme.typography.bodyStrong,
                modifier = Modifier.padding(AlTheme.spacing.md),
            )
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                state.establishmentOptions.forEach { establishment ->
                    AlListRow(
                        title = establishment.name,
                        subtitle = establishment.formattedAddress,
                        onClick = {
                            onAction(
                                EstablishmentFoodMovementHistoryAction.EstablishmentSelected(
                                    establishment,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun formatMovementDateTime(epochMillis: Long): String {
    val value = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "${value.day.toString().padStart(2, '0')}/" +
        "${value.month.number.toString().padStart(2, '0')}/${value.year} " +
        "${value.hour.toString().padStart(2, '0')}:" +
        value.minute.toString().padStart(2, '0')
}

private const val LOAD_MORE_THRESHOLD = 3
