package ar.com.ingenieriaalimentacion.presentation.feature.food.recipes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextButton
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RecipesScreen(state: RecipesState, onAction: (RecipesAction) -> Unit) {
    val proposal = state.request?.proposals?.firstOrNull { it.id == state.selectedProposalId }
    val listScroll = rememberScrollState()
    val detailScroll = rememberScrollState()
    val scrollState = if (state.detail != null || proposal != null) detailScroll else listScroll
    LaunchedEffect(state.selectedProposalId, state.detail?.requestId) {
        detailScroll.scrollTo(0)
    }
    LaunchedEffect(state.tab, state.request?.id) {
        listScroll.scrollTo(0)
    }
    BackHandler(enabled = state.detail != null || proposal != null || state.isSubmitting) {
        onAction(RecipesAction.BackClick)
    }
    AlScaffold {
        Column(Modifier.fillMaxSize()) {
            AlBackTopAppBar(
                title = when {
                    state.detail != null -> "Volver a preparar"
                    proposal != null -> "Detalle de receta"
                    else -> "Recetas con tu stock"
                },
                onBackClick = { onAction(RecipesAction.BackClick) },
            )
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(scrollState)
                    .imePadding()
                    .padding(AlTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.lg),
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
                            style = AlTheme.typography.caption,
                            color = AlTheme.colors.textBase,
                        )
                        if (!state.isOnline)
                            AlInlineBanner(
                                "Necesitás conexión para buscar recetas o registrar una preparación.",
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
                        when {
                            state.detail != null -> HistoryDetail(state, onAction)
                            proposal != null -> {
                                RecipeCard(proposal) {
                                    RegistrationAction(
                                        enabled = state.canEdit && state.isOnline &&
                                            state.request?.selection == null &&
                                            state.request?.id !in state.blockedRequestIds,
                                        registered = state.request?.selection?.proposalId == proposal.id,
                                        onClick = { onAction(RecipesAction.Choose(proposal.id)) },
                                    )
                                }
                            }
                            else -> {
                                TabRow(
                                    selectedTabIndex = state.tab.ordinal,
                                    containerColor = AlTheme.colors.surfaceBase,
                                    contentColor = AlTheme.colors.accent,
                                ) {
                                    RecipesTab.entries.forEach { tab ->
                                        Tab(
                                            selected = state.tab == tab,
                                            onClick = { onAction(RecipesAction.TabSelected(tab)) },
                                            enabled = !state.isBusy,
                                            text = { Text(if (tab == RecipesTab.Generate) "Propuestas" else "Historial", style = AlTheme.typography.label) },
                                        )
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
        }
        state.confirmation?.let { confirmation ->
            AlConfirmDialog(
                open = true,
                title = "¿Registrar esta preparación?",
                description =
                    "${confirmation.name}\nPara ${confirmation.portions} chicos en ${state.establishment?.name.orEmpty()}.\n\nSe registrará una salida y se descontarán los ingredientes del stock.",
                confirmText = "Confirmar y descontar stock",
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
    val request = state.request
    if (state.isEditingRequest || request == null) {
        AlCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.lg)) {
                Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                    Text("¿Qué vamos a preparar?", style = AlTheme.typography.title, color = AlTheme.colors.textBase)
                    Text("Buscamos recetas con los alimentos disponibles en tu establecimiento.", style = AlTheme.typography.body, color = AlTheme.colors.textMuted)
                }
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
                Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                    AlSectionLabel("Tipo de comida")
                    MealType.entries.chunked(2).forEach { meals ->
                        Row(horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                            meals.forEach { meal ->
                                androidx.compose.material3.FilterChip(
                                    selected = state.mealType == meal,
                                    onClick = { onAction(RecipesAction.MealSelected(meal)) },
                                    enabled = state.canEdit,
                                    label = { Text(meal.label, style = AlTheme.typography.label) },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                )
                            }
                        }
                    }
                }
                AlButton(
                    onClick = { onAction(RecipesAction.Generate) },
                    enabled = state.canGenerate,
                    loading = state.isGenerating,
                    fullWidth = true,
                ) { Text("Buscar recetas") }
            }
        }
    } else {
        AlCard(Modifier.fillMaxWidth()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
            ) {
                Column(Modifier.align(Alignment.CenterVertically), verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs)) {
                    AlSectionLabel("Tu búsqueda")
                    Text("${mealLabel(request.mealType)} · ${request.childrenCount} chicos", style = AlTheme.typography.bodyStrong, color = AlTheme.colors.textBase)
                }
                TextButton(onClick = { onAction(RecipesAction.EditRequest) }, enabled = state.canEdit) {
                    Text("Cambiar", color = AlTheme.colors.accent)
                }
            }
        }
    }
    if (state.isGenerating) {
        Text("Estamos preparando las propuestas. Puede tardar unos minutos.", style = AlTheme.typography.body, color = AlTheme.colors.textMuted)
    }
    request?.let {
        Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
            Text("Propuestas para hoy", style = AlTheme.typography.title, color = AlTheme.colors.textBase)
            Text("${request.proposals.size} ${if (request.proposals.size == 1) "opción" else "opciones"} para comparar. Consultar una receta no modifica el stock.", style = AlTheme.typography.body, color = AlTheme.colors.textMuted)
            if (state.isEditingRequest) {
                Text("Estas propuestas corresponden a ${mealLabel(request.mealType).lowercase()} para ${request.childrenCount} chicos. Buscá nuevamente para aplicar los cambios.", style = AlTheme.typography.caption, color = AlTheme.colors.textMuted)
            }
        }
        if (request.selection != null) {
            AlInlineBanner("La preparación ya está registrada. Podés volver a prepararla desde el historial.")
        }
        request.proposals.forEach { recipe ->
            RecipeSummaryCard(recipe, registered = request.selection?.proposalId == recipe.id) {
                onAction(RecipesAction.ShowProposal(recipe.id))
            }
        }
    }
}

