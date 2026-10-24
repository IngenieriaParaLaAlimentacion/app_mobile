package ar.com.ingenieriaalimentacion.presentation.feature.food.recipes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.banner.AlBannerVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.banner.AlInlineBanner
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.card.AlCard
import ar.com.ingenieriaalimentacion.core.design_system.components.dialog.AlConfirmDialog
import ar.com.ingenieriaalimentacion.core.design_system.components.dialog.AlConfirmTone
import ar.com.ingenieriaalimentacion.core.design_system.components.header.AlSectionLabel
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlInput
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.recipe.*
import ar.com.ingenieriaalimentacion.presentation.common.formatQuantityWithUnit
import com.komoui.components.Spinner
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RecipesScreenRoot(
    savedStateHandle: SavedStateHandle,
    onStockChanged: () -> Unit,
    navigate: (RecipesDestination) -> Unit,
    viewModel: RecipesViewModel = koinViewModel(parameters = { parametersOf(savedStateHandle) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }
    LaunchedEffect(state.mutationVersion) {
        if (state.mutationVersion > 0) onStockChanged()
    }
    RecipesScreen(state, viewModel::onAction)
}

@Composable
fun RecipesScreen(state: RecipesState, onAction: (RecipesAction) -> Unit) {
    AlScaffold {
        Column(Modifier.fillMaxSize()) {
            AlBackTopAppBar(
                title = if (state.detail != null) "Receta elegida" else "Recetas",
                onBackClick = { onAction(RecipesAction.BackClick) },
            )
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(AlTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.md),
            ) {
                when {
                    state.isLoading -> Spinner()
                    state.scopeError != null -> {
                        AlInlineBanner(state.scopeError, variant = AlBannerVariant.Warning)
                        AlButton(onClick = { onAction(RecipesAction.RetryEstablishment) }) {
                            Text("Reintentar")
                        }
                    }
                    else -> {
                        Text(
                            state.establishment?.name.orEmpty(),
                            style = AlTheme.typography.subtitle,
                            color = AlTheme.colors.textBase,
                        )
                        if (!state.isOnline)
                            AlInlineBanner(
                                "Necesitás conexión para generar recetas, elegir una o volver a cocinarla.",
                                variant = AlBannerVariant.Warning,
                            )
                        state.successMessage?.let { AlInlineBanner(it) }
                        state.screenError?.let { RecipeErrorBanner(it, state, onAction) }
                        if (state.pendingMutation != null) {
                            AlInlineBanner(
                                "La confirmación quedó pendiente. Reintentá la misma operación para comprobarla sin descontar el stock dos veces.",
                                variant = AlBannerVariant.Warning,
                            )
                            AlButton(
                                onClick = { onAction(RecipesAction.RetryConfirmation) },
                                enabled =
                                    state.isOnline &&
                                        !state.isBusy &&
                                        state.pendingMutation.establishmentId ==
                                            state.establishment?.id,
                                loading = state.isSubmitting,
                                fullWidth = true,
                            ) {
                                Text("Reintentar confirmación")
                            }
                            if (state.pendingMutation.establishmentId != state.establishment?.id)
                                Text(
                                    "Volvé a seleccionar el establecimiento de la confirmación pendiente en Inicio."
                                )
                        }
                        if (state.detail != null) {
                            HistoryDetail(state, onAction)
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                                RecipesTab.entries.forEach { tab ->
                                    AlButton(
                                        onClick = { onAction(RecipesAction.TabSelected(tab)) },
                                        enabled = !state.isBusy,
                                        variant =
                                            if (state.tab == tab) AlButtonVariant.Default
                                            else AlButtonVariant.Outline,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            if (tab == RecipesTab.Generate) "Generar"
                                            else "Historial"
                                        )
                                    }
                                }
                            }
                            when (state.tab) {
                                RecipesTab.Generate -> GenerateContent(state, onAction)
                                RecipesTab.History -> HistoryContent(state, onAction)
                            }
                        }
                    }
                }
            }
        }
        state.confirmation?.let { confirmation ->
            AlConfirmDialog(
                open = true,
                title = "¿Cocinar esta receta?",
                description =
                    "${confirmation.name}, para ${confirmation.portions} chicos. Al confirmar se registrará una salida y se descontarán los ingredientes del stock.",
                confirmText = "Confirmar y registrar salida",
                confirmTone = AlConfirmTone.Neutral,
                loading = state.isSubmitting,
                onConfirm = { onAction(RecipesAction.Confirm) },
                onDismiss = { onAction(RecipesAction.DismissConfirmation) },
            )
        }
    }
}

