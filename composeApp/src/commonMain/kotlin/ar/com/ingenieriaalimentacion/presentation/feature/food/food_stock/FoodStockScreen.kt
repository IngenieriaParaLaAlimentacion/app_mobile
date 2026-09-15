package ar.com.ingenieriaalimentacion.presentation.feature.food.food_stock

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.AlErrorState
import ar.com.ingenieriaalimentacion.core.design_system.components.EmptyState
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlSelectField
import ar.com.ingenieriaalimentacion.core.design_system.components.list.AlListRow
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.segmented.AlSegmentedControl
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockItem
import ar.com.ingenieriaalimentacion.domain.model.food.StockSortOrder
import ar.com.ingenieriaalimentacion.presentation.common.dayLabel
import ar.com.ingenieriaalimentacion.presentation.common.dayOf
import ar.com.ingenieriaalimentacion.presentation.common.formatQuantity
import ar.com.ingenieriaalimentacion.presentation.common.today
import com.komoui.components.Spinner
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.TimeZone
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FoodStockScreenRoot(
    viewModel: FoodStockViewModel = koinViewModel(),
    navigate: (FoodStockDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    // The current establishment is NOT observable (research §9.4): the local store is a one-shot
    // suspend read with no Flow, so nothing pushes a change into this screen. The only way to
    // change it is to go back to Home and use its picker — which makes ON_RESUME exactly the right
    // trigger. The ViewModel skips the first resume (init already resolved) and no-ops when the id
    // is unchanged, so this costs one SQLDelight read per return-to-screen and no network call.
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    FoodStockScreen(state = state, onAction = viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodStockScreen(
    state: FoodStockState,
    onAction: (FoodStockAction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    AlScaffold {
        Column(modifier = Modifier.fillMaxSize()) {
            AlBackTopAppBar(
                title = "Stock",
                onBackClick = { onAction(FoodStockAction.BackClick) },
            )

            state.establishment?.let {
                Text(
                    text = it.name,
                    style = AlTheme.typography.caption,
                    color = AlTheme.colors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = AlTheme.spacing.lg,
                            end = AlTheme.spacing.lg,
                            bottom = AlTheme.spacing.sm,
                        ),
                )
            }

            if (state.establishment != null) {
                FilterBar(state = state, onAction = onAction)
            }

            StockBody(state = state, onAction = onAction, modifier = Modifier.weight(1f))
        }

        if (state.showEstablishmentPicker) {
            PickerSheet(
                title = if (state.staleSelection) {
                    "Elegí otro establecimiento"
                } else {
                    "Elegí el establecimiento"
                },
                sheetState = sheetState,
                onDismiss = { onAction(FoodStockAction.EstablishmentPickerDismiss) },
            ) {
                state.establishmentOptions.forEach { establishment ->
                    AlListRow(
                        title = establishment.name,
                        subtitle = establishment.formattedAddress,
                        onClick = { onAction(FoodStockAction.EstablishmentSelected(establishment)) },
                    )
                }
            }
        }

        if (state.showSortSheet) {
            PickerSheet(
                title = "Ordenar por",
                sheetState = sheetState,
                onDismiss = { onAction(FoodStockAction.SortSheetDismiss) },
            ) {
                StockSortOrder.entries.forEach { sort ->
                    AlListRow(
                        title = sort.label,
                        onClick = { onAction(FoodStockAction.SortSelected(sort)) },
                        showChevron = false,
                        trailing = { if (sort == state.filters.sortOrder) SelectedMark() },
                    )
                }
            }
        }

        if (state.showCategorySheet) {
            PickerSheet(
                title = "Categoría",
                sheetState = sheetState,
                onDismiss = { onAction(FoodStockAction.CategorySheetDismiss) },
            ) {
                // A full-width row, not an AlChoiceGroup chip: one catalog category is 60+ chars
                // ("Legumbres, cereales, papa, choclo, batata, pan y pastas") and would render as a
                // paragraph inside a chip.
                AlListRow(
                    title = "Todas las categorías",
                    onClick = { onAction(FoodStockAction.CategorySelected(null)) },
                    showChevron = false,
                    trailing = { if (state.filters.categoryId == null) SelectedMark() },
                )
                state.categories.forEach { category ->
                    AlListRow(
                        title = category.name,
                        onClick = { onAction(FoodStockAction.CategorySelected(category.id)) },
                        showChevron = false,
                        trailing = { if (state.filters.categoryId == category.id) SelectedMark() },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterBar(state: FoodStockState, onAction: (FoodStockAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AlTheme.spacing.lg, vertical = AlTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
    ) {
        // Two segments, not a switch: the control names both states, so "Disponibles" can never be
        // read as the double negative an unlabelled toggle next to "Solo disponibles" invites.
        AlSegmentedControl(
            segments = listOf("Disponibles", "Todos"),
            selectedIndex = if (state.filters.onlyAvailable) 0 else 1,
            onSelect = { index -> onAction(FoodStockAction.OnlyAvailableChanged(index == 0)) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
            AlSelectField(
                modifier = Modifier.weight(1f),
                value = state.selectedCategoryName ?: "Todas",
                // Disabled until the (best-effort) category call returns; the list never waits.
                enabled = state.categories.isNotEmpty(),
                onClick = { onAction(FoodStockAction.CategorySheetOpen) },
            )
            AlSelectField(
                modifier = Modifier.weight(1f),
                value = state.filters.sortOrder.label,
                onClick = { onAction(FoodStockAction.SortSheetOpen) },
            )
        }
    }
}

@Composable
private fun StockBody(
    state: FoodStockState,
    onAction: (FoodStockAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentState by rememberUpdatedState(state)
    val itemCount by rememberUpdatedState(state.items.size)

    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= itemCount - 1 - LOAD_MORE_THRESHOLD
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && currentState.canLoadMore) onAction(FoodStockAction.LoadMore)
        }
    }

    when {
        state.needsEstablishment && state.establishmentOptions.isEmpty() -> EmptyState(
            title = "Todavía no tenés un establecimiento",
            caption = "Pedí acceso a un establecimiento desde tu perfil para ver su stock.",
            modifier = modifier,
        )

        // AlErrorState, not EmptyState: the picker auto-opens, but dismissing it must not strand
        // the user on a surface that has no way back to it.
        state.needsEstablishment -> AlErrorState(
            title = if (state.staleSelection) {
                "Ese establecimiento ya no está disponible"
            } else {
                "Elegí un establecimiento"
            },
            caption = "Elegí de cuál querés ver el stock.",
            retryLabel = "Elegir establecimiento",
            onRetry = { onAction(FoodStockAction.EstablishmentPickerOpen) },
            modifier = modifier,
        )

        state.isLoading -> Box(modifier.fillMaxSize(), Alignment.Center) { Spinner(size = 28.dp) }

        state.showsBlockingError -> StockErrorState(state = state, onAction = onAction, modifier = modifier)

        state.isEmpty -> EmptyState(
            title = state.emptyTitle(),
            caption = state.emptyCaption(),
            modifier = modifier,
        )

        else -> LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
            items(
                items = state.items,
                // (establishment_id, food_id) is unique server-side, so food_id is a stable
                // per-establishment key. It is NOT used to dedup — see the ViewModel.
                key = { it.foodId },
                contentType = { "stock-row" },
            ) { item -> StockRow(item) }
            if (state.isLoadingMore) {
                item {
                    Spinner(modifier = Modifier.fillMaxWidth().padding(16.dp), size = 24.dp)
                }
            }
        }
    }
}

@Composable
private fun StockRow(item: FoodStockItem) {
    val zone = TimeZone.currentSystemDefault()
    AlListRow(
        title = item.foodName,
        // "Cereales · kg" — category and unit are both row-level facts and neither deserves a line.
        subtitle = "${item.categoryName} · ${item.unit.shortLabel}",
        // No onClick: there is no stock-detail screen in this story, and the chevron would promise
        // one. (`/stock/{food_id}/movements` exists on the backend but is out of scope.)
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    // formatQuantity: "10.000" -> "10", "0.750" -> "0,75". String-in/string-out,
                    // never through Double. Mono face because this is read as data.
                    text = formatQuantity(item.quantity),
                    style = AlTheme.typography.data,
                    color = if (item.isDepleted) AlTheme.colors.textMuted else AlTheme.colors.textBase,
                )
                item.updatedAtMillis?.let { millis ->
                    Text(
                        // "Actualizado", never "última carga": this is the WRITE timestamp, and a
                        // backdated intake stamps it with today. Research §9.4.2.
                        text = "Actualizado ${dayLabel(dayOf(millis, zone), today(zone)).lowercase()}",
                        style = AlTheme.typography.caption,
                        color = AlTheme.colors.textMuted,
                    )
                }
            }
        },
    )
}

@Composable
private fun StockErrorState(
    state: FoodStockState,
    onAction: (FoodStockAction) -> Unit,
    modifier: Modifier = Modifier,
) = when (state.error) {
    FoodStockScreenError.AccessDenied -> AlErrorState(
        title = "No tenés acceso a este establecimiento",
        caption = "Puede que todavía no te hayan aprobado, o que te hayan dado de baja. " +
            "Elegí otro establecimiento o pedí acceso desde tu perfil.",
        retryLabel = "Elegir establecimiento",
        onRetry = { onAction(FoodStockAction.EstablishmentPickerOpen) },
        modifier = modifier,
    )

    FoodStockScreenError.EstablishmentNotFound -> AlErrorState(
        title = "Ese establecimiento ya no existe",
        caption = "Elegí otro para ver su stock.",
        retryLabel = "Elegir establecimiento",
        onRetry = { onAction(FoodStockAction.EstablishmentPickerOpen) },
        modifier = modifier,
    )

    FoodStockScreenError.Terminal -> AlErrorState(
        title = "No pudimos mostrar el stock",
        caption = "Volvé a intentarlo más tarde.",
        onRetry = null,
        modifier = modifier,
    )

    // Retryable + EstablishmentsUnavailable share the retry affordance; only the copy differs, and
    // the offline variant is what ConnectivityObserver buys us.
    else -> AlErrorState(
        title = "No pudimos cargar el stock",
        caption = if (!state.isOnline) {
            "Estás sin conexión. El stock se consulta en línea; volvé a intentarlo cuando tengas señal."
        } else {
            "Revisá tu conexión e intentá de nuevo."
        },
        onRetry = { onAction(FoodStockAction.RetryLoad) },
        modifier = modifier,
    )
}

/** CA4's empty state, filter-aware: a category with no rows here is a real and expected case. */
private fun FoodStockState.emptyTitle(): String = when {
    filters.categoryId != null -> "Sin stock en esta categoría"
    filters.onlyAvailable -> "Sin alimentos disponibles"
    else -> "Todavía no hay stock"
}

private fun FoodStockState.emptyCaption(): String = when {
    filters.categoryId != null && filters.onlyAvailable ->
        "Probá con otra categoría, o mostrá también los alimentos agotados."
    filters.categoryId != null -> "Probá con otra categoría."
    filters.onlyAvailable -> "Mostrá «Todos» para ver también los alimentos que llegaron a cero."
    else -> "Cuando registres ingresos de alimentos, el stock aparecerá acá."
}

@Composable
private fun SelectedMark() = Text(
    text = "✓",
    style = AlTheme.typography.body,
    color = AlTheme.colors.accent,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(
    title: String,
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                text = title,
                style = AlTheme.typography.body,
                color = AlTheme.colors.textBase,
                modifier = Modifier.padding(
                    horizontal = AlTheme.spacing.md,
                    vertical = AlTheme.spacing.sm,
                ),
            )
            // 24 categories do not fit a phone; the sheet scrolls rather than clipping.
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) { content() }
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 3
