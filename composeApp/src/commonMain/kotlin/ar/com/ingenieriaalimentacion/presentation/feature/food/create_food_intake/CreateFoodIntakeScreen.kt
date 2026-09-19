package ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_intake

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.banner.AlBannerVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.banner.AlInlineBanner
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlAutocompleteInput
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlInput
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlSuggestion
import ar.com.ingenieriaalimentacion.core.design_system.components.input.DateVisualTransformation
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.food.Food
import ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_intake.components.FoodLineRow
import com.komoui.components.Spinner
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CreateFoodIntakeScreenRoot(
    navigate: (CreateFoodIntakeDestination) -> Unit,
    viewModel: CreateFoodIntakeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    CreateFoodIntakeScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun CreateFoodIntakeScreen(
    state: CreateFoodIntakeState,
    onAction: (CreateFoodIntakeAction) -> Unit,
) {
    val scrollState = rememberScrollState()
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    AlScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            AlBackTopAppBar(
                onBackClick = { onAction(CreateFoodIntakeAction.BackClick) },
                title = "Cargar ingreso",
                enabled = !state.isSubmitting,
            )

            when {
                state.isLoadingProfile -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Spinner(size = 28.dp)
                }

                state.profileError != null -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(AlTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.profileError,
                        style = AlTheme.typography.body,
                        color = AlTheme.colors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                    AlButton(onClick = { onAction(CreateFoodIntakeAction.RetryProfile) }) {
                        Text("Reintentar")
                    }
                }

                else -> CreateFoodIntakeForm(
                    state = state,
                    onAction = onAction,
                    scrollState = scrollState,
                    isImeVisible = isImeVisible,
                )
            }
        }

    }
}

@Composable
private fun CreateFoodIntakeForm(
    state: CreateFoodIntakeState,
    onAction: (CreateFoodIntakeAction) -> Unit,
    scrollState: ScrollState,
    isImeVisible: Boolean,
) {
    val enabled = !state.isSubmitting
    val suggestions = remember(state.search.results) { state.search.results.map { it.toSuggestion() } }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AlTheme.spacing.lg),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(top = AlTheme.spacing.lg, bottom = AlTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!state.isOnline) {
                    AlInlineBanner(
                        message = "Necesitás conexión para cargar un ingreso.",
                        variant = AlBannerVariant.Danger,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs)) {
                    Text(
                        text = "Establecimiento",
                        style = AlTheme.typography.label,
                        color = AlTheme.colors.textMuted,
                    )
                    Text(
                        text = state.selectedEstablishment?.name.orEmpty(),
                        style = AlTheme.typography.body,
                        color = AlTheme.colors.textBase,
                    )
                }

                AlInput(
                    label = "Fecha de recepción",
                    value = state.receivedAt.value,
                    onValueChange = { onAction(CreateFoodIntakeAction.ReceivedAtChanged(it)) },
                    onFocusChange = { onAction(CreateFoodIntakeAction.ReceivedAtFocusChanged(it)) },
                    error = state.receivedAt.displayError,
                    enabled = enabled,
                    scrollState = scrollState,
                    isImeVisible = isImeVisible,
                    placeholder = "DD/MM/AAAA",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = DateVisualTransformation(),
                )

                AlAutocompleteInput(
                    label = "Agregar alimento",
                    value = state.search.query,
                    onValueChange = { onAction(CreateFoodIntakeAction.SearchQueryChanged(it)) },
                    onFocusChange = { onAction(CreateFoodIntakeAction.SearchFocusChanged(it)) },
                    // Offline the catalog is unreachable, so the field says so by not accepting input.
                    enabled = enabled && state.isOnline,
                    placeholder = "Buscá por nombre",
                    expanded = state.search.isExpanded,
                    isLoading = state.search.isSearching,
                    suggestions = suggestions,
                    onSuggestionClick = { suggestion ->
                        state.search.results
                            .firstOrNull { it.id == suggestion.id }
                            ?.let { onAction(CreateFoodIntakeAction.FoodPicked(it)) }
                    },
                    errorText = "No se pudo buscar en el catálogo.".takeIf { state.search.error },
                    onRetry = { onAction(CreateFoodIntakeAction.SearchRetry) },
                    footer = state.search.footer,
                )

                state.lines.forEach { line ->
                    FoodLineRow(
                        line = line,
                        enabled = enabled,
                        scrollState = scrollState,
                        isImeVisible = isImeVisible,
                        onQuantityChange = {
                            onAction(CreateFoodIntakeAction.LineQuantityChanged(line.food.id, it))
                        },
                        onQuantityFocusChange = {
                            onAction(CreateFoodIntakeAction.LineQuantityFocusChanged(line.food.id, it))
                        },
                        onRemove = { onAction(CreateFoodIntakeAction.LineRemoved(line.food.id)) },
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs)) {
                    AlInput(
                        label = "Comentarios",
                        value = state.comments.value,
                        onValueChange = { onAction(CreateFoodIntakeAction.CommentsChanged(it)) },
                        onFocusChange = { onAction(CreateFoodIntakeAction.CommentsFocusChanged(it)) },
                        error = state.comments.displayError,
                        enabled = enabled,
                        singleLine = false,
                        scrollState = scrollState,
                        isImeVisible = isImeVisible,
                        placeholder = "Opcional",
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
                    .padding(top = 8.dp, bottom = AlTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.submitError?.let { error ->
                    Text(text = error, color = AlTheme.colors.danger, style = AlTheme.typography.body)
                }
                AlButton(
                    onClick = { onAction(CreateFoodIntakeAction.Submit) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.canSubmit,
                    loading = state.isSubmitting,
                    fullWidth = true,
                ) {
                    Text("Guardar")
                }
            }
        }
    }
}

private fun Food.toSuggestion(): AlSuggestion = AlSuggestion(
    id = id,
    title = name,
    subtitle = categoryName,
    trailing = unit.shortLabel,
)
