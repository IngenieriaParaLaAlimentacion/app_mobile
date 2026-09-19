package ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_outflow

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.AlErrorState
import ar.com.ingenieriaalimentacion.core.design_system.components.EmptyState
import ar.com.ingenieriaalimentacion.core.design_system.components.banner.AlBannerVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.banner.AlInlineBanner
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlInput
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlSelectField
import ar.com.ingenieriaalimentacion.core.design_system.components.input.DateVisualTransformation
import ar.com.ingenieriaalimentacion.core.design_system.components.list.AlListRow
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.toast.AlToast
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.presentation.common.formatQuantityWithUnit
import ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_outflow.components.FoodOutflowLineRow
import com.komoui.components.Spinner
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CreateFoodOutflowScreenRoot(
    navigate: (CreateFoodOutflowDestination) -> Unit,
    viewModel: CreateFoodOutflowViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }
    CreateFoodOutflowScreen(state = state, onAction = viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFoodOutflowScreen(
    state: CreateFoodOutflowState,
    onAction: (CreateFoodOutflowAction) -> Unit,
) {
    val scrollState = rememberScrollState()
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    AlScaffold {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            AlBackTopAppBar(
                title = "Registrar egreso",
                enabled = !state.isSubmitting,
                onBackClick = { onAction(CreateFoodOutflowAction.BackClick) },
            )
            when {
                state.isLoadingProfile -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Spinner(size = 28.dp)
                }
                state.profileError != null -> AlErrorState(
                    title = "No pudimos cargar los establecimientos",
                    caption = state.profileError,
                    onRetry = { onAction(CreateFoodOutflowAction.RetryProfile) },
                    modifier = Modifier.fillMaxSize(),
                )
                else -> OutflowForm(state, onAction, scrollState, isImeVisible)
            }
        }
        if (state.showEstablishmentPicker) EstablishmentSheet(state, onAction)
        if (state.stock.showPicker) StockSheet(state, onAction)
        if (state.stock.showCategoryPicker) CategorySheet(state, onAction)
    }
}

