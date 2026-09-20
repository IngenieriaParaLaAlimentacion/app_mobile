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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import ar.com.ingenieriaalimentacion.core.design_system.components.sheet.AlBottomSheet
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockItem
import ar.com.ingenieriaalimentacion.domain.model.food.StockSortOrder
import ar.com.ingenieriaalimentacion.presentation.common.formatQuantityWithUnit
import com.komoui.components.Spinner
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_check_line
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.vectorResource
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

    // The current establishment is not observable, so returning to the screen is the trigger.
    // See FoodStockViewModel.onResume for why that is cheap and why it cannot double-fire.
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
    AlScaffold {
        Column(modifier = Modifier.fillMaxSize()) {
            AlBackTopAppBar(
                title = "Stock",
                onBackClick = { onAction(FoodStockAction.BackClick) },
            )

            state.establishment?.let { establishment ->
                Text(
                    text = establishment.name,
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

                FilterBar(
                    categoryLabel = state.selectedCategoryName ?: "Todas",
                    sortLabel = state.filters.sortOrder.label,
                    categoriesEnabled = state.categories.isNotEmpty(),
                    onAction = onAction,
                )
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
                onDismiss = { onAction(FoodStockAction.SortSheetDismiss) },
            ) {
                StockSortOrder.entries.forEach { sort ->
                    SelectableRow(
                        title = sort.label,
                        selected = sort == state.filters.sortOrder,
                        onClick = { onAction(FoodStockAction.SortSelected(sort)) },
                    )
                }
            }
        }

        if (state.showCategorySheet) {
            PickerSheet(
                title = "Categoría",
                onDismiss = { onAction(FoodStockAction.CategorySheetDismiss) },
            ) {
                // A full-width row, not an AlChoiceGroup chip: one catalog category is 60+ chars
                // ("Legumbres, cereales, papa, choclo, batata, pan y pastas") and would render as a
                // paragraph inside a chip.
                SelectableRow(
                    title = "Todas las categorías",
                    selected = state.filters.categoryId == null,
                    onClick = { onAction(FoodStockAction.CategorySelected(null)) },
                )
                state.categories.forEach { category ->
                    SelectableRow(
                        title = category.name,
                        selected = state.filters.categoryId == category.id,
                        onClick = { onAction(FoodStockAction.CategorySelected(category.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterBar(
    categoryLabel: String,
    sortLabel: String,
    categoriesEnabled: Boolean,
    onAction: (FoodStockAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AlTheme.spacing.lg, vertical = AlTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
            AlSelectField(
                modifier = Modifier.weight(1f),
                value = categoryLabel,
                // Disabled until the (best-effort) category call returns; the list never waits.
                enabled = categoriesEnabled,
                onClick = { onAction(FoodStockAction.CategorySheetOpen) },
            )
            AlSelectField(
                modifier = Modifier.weight(1f),
                value = sortLabel,
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
    LaunchedEffect(listState) {
        // The last visible index, not a nearEnd boolean: after a failed extra page the boolean is
        // already true, so distinctUntilChanged would swallow every later emission and the retry
        // the user expects from scrolling on would never fire.
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { last ->
                val nearEnd = last >= currentState.items.size - 1 - LOAD_MORE_THRESHOLD
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

        state.isEmpty -> {
            val (title, caption) = state.emptyCopy()
            EmptyState(title = title, caption = caption, modifier = modifier)
        }

        else -> LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
            itemsIndexed(
                items = state.items,
                // Position first: pages are only ever appended, so the index is stable for rows
                // already on screen, and it keeps the key unique. food_id alone would not — a
                // write between two page requests shifts the OFFSET window and can repeat a row,
                // and a duplicate key is a LazyColumn crash. Deduping is the server's job (it
                // breaks ties on id); the client must merely survive a repeat.
                key = { index, item -> "$index-${item.foodId}" },
                contentType = { _, _ -> "stock-row" },
            ) { _, item -> StockRow(item) }
            if (state.isLoadingMore) {
                item {
                    Spinner(modifier = Modifier.fillMaxWidth().padding(16.dp), size = 24.dp)
                }
            }
        }
    }
}

@Composable
internal fun StockRow(item: FoodStockItem) {
    AlListRow(
        title = item.foodName,
        subtitle = item.categoryName,
        // No onClick: there is no stock-detail screen in this story, and the chevron would promise
        // one. (`/stock/{food_id}/movements` exists on the backend but is out of scope.)
        // 6/10, not a flat 8/8: Work Sans's ascent makes even padding read top-heavy here. The
        // 16dp total is unchanged, so row height and list rhythm stay put. See AlListRow's KDoc.
        topPadding = 6.dp,
        bottomPadding = 10.dp,
        trailing = {
            Text(
                // formatQuantityWithUnit: "10.000" -> "10 kg", "0.750" -> "0,75 kg".
                // String-in/string-out, never through Double. Mono face: this is read as data.
                text = formatQuantityWithUnit(item.quantity, item.unit),
                style = AlTheme.typography.data,
                color = if (item.isDepleted) AlTheme.colors.textMuted else AlTheme.colors.textBase,
            )
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
private fun FoodStockState.emptyCopy(): Pair<String, String> = when {
    filters.categoryId != null -> "Sin stock en esta categoría" to "Probá con otra categoría."

    else -> "Sin alimentos disponibles" to
        "Cuando registres ingresos de alimentos, el stock aparecerá acá."
}

@Composable
private fun SelectableRow(title: String, selected: Boolean, onClick: () -> Unit) = AlListRow(
    title = title,
    onClick = onClick,
    showChevron = false,
    trailing = {
        if (selected) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_mingcute_check_line),
                contentDescription = null,
                tint = AlTheme.colors.accent,
            )
        }
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlBottomSheet(onDismissRequest = onDismiss) {
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
