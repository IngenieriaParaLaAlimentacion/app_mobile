package ar.com.ingenieriaalimentacion.presentation.feature.food.food_overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.AlErrorState
import ar.com.ingenieriaalimentacion.core.design_system.components.EmptyState
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlSelectField
import ar.com.ingenieriaalimentacion.core.design_system.components.list.AlListRow
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.segmented.AlSegmentedControl
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.food.StockSortOrder
import ar.com.ingenieriaalimentacion.presentation.feature.food.establishment_food_movement_history.MovementRow
import ar.com.ingenieriaalimentacion.presentation.feature.food.food_stock.StockRow
import ar.com.ingenieriaalimentacion.presentation.navigation.FOOD_MOVEMENT_CREATED_RESULT
import ar.com.ingenieriaalimentacion.presentation.navigation.RefreshOnNavResult
import com.komoui.components.Spinner
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
                title = "Alimentos",
                onBackClick = { onAction(FoodOverviewAction.BackClick) },
            )
            state.establishment?.let {
                Text(
                    text = it.name,
                    style = AlTheme.typography.caption,
                    color = AlTheme.colors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = AlTheme.spacing.sm),
                )
            }
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

@Composable
private fun FoodOverviewContent(
    state: FoodOverviewState,
    onAction: (FoodOverviewAction) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AlTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.md),
    ) {
        item { SectionTitle("STOCK ACTUAL") }
        item {
            AlSegmentedControl(
                segments = listOf("Disponibles", "Todos"),
                selectedIndex = if (state.stock.filters.onlyAvailable) 0 else 1,
                onSelect = { onAction(FoodOverviewAction.OnlyAvailableChanged(it == 0)) },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                AlSelectField(
                    value = state.stock.selectedCategoryName ?: "Todas",
                    enabled = state.stock.categories.isNotEmpty(),
                    onClick = { onAction(FoodOverviewAction.CategorySheetOpen) },
                    modifier = Modifier.weight(1f),
                )
                AlSelectField(
                    value = state.stock.filters.sortOrder.label,
                    onClick = { onAction(FoodOverviewAction.SortSheetOpen) },
                    modifier = Modifier.weight(1f),
                )
            }
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
                    title = "No hay stock para mostrar",
                    caption = "Probá cambiar los filtros.",
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                )
            }
            else -> itemsIndexed(
                items = state.stock.items,
                key = { index, item -> "stock-$index-${item.foodId}" },
            ) { _, item -> StockRow(item) }
        }
        if (state.stock.canLoadMore || state.stock.isLoadingMore) {
            item {
                AlButton(
                    onClick = { onAction(FoodOverviewAction.LoadMoreStock) },
                    enabled = !state.stock.isLoadingMore,
                    loading = state.stock.isLoadingMore,
                    fullWidth = true,
                    variant = AlButtonVariant.Outline,
                ) { Text("Cargar más") }
            }
        }

        item { SectionTitle("MOVIMIENTOS") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                AlButton(
                    onClick = { onAction(FoodOverviewAction.IntakeClick) },
                    modifier = Modifier.weight(1f),
                ) { Text("Ingresar alimentos") }
                AlButton(
                    onClick = { onAction(FoodOverviewAction.OutflowClick) },
                    modifier = Modifier.weight(1f),
                    variant = AlButtonVariant.Outline,
                ) { Text("Registrar egreso") }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ÚLTIMOS MOVIMIENTOS",
                    style = AlTheme.typography.caption,
                    color = AlTheme.colors.textMuted,
                )
                Text(
                    text = "Ver todo",
                    style = AlTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                    color = AlTheme.colors.textBase,
                    modifier = Modifier.clickable {
                        onAction(FoodOverviewAction.SeeAllMovementsClick)
                    },
                )
            }
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
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                )
            }
            else -> itemsIndexed(
                items = state.movements.items,
                key = { index, item -> "movement-$index-${item.direction.wireValue}:${item.id}" },
            ) { _, item ->
                MovementRow(item) { onAction(FoodOverviewAction.MovementClick(item.id)) }
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
private fun SectionTitle(value: String) {
    Text(
        text = value,
        style = AlTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
        color = AlTheme.colors.textMuted,
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
        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySheet(
    state: FoodOverviewState,
    onAction: (FoodOverviewAction) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = { onAction(FoodOverviewAction.CategorySheetDismiss) }) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            AlListRow(
                title = "Todas las categorías",
                showChevron = false,
                onClick = { onAction(FoodOverviewAction.CategorySelected(null)) },
            )
            state.stock.categories.forEach { category ->
                AlListRow(
                    title = category.name,
                    showChevron = false,
                    onClick = { onAction(FoodOverviewAction.CategorySelected(category.id)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(
    state: FoodOverviewState,
    onAction: (FoodOverviewAction) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = { onAction(FoodOverviewAction.SortSheetDismiss) }) {
        Column(Modifier.navigationBarsPadding()) {
            StockSortOrder.entries.forEach { sort ->
                AlListRow(
                    title = sort.label,
                    showChevron = false,
                    onClick = { onAction(FoodOverviewAction.SortSelected(sort)) },
                )
            }
        }
    }
}