@Composable
private fun GenerateContent(state: RecipesState, onAction: (RecipesAction) -> Unit) {
    AlInput(
        value = state.children.value,
        onValueChange = { onAction(RecipesAction.ChildrenChanged(it)) },
        label = "¿Cuántos chicos comen hoy?",
        required = true,
        enabled = state.canEdit,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        error = state.children.displayError,
        onFocusChange = { onAction(RecipesAction.ChildrenFocusChanged(it)) },
    )
    AlSectionLabel("Tipo de comida")
    MealType.entries.chunked(2).forEach { meals ->
        Row(horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
            meals.forEach { meal ->
                AlButton(
                    onClick = { onAction(RecipesAction.MealSelected(meal)) },
                    enabled = state.canEdit,
                    variant =
                        if (state.mealType == meal) AlButtonVariant.Default
                        else AlButtonVariant.Outline,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(meal.label)
                }
            }
        }
    }
    AlButton(
        onClick = { onAction(RecipesAction.Generate) },
        enabled = state.canGenerate,
        loading = state.isGenerating,
        fullWidth = true,
    ) {
        Text("Buscar propuestas")
    }
    if (state.isGenerating)
        Text(
            "Estamos preparando las propuestas. Puede tardar unos minutos.",
            style = AlTheme.typography.body,
            color = AlTheme.colors.textMuted,
        )
    state.request?.let { request ->
        if (request.selection != null)
            AlInlineBanner("Ya elegiste una receta de este pedido. Podés verla en el historial.")
        request.proposals.forEach { recipe ->
            RecipeCard(recipe) {
                AlButton(
                    onClick = { onAction(RecipesAction.Choose(recipe.id)) },
                    enabled =
                        state.canEdit &&
                            state.isOnline &&
                            request.selection == null &&
                            request.id !in state.blockedRequestIds,
                    fullWidth = true,
                ) {
                    Text(
                        if (request.selection?.proposalId == recipe.id) "Elegida"
                        else "Elegir esta receta"
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryContent(state: RecipesState, onAction: (RecipesAction) -> Unit) {
    Text("Recetas elegidas", style = AlTheme.typography.title, color = AlTheme.colors.textBase)
    AlButton(
        onClick = { onAction(RecipesAction.ReloadHistory) },
        enabled = state.isOnline && !state.isHistoryLoading,
        variant = AlButtonVariant.Outline,
    ) {
        Text("Actualizar")
    }
    state.historyError?.let {
        AlInlineBanner(recipeErrorMessage(it, emptyList()), variant = AlBannerVariant.Danger)
    }
    if (state.history.isEmpty() && !state.isHistoryLoading)
        Text(
            "Todavía no hay recetas elegidas para este establecimiento.",
            style = AlTheme.typography.body,
            color = AlTheme.colors.textMuted,
        )
    state.history.forEach { request ->
        request.proposals.forEach { proposal ->
            AlCard(Modifier.fillMaxWidth()) {
                Text(
                    proposal.name,
                    style = AlTheme.typography.subtitle,
                    color = AlTheme.colors.textBase,
                )
                Text(
                    "${proposal.portions} porciones · ${mealLabel(request.mealType)} · ${recipeDate(request.createdAt)}",
                    style = AlTheme.typography.caption,
                    color = AlTheme.colors.textMuted,
                )
                AlButton(
                    onClick = { onAction(RecipesAction.ShowRecipe(request.id, proposal.id)) },
                    enabled = state.canEdit,
                    variant = AlButtonVariant.Outline,
                ) {
                    Text("Ver receta")
                }
            }
        }
    }
    if (state.isHistoryLoading) Spinner()
    if (state.historyHasMore)
        AlButton(
            onClick = { onAction(RecipesAction.LoadMoreHistory) },
            enabled = state.isOnline && !state.isHistoryLoading,
            variant = AlButtonVariant.Outline,
            fullWidth = true,
        ) {
            Text("Cargar más recetas")
        }
}

@Composable
private fun HistoryDetail(state: RecipesState, onAction: (RecipesAction) -> Unit) {
    val detail = state.detail ?: return
    RecipeCard(detail.proposal)
    AlSectionLabel("Volver a cocinar")
    AlInput(
        value = state.reuseChildren.value,
        onValueChange = { onAction(RecipesAction.ReuseChildrenChanged(it)) },
        label = "Cantidad de chicos de hoy",
        required = true,
        enabled = state.canEdit,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        error = state.reuseChildren.displayError,
        onFocusChange = { onAction(RecipesAction.ReuseChildrenFocusChanged(it)) },
    )
    AlButton(
        onClick = { onAction(RecipesAction.PreviewReuse) },
        enabled = state.canPreviewReuse,
        loading = state.isPreviewing,
        fullWidth = true,
        variant = AlButtonVariant.Outline,
    ) {
        Text("Ver cantidades para hoy")
    }
    state.reusePreview?.let { preview ->
        AlSectionLabel("Vista previa para hoy")
        Text(
            "Todavía no se descontó ningún alimento del stock.",
            style = AlTheme.typography.caption,
            color = AlTheme.colors.textMuted,
        )
        RecipeCard(preview) {
            AlButton(
                onClick = { onAction(RecipesAction.ConfirmReuse) },
                enabled = state.canEdit && state.isOnline,
                fullWidth = true,
            ) {
                Text("Cocinar esta receta")
            }
        }
    }
}

@Composable
internal fun RecipeCard(recipe: RecipeProposal, actions: (@Composable () -> Unit)? = null) {
    var expanded by rememberSaveable(recipe.id) { mutableStateOf(false) }
    AlCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
            Text(recipe.name, style = AlTheme.typography.title, color = AlTheme.colors.textBase)
            Text(
                "${recipe.portions} porciones",
                style = AlTheme.typography.data,
                color = AlTheme.colors.textMuted,
            )
            recipe.culturalIdentity?.let { identity ->
                Text(
                    when (identity) {
                        "paraguayan" -> "Inspiración paraguaya"
                        "bolivian" -> "Inspiración boliviana"
                        else -> "Inspiración cultural: $identity"
                    },
                    style = AlTheme.typography.caption,
                    color = AlTheme.colors.accent,
                )
            }
            Text(
                "Energía: ${recipeNumber(recipe.energyKcal)} de ${recipeNumber(recipe.targetEnergyKcal)} kcal previstas",
                style = AlTheme.typography.dataSmall,
                color = AlTheme.colors.textBase,
            )
            if (recipe.unmetConstraints.isNotEmpty())
                AlInlineBanner(
                    "Esta propuesta no alcanza todos los objetivos. Revisá los faltantes antes de elegirla.",
                    variant = AlBannerVariant.Warning,
                )
            recipe.unmetConstraints.forEach { shortfall ->
                Text(
                    shortfallMessage(shortfall),
                    style = AlTheme.typography.caption,
                    color = AlTheme.colors.warning,
                )
            }
            recipeWarningMessages(recipe.warnings).forEach { message ->
                AlInlineBanner(message, variant = AlBannerVariant.Warning)
            }
            AlSectionLabel("Ingredientes · cantidades totales")
            recipe.ingredients.forEach { ingredient ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        ingredient.name,
                        modifier = Modifier.weight(1f),
                        style = AlTheme.typography.body,
                        color = AlTheme.colors.textBase,
                    )
                    Text(
                        formatQuantityWithUnit(ingredient.quantity, ingredient.unit),
                        style = AlTheme.typography.dataSmall,
                        color = AlTheme.colors.textBase,
                    )
                }
            }
            AlButton(
                onClick = { expanded = !expanded },
                variant = AlButtonVariant.Outline,
                fullWidth = true,
            ) {
                Text(
                    if (expanded) "Ocultar preparación y nutrientes"
                    else "Ver preparación y nutrientes"
                )
            }
            if (expanded) {
                AlSectionLabel("Preparación")
                recipe.steps.forEachIndexed { index, step ->
                    Text(
                        "${index + 1}. $step",
                        style = AlTheme.typography.body,
                        color = AlTheme.colors.textBase,
                    )
                }
                AlSectionLabel("Adecuación por nutriente")
                Text(
                    "Porcentaje respecto de la referencia nutricional por 1.000 kcal para chicos de 7 a 9 años.",
                    style = AlTheme.typography.caption,
                    color = AlTheme.colors.textMuted,
                )
                recipe.nutrientAdequacyPercent.forEach { (name, percent) ->
                    NutrientRow(nutrientLabel(name), percent)
                }
                AlSectionLabel("Distribución de macronutrientes")
                recipe.macronutrientPercent.forEach { (name, percent) ->
                    NutrientRow(nutrientLabel(name), percent)
                }
                AlSectionLabel("Grupos de alimentos cubiertos")
                Text(
                    recipe.gapaGroups
                        .joinToString(" · ") { gapaLabel(it) }
                        .ifEmpty { "Sin grupos identificados" },
                    style = AlTheme.typography.body,
                    color = AlTheme.colors.textBase,
                )
            }
            actions?.invoke()
        }
    }
}

@Composable
private fun NutrientRow(label: String, percent: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = AlTheme.typography.body, color = AlTheme.colors.textBase)
        Text(
            "${recipeNumber(percent)} %",
            style = AlTheme.typography.dataSmall,
            color = AlTheme.colors.textBase,
        )
    }
}

@Composable
private fun RecipeErrorBanner(
    error: RecipeError,
    state: RecipesState,
    onAction: (RecipesAction) -> Unit,
) {
    val ingredients =
        state.request?.proposals.orEmpty().flatMap { it.ingredients } +
            state.detail?.proposal?.ingredients.orEmpty() +
            state.reusePreview?.ingredients.orEmpty()
    AlInlineBanner(recipeErrorMessage(error, ingredients), variant = AlBannerVariant.Danger)
    if (state.pendingMutation == null && state.detail == null && state.tab == RecipesTab.Generate) {
        if (error == RecipeError.AlreadyChosen) {
            AlButton(
                onClick = { onAction(RecipesAction.TabSelected(RecipesTab.History)) },
                variant = AlButtonVariant.Outline,
            ) {
                Text("Ver historial")
            }
        } else {
            AlButton(
                onClick = { onAction(RecipesAction.Generate) },
                enabled = state.canGenerate,
                variant = AlButtonVariant.Outline,
            ) {
                Text("Reintentar")
            }
        }
    }
}