@Composable
private fun OutflowForm(
    state: CreateFoodOutflowState,
    onAction: (CreateFoodOutflowAction) -> Unit,
    scrollState: ScrollState,
    isImeVisible: Boolean,
) {
    val enabled = !state.isSubmitting
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = AlTheme.spacing.lg),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(top = AlTheme.spacing.lg, bottom = AlTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.md),
            ) {
                if (!state.isOnline) {
                    AlInlineBanner(
                        message = "Necesitás conexión para registrar un egreso.",
                        variant = AlBannerVariant.Danger,
                    )
                }
                AlSelectField(
                    label = "Establecimiento",
                    value = state.selectedEstablishment?.name.orEmpty(),
                    placeholder = "Elegí el establecimiento",
                    enabled = enabled && state.establishmentOptions.isNotEmpty(),
                    onClick = { onAction(CreateFoodOutflowAction.EstablishmentPickerOpen) },
                )
                AlInput(
                    label = "Fecha del egreso",
                    value = state.occurredAt.value,
                    onValueChange = { onAction(CreateFoodOutflowAction.OccurredAtChanged(it)) },
                    onFocusChange = { onAction(CreateFoodOutflowAction.OccurredAtFocusChanged(it)) },
                    error = state.occurredAt.displayError,
                    enabled = enabled,
                    placeholder = "DD/MM/AAAA",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = DateVisualTransformation(),
                    scrollState = scrollState,
                    isImeVisible = isImeVisible,
                )
                AlSelectField(
                    label = "Agregar alimento",
                    value = "",
                    placeholder = "Elegí del stock disponible",
                    enabled = enabled && state.isOnline && state.selectedEstablishment != null,
                    onClick = { onAction(CreateFoodOutflowAction.StockPickerOpen) },
                )
                state.lines.forEach { line ->
                    FoodOutflowLineRow(
                        line = line,
                        enabled = enabled,
                        scrollState = scrollState,
                        isImeVisible = isImeVisible,
                        onQuantityChange = {
                            onAction(CreateFoodOutflowAction.LineQuantityChanged(line.stock.foodId, it))
                        },
                        onQuantityFocusChange = {
                            onAction(CreateFoodOutflowAction.LineQuantityFocusChanged(line.stock.foodId, it))
                        },
                        onRemove = { onAction(CreateFoodOutflowAction.LineRemoved(line.stock.foodId)) },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs)) {
                    AlInput(
                        label = "Comentarios",
                        value = state.comments.value,
                        onValueChange = { onAction(CreateFoodOutflowAction.CommentsChanged(it)) },
                        onFocusChange = { onAction(CreateFoodOutflowAction.CommentsFocusChanged(it)) },
                        error = state.comments.displayError,
                        enabled = enabled,
                        singleLine = false,
                        placeholder = "Opcional",
                        scrollState = scrollState,
                        isImeVisible = isImeVisible,
                    )
                    Text(
                        text = state.commentsCount,
                        style = AlTheme.typography.caption,
                        color = AlTheme.colors.textMuted,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AlTheme.spacing.sm, bottom = AlTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
            ) {
                state.submitError?.let {
                    Text(
                        text = it,
                        style = AlTheme.typography.body,
                        color = AlTheme.colors.danger,
                    )
                }
                AlButton(
                    onClick = { onAction(CreateFoodOutflowAction.Submit) },
                    enabled = state.canSubmit,
                    loading = state.isSubmitting,
                    fullWidth = true,
                ) {
                    Text("Guardar")
                }
            }
        }
        AlToast(
            message = state.successMessage,
            onDismiss = { onAction(CreateFoodOutflowAction.DismissSuccess) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EstablishmentSheet(
    state: CreateFoodOutflowState,
    onAction: (CreateFoodOutflowAction) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onAction(CreateFoodOutflowAction.EstablishmentPickerDismiss) },
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                text = "Elegí el establecimiento",
                modifier = Modifier.padding(AlTheme.spacing.md),
                style = AlTheme.typography.body,
                color = AlTheme.colors.textBase,
            )
            state.establishmentOptions.forEach { establishment ->
                AlListRow(
                    title = establishment.name,
                    subtitle = establishment.formattedAddress,
                    onClick = {
                        onAction(CreateFoodOutflowAction.EstablishmentSelected(establishment))
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockSheet(
    state: CreateFoodOutflowState,
    onAction: (CreateFoodOutflowAction) -> Unit,
) {
    val listState = rememberLazyListState()
    val currentStock by rememberUpdatedState(state.stock)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { last ->
                if (last >= currentStock.items.lastIndex - 3) {
                    onAction(CreateFoodOutflowAction.LoadMoreStock)
                }
            }
    }
    ModalBottomSheet(
        onDismissRequest = { onAction(CreateFoodOutflowAction.StockPickerDismiss) },
    ) {
        Column(modifier = Modifier.navigationBarsPadding().heightIn(max = 620.dp)) {
            Text(
                text = "Elegí un alimento",
                modifier = Modifier.padding(AlTheme.spacing.md),
                style = AlTheme.typography.body,
                color = AlTheme.colors.textBase,
            )
            AlSelectField(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AlTheme.spacing.md),
                label = "Categoría",
                value = state.stock.selectedCategoryName ?: "Todas",
                enabled = state.stock.categories.isNotEmpty(),
                onClick = { onAction(CreateFoodOutflowAction.CategoryPickerOpen) },
            )
            when {
                state.stock.isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Spinner(size = 28.dp)
                }
                state.stock.error != null -> AlErrorState(
                    title = "No pudimos cargar el stock",
                    caption = state.stock.error,
                    onRetry = { onAction(CreateFoodOutflowAction.RetryStock) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                state.stock.items.isEmpty() -> EmptyState(
                    title = "Sin alimentos disponibles",
                    caption = "Probá otra categoría o elegí otro establecimiento.",
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                else -> LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    items(state.stock.items, key = { it.foodId }) { item ->
                        val selected = state.lines.any { it.stock.foodId == item.foodId }
                        AlListRow(
                            title = item.foodName,
                            subtitle = "${item.categoryName} · Disponible " +
                                formatQuantityWithUnit(item.quantity, item.unit),
                            onClick = if (selected) {
                                null
                            } else {
                                { onAction(CreateFoodOutflowAction.FoodPicked(item)) }
                            },
                            showChevron = !selected,
                            trailing = if (selected) {
                                {
                                    Text(
                                        text = "Agregado",
                                        style = AlTheme.typography.caption,
                                        color = AlTheme.colors.textMuted,
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                    if (state.stock.isLoadingMore) {
                        item {
                            Spinner(
                                modifier = Modifier.fillMaxWidth().padding(AlTheme.spacing.md),
                                size = 24.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySheet(
    state: CreateFoodOutflowState,
    onAction: (CreateFoodOutflowAction) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onAction(CreateFoodOutflowAction.CategoryPickerDismiss) },
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            AlListRow(
                title = "Todas las categorías",
                showChevron = false,
                onClick = { onAction(CreateFoodOutflowAction.CategorySelected(null)) },
            )
            state.stock.categories.forEach { category ->
                AlListRow(
                    title = category.name,
                    showChevron = false,
                    onClick = { onAction(CreateFoodOutflowAction.CategorySelected(category.id)) },
                )
            }
        }
    }
}