@Composable
private fun RecipeSummaryCard(recipe: RecipeProposal, registered: Boolean, onClick: () -> Unit) {
    AlCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.md)) {
            RecipeHeading(recipe, compact = true)
            val observations = recipe.unmetConstraints.size + recipeWarningMessages(recipe.warnings).size
            if (observations > 0) {
                Text(
                    "Revisar $observations ${if (observations == 1) "observación" else "observaciones"} antes de preparar",
                    style = AlTheme.typography.bodyStrong,
                    color = AlTheme.colors.warning,
                )
            }
            if (registered) Text("Preparación registrada", style = AlTheme.typography.label, color = AlTheme.colors.accent)
            AlButton(onClick = onClick, fullWidth = true, variant = AlButtonVariant.Outline) { Text("Ver receta") }
        }
    }
}

@Composable
private fun HistoryContent(state: RecipesState, onAction: (RecipesAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
        Text("Preparaciones registradas", style = AlTheme.typography.title, color = AlTheme.colors.textBase)
        Text("Consultá lo que prepararon y ajustá las cantidades para volver a cocinarlo.", style = AlTheme.typography.body, color = AlTheme.colors.textMuted)
        TextButton(
            onClick = { onAction(RecipesAction.ReloadHistory) },
            enabled = state.isOnline && !state.isHistoryLoading,
        ) { Text("Actualizar historial", color = AlTheme.colors.accent) }
    }
    state.historyError?.let {
        AlInlineBanner(recipeErrorMessage(it, emptyList()), variant = AlBannerVariant.Danger)
    }
    if (state.history.isEmpty() && !state.isHistoryLoading && state.historyError == null) {
        Text(
            "Todavía no hay preparaciones registradas. Buscá una receta en Propuestas para empezar.",
            style = AlTheme.typography.body,
            color = AlTheme.colors.textMuted,
        )
    }
    state.history.forEach { request ->
        request.proposals.filter { it.id == request.selection?.proposalId }.forEach { proposal ->
            AlCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.md)) {
                    Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs)) {
                        Text(proposal.name, style = AlTheme.typography.subtitle, color = AlTheme.colors.textBase)
                        Text(
                            "${proposal.portions} porciones · ${mealLabel(request.mealType)} · ${recipeDate(request.createdAt)}",
                            style = AlTheme.typography.caption,
                            color = AlTheme.colors.textMuted,
                        )
                    }
                    AlButton(
                        onClick = { onAction(RecipesAction.ShowRecipe(request.id, proposal.id)) },
                        enabled = state.canEdit,
                        fullWidth = true,
                        variant = AlButtonVariant.Outline,
                    ) { Text("Ver receta") }
                }
            }
        }
    }
    if (state.isHistoryLoading) Spinner()
    if (state.historyHasMore) {
        AlButton(
            onClick = { onAction(RecipesAction.LoadMoreHistory) },
            enabled = state.isOnline && !state.isHistoryLoading,
            variant = AlButtonVariant.Outline,
            fullWidth = true,
        ) { Text("Cargar más recetas") }
    }
}

@Composable
private fun HistoryDetail(state: RecipesState, onAction: (RecipesAction) -> Unit) {
    val detail = state.detail ?: return
    AlCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.md)) {
            Text("Ajustar para hoy", style = AlTheme.typography.subtitle, color = AlTheme.colors.textBase)
            AlInput(
                value = state.reuseChildren.value,
                onValueChange = { onAction(RecipesAction.ReuseChildrenChanged(it)) },
                label = "¿Cuántos chicos comen hoy?",
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
            ) { Text("Calcular cantidades para hoy") }
        }
    }
    Text(
        if (state.reusePreview != null) "Cantidades actualizadas para hoy. Todavía no se descontó stock."
        else "Cantidades de la preparación anterior. Calculá las de hoy antes de registrar.",
        style = AlTheme.typography.body,
        color = AlTheme.colors.textMuted,
    )
    RecipeCard(state.reusePreview ?: detail.proposal) {
        if (state.reusePreview != null) {
            RegistrationAction(
                enabled = state.canEdit && state.isOnline,
                onClick = { onAction(RecipesAction.ConfirmReuse) },
            )
        }
    }
}

