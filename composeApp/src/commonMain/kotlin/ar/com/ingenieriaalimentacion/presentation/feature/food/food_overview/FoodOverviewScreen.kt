package ar.com.ingenieriaalimentacion.presentation.feature.food.food_overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.AlErrorState
import ar.com.ingenieriaalimentacion.core.design_system.components.EmptyState
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.list.AlDateSeparator
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.sheet.AlBottomSheet
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.food.StockSortOrder
import ar.com.ingenieriaalimentacion.presentation.common.today
import ar.com.ingenieriaalimentacion.presentation.feature.food.components.FoodSelectionOption
import ar.com.ingenieriaalimentacion.presentation.feature.food.components.FoodStockControls
import ar.com.ingenieriaalimentacion.presentation.feature.food.establishment_food_movement_history.MovementRow
import ar.com.ingenieriaalimentacion.presentation.feature.food.establishment_food_movement_history.toDayGroups
import ar.com.ingenieriaalimentacion.presentation.feature.food.food_stock.StockRow
import ar.com.ingenieriaalimentacion.presentation.navigation.FOOD_MOVEMENT_CREATED_RESULT
import ar.com.ingenieriaalimentacion.presentation.navigation.RefreshOnNavResult
import com.komoui.components.Spinner
import kotlinx.datetime.TimeZone
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FoodOverviewScreenRoot(
    savedStateHandle: SavedStateHandle?,
    viewModel: FoodOverviewViewModel = koinViewModel(),
    navigate: (FoodOverviewDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }
    RefreshOnNavResult(savedStateHandle, FOOD_MOVEMENT_CREATED_RESULT) {
        viewModel.refreshAfterMutation()
    }
    FoodOverviewScreen(state, viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodOverviewScreen(
    state: FoodOverviewState,
    onAction: (FoodOverviewAction) -> Unit,
) {
    AlScaffold {
        Column(Modifier.fillMaxSize()) {
            AlBackTopAppBar(
                title = state.establishment?.name ?: "Alimentos",
                onBackClick = { onAction(FoodOverviewAction.BackClick) },
            )
            when {
                state.isResolvingEstablishment -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Spinner(size = 28.dp) }
                state.establishmentProblem != null -> ScopeProblem(state, onAction)
                else -> FoodOverviewContent(state, onAction)
            }
        }
        if (state.stock.showCategorySheet) CategorySheet(state, onAction)
        if (state.stock.showSortSheet) SortSheet(state, onAction)
    }
}

/**
 * The screen's single horizontal inset. `AlListRow` already carries the same `md` padding
 * internally, so stock and movement rows line up with these without asking for it.
 */
@Composable
private fun Modifier.gutter(): Modifier = padding(horizontal = AlTheme.spacing.md)

@Composable
private fun FoodOverviewContent(
    state: FoodOverviewState,
    onAction: (FoodOverviewAction) -> Unit,
) {
    val zone = TimeZone.currentSystemDefault()
    val movements = state.movements.items
    val days = remember(movements) { movements.toDayGroups(today = today(zone), zone = zone) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // No horizontal inset: AlListRow pads itself by `md` and would be pushed in twice.
        // Every item that is NOT a self-padding row carries `Modifier.gutter()` instead.
        contentPadding = PaddingValues(vertical = AlTheme.spacing.lg),
        // Deliberately no verticalArrangement: it would also space two consecutive AlListRows,
        // which already close themselves with a hairline, leaving a gap above a row's text and
        // none below its divider. Rows stack flush so the dividers carry the rhythm.
    ) {
        item { OverviewActions(onAction) }
        item {
            OverviewSectionHeading(
                text = "Stock actual",
                modifier = Modifier.gutter().padding(top = AlTheme.spacing.lg),
            )
        }
        item {
            FoodStockControls(
                categoryName = state.stock.selectedCategoryName,
                filtered = state.stock.filters.categoryId != null,
                categoriesEnabled = state.stock.categories.isNotEmpty(),
                sortOrder = state.stock.filters.sortOrder,
                onCategoryClick = { onAction(FoodOverviewAction.CategorySheetOpen) },
                onSortClick = { onAction(FoodOverviewAction.SortSheetOpen) },
                onClearCategory = { onAction(FoodOverviewAction.CategorySelected(null)) },
            )
        }
        when {
            state.stock.isLoading -> item { CenterSpinner() }
            state.stock.error != null && state.stock.items.isEmpty() -> item {
                SectionError(
                    section = "stock",
                    error = state.stock.error,
                    online = state.isOnline,
                    retry = { onAction(FoodOverviewAction.RetryStock) },
                )
            }
            state.stock.items.isEmpty() -> item {
                EmptyState(
                    title = if (state.stock.filters.categoryId != null) {
                        "No hay resultados para esta categoría"
                    } else {
                        "No hay alimentos en stock"
                    },
                    caption = if (state.stock.filters.categoryId != null) {
                        "Quitá el filtro para ver todas las categorías."
                    } else {
                        "Registrá un ingreso para comenzar."
                    },
                    modifier = Modifier.gutter().fillMaxWidth().heightIn(min = 140.dp),
                )
            }
            else -> itemsIndexed(
                items = state.stock.items,
                key = { index, item -> "stock-$index-${item.foodId}" },
            ) { _, item -> StockRow(item) }
        }
        item {
            TextButton(
                modifier = Modifier.gutter().fillMaxWidth().heightIn(min = 48.dp),
                onClick = { onAction(FoodOverviewAction.SeeAllStockClick) },
            ) {
                Text("Ver todo el stock", color = AlTheme.colors.accent)
            }
        }

        item {
            OverviewSectionHeading(
                text = "Últimos movimientos",
                modifier = Modifier.gutter().padding(top = AlTheme.spacing.xl),
                onSeeAll = { onAction(FoodOverviewAction.SeeAllMovementsClick) },
            )
        }
        when {
            state.movements.isLoading -> item { CenterSpinner() }
            state.movements.error != null -> item {
                SectionError(
                    section = "movimientos",
                    error = state.movements.error,
                    online = state.isOnline,
                    retry = { onAction(FoodOverviewAction.RetryMovements) },
                )
            }
            state.movements.items.isEmpty() -> item {
                EmptyState(
                    title = "Todavía no hay movimientos",
                    caption = "Los ingresos y egresos aparecerán acá.",
                    modifier = Modifier.gutter().fillMaxWidth().heightIn(min = 140.dp),
                )
            }
            else -> days.forEachIndexed { dayIndex, day ->
                item(key = day.key, contentType = "date-separator") {
                    // Not a stickyHeader: this list is a mixed page, and a pinned movement date
                    // would hang over the stock section as the user scrolls back up.
                    AlDateSeparator(
                        label = day.label,
                        // The first block already has the section header's gap above it.
                        topPadding = if (dayIndex == 0) {
                            AlTheme.spacing.md
                        } else {
                            AlTheme.spacing.lg
                        },
                    )
                }
                itemsIndexed(
                    items = day.items,
                    key = { index, item ->
                        "movement-$dayIndex-$index-${item.direction.wireValue}:${item.id}"
                    },
                    contentType = { _, _ -> "movement" },
                ) { index, item ->
                    // The last row of a day closes its block: the next header is the divider.
                    MovementRow(item = item, showDivider = index != day.items.lastIndex) {
                        onAction(FoodOverviewAction.MovementClick(item.id))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeProblem(
    state: FoodOverviewState,
    onAction: (FoodOverviewAction) -> Unit,
) {
    val loadFailed = state.establishmentProblem == EstablishmentProblem.LoadFailed
    AlErrorState(
        title = if (loadFailed) {
            "No pudimos cargar tu establecimiento"
        } else {
            "Elegí un establecimiento en Inicio"
        },
        caption = if (loadFailed) {
            "Revisá tu conexión e intentá nuevamente."
        } else {
            "Alimentos usa el establecimiento seleccionado globalmente."
        },
        retryLabel = if (loadFailed) "Reintentar" else "Volver a Inicio",
        onRetry = {
            onAction(if (loadFailed) FoodOverviewAction.RetryAll else FoodOverviewAction.BackClick)
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun CenterSpinner() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(AlTheme.spacing.lg),
        contentAlignment = Alignment.Center,
    ) { Spinner(size = 24.dp) }
}

@Composable
private fun SectionError(
    section: String,
    error: FoodOverviewSectionError,
    online: Boolean,
    retry: () -> Unit,
) {
    val terminal = error == FoodOverviewSectionError.Terminal
    val caption = when (error) {
        FoodOverviewSectionError.AccessDenied ->
            "Ya no tenés acceso al establecimiento seleccionado. Volvé a Inicio para elegir otro."
        FoodOverviewSectionError.EstablishmentNotFound ->
            "El establecimiento seleccionado ya no existe. Volvé a Inicio para elegir otro."
        FoodOverviewSectionError.Terminal -> "Volvé a intentarlo más tarde."
        FoodOverviewSectionError.Retryable -> if (online) {
            "Revisá tu conexión e intentá nuevamente."
        } else {
            "Volvé a intentar cuando tengas conexión."
        }
    }
    AlErrorState(
        title = "No pudimos cargar $section",
        caption = caption,
        onRetry = retry.takeUnless { terminal },
        modifier = Modifier.gutter().fillMaxWidth().heightIn(min = 160.dp),
    )
}

@Composable
private fun OverviewSectionHeading(
    text: String,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs),
    ) {
        Text(
            text = text,
            style = AlTheme.typography.subtitle,
            color = AlTheme.colors.textBase,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Ver todo", color = AlTheme.colors.accent)
            }
        }
    }
}

@Composable
private fun OverviewActions(onAction: (FoodOverviewAction) -> Unit) {
    BoxWithConstraints(Modifier.gutter().fillMaxWidth()) {
        val stack = maxWidth < 360.dp * LocalDensity.current.fontScale
        if (stack) {
            Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                IntakeButton(Modifier.fillMaxWidth(), onAction)
                OutflowButton(Modifier.fillMaxWidth(), onAction)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                IntakeButton(Modifier.weight(1f), onAction)
                OutflowButton(Modifier.weight(1f), onAction)
            }
        }
    }
}

@Composable
private fun IntakeButton(modifier: Modifier, onAction: (FoodOverviewAction) -> Unit) {
    AlButton(
        modifier = modifier.heightIn(min = 48.dp),
        onClick = { onAction(FoodOverviewAction.IntakeClick) },
    ) { Text("Registrar ingreso") }
}

@Composable
private fun OutflowButton(modifier: Modifier, onAction: (FoodOverviewAction) -> Unit) {
    AlButton(
        modifier = modifier.heightIn(min = 48.dp),
        onClick = { onAction(FoodOverviewAction.OutflowClick) },
        variant = AlButtonVariant.Outline,
    ) { Text("Registrar egreso") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySheet(state: FoodOverviewState, onAction: (FoodOverviewAction) -> Unit) {
    AlBottomSheet(onDismissRequest = { onAction(FoodOverviewAction.CategorySheetDismiss) }) {
        OverviewSectionHeading("Categoría", Modifier.gutter().padding(bottom = AlTheme.spacing.md))
        Column(
            Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()).selectableGroup(),
        ) {
            FoodSelectionOption(
                title = "Todas las categorías",
                selected = state.stock.filters.categoryId == null,
                onClick = { onAction(FoodOverviewAction.CategorySelected(null)) },
            )
            state.stock.categories.forEach { category ->
                FoodSelectionOption(
                    title = category.name,
                    selected = state.stock.filters.categoryId == category.id,
                    onClick = { onAction(FoodOverviewAction.CategorySelected(category.id)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(state: FoodOverviewState, onAction: (FoodOverviewAction) -> Unit) {
    AlBottomSheet(onDismissRequest = { onAction(FoodOverviewAction.SortSheetDismiss) }) {
        OverviewSectionHeading("Ordenar por", Modifier.gutter().padding(bottom = AlTheme.spacing.md))
        Column(
            Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()).selectableGroup(),
        ) {
            StockSortOrder.entries.forEach { sort ->
                FoodSelectionOption(
                    title = sort.label,
                    selected = state.stock.filters.sortOrder == sort,
                    onClick = { onAction(FoodOverviewAction.SortSelected(sort)) },
                )
            }
        }
    }
}