@Composable
private fun RecipeHeading(recipe: RecipeProposal, compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs)) {
        Text(recipe.name, style = if (compact) AlTheme.typography.subtitle else AlTheme.typography.title, color = AlTheme.colors.textBase)
        Text("${recipe.portions} porciones · ${recipe.ingredients.size} ingredientes", style = AlTheme.typography.caption, color = AlTheme.colors.textMuted)
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
    }
}

@Composable
private fun RegistrationAction(enabled: Boolean, registered: Boolean = false, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
        Text(
            if (registered) "Esta preparación ya se registró. Para repetirla, usá el historial."
            else "Al confirmar, se registrará una salida y se descontarán los ingredientes del stock.",
            style = AlTheme.typography.caption,
            color = AlTheme.colors.textMuted,
        )
        AlButton(onClick = onClick, enabled = enabled && !registered, fullWidth = true) {
            Text(if (registered) "Preparación registrada" else "Registrar preparación")
        }
    }
}

@Composable
internal fun RecipeCard(recipe: RecipeProposal, actions: (@Composable () -> Unit)? = null) {
    var nutritionExpanded by rememberSaveable(recipe.id) { mutableStateOf(false) }
    AlCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.lg)) {
            RecipeHeading(recipe)
            val observations = recipe.unmetConstraints.map(::shortfallMessage) + recipeWarningMessages(recipe.warnings)
            if (observations.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(AlTheme.colors.warningContainer, RoundedCornerShape(AlTheme.corners.md))
                        .padding(AlTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
                ) {
                    Text("Antes de preparar", style = AlTheme.typography.bodyStrong, color = AlTheme.colors.warning)
                    observations.forEach { Text("• $it", style = AlTheme.typography.body, color = AlTheme.colors.textBase) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.md)) {
                Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs)) {
                    Text("Ingredientes", style = AlTheme.typography.subtitle, color = AlTheme.colors.textBase)
                    Text("Cantidades totales para ${recipe.portions} porciones", style = AlTheme.typography.caption, color = AlTheme.colors.textMuted)
                }
                Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                    recipe.ingredients.forEach { ingredient ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.md)) {
                            Text(ingredient.name, modifier = Modifier.weight(1f), style = AlTheme.typography.body, color = AlTheme.colors.textBase)
                            Text(formatQuantityWithUnit(ingredient.quantity, ingredient.unit), modifier = Modifier.widthIn(max = 140.dp), textAlign = TextAlign.End, style = AlTheme.typography.dataSmall, color = AlTheme.colors.textBase)
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.md)) {
                Text("Preparación", style = AlTheme.typography.subtitle, color = AlTheme.colors.textBase)
                recipe.steps.forEachIndexed { index, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                        Text("${index + 1}.", modifier = Modifier.width(24.dp), style = AlTheme.typography.dataSmall, color = AlTheme.colors.accent)
                        Text(step, modifier = Modifier.weight(1f), style = AlTheme.typography.body, color = AlTheme.colors.textBase)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.md)) {
                AlButton(onClick = { nutritionExpanded = !nutritionExpanded }, variant = AlButtonVariant.Outline, fullWidth = true) {
                    Text(if (nutritionExpanded) "Ocultar información nutricional" else "Ver información nutricional")
                }
                if (nutritionExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                        AlSectionLabel("Energía")
                        Text("${recipeNumber(recipe.energyKcal)} de ${recipeNumber(recipe.targetEnergyKcal)} kcal previstas", style = AlTheme.typography.dataSmall, color = AlTheme.colors.textBase)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                        AlSectionLabel("Adecuación por nutriente")
                        Text("Porcentaje respecto de la referencia nutricional por 1.000 kcal para chicos de 7 a 9 años.", style = AlTheme.typography.caption, color = AlTheme.colors.textMuted)
                        recipe.nutrientAdequacyPercent.forEach { (name, percent) -> NutrientRow(nutrientLabel(name), percent) }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                        AlSectionLabel("Distribución de macronutrientes")
                        recipe.macronutrientPercent.forEach { (name, percent) -> NutrientRow(nutrientLabel(name), percent) }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                        AlSectionLabel("Grupos de alimentos cubiertos")
                        Text(recipe.gapaGroups.joinToString(" · ") { gapaLabel(it) }.ifEmpty { "Sin grupos identificados" }, style = AlTheme.typography.body, color = AlTheme.colors.textBase)
                    }
                }
            }
            actions?.invoke()
        }
    }
}

@Composable
private fun NutrientRow(label: String, percent: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.md)) {
        Text(label, modifier = Modifier.weight(1f), style = AlTheme.typography.body, color = AlTheme.colors.textBase)
        Text("${recipeNumber(percent)} %", style = AlTheme.typography.dataSmall, color = AlTheme.colors.textBase)
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
        } else if (state.selectedProposalId == null) {
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
