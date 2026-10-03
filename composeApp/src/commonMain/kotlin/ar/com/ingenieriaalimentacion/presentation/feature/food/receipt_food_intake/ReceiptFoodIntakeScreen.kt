package ar.com.ingenieriaalimentacion.presentation.feature.food.receipt_food_intake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.banner.AlBannerVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.banner.AlInlineBanner
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.card.AlCard
import ar.com.ingenieriaalimentacion.core.design_system.components.dialog.AlConfirmDialog
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlAutocompleteInput
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlInput
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlSuggestion
import ar.com.ingenieriaalimentacion.core.design_system.components.input.DateVisualTransformation
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.sheet.AlBottomSheet
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptError
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptPhoto
import com.komoui.components.Spinner
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ReceiptFoodIntakeScreenRoot(
    navigate: (ReceiptFoodIntakeDestination) -> Unit,
    viewModel: ReceiptFoodIntakeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberReceiptImagePicker { viewModel.onAction(ReceiptFoodIntakeAction.PhotoPicked(it)) }
    LaunchedEffect(state.destination) { state.destination?.let { navigate(it); viewModel.clearDestination() } }
    ReceiptFoodIntakeScreen(state, viewModel.previewPhoto()) { action ->
        when (action) {
            ReceiptFoodIntakeAction.CameraClick -> picker.launchCamera()
            ReceiptFoodIntakeAction.GalleryClick -> picker.launchGallery()
            else -> viewModel.onAction(action)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptFoodIntakeScreen(
    state: ReceiptFoodIntakeState,
    photo: ReceiptPhoto?,
    onAction: (ReceiptFoodIntakeAction) -> Unit,
) {
    val scroll = rememberScrollState()
    var showSourceSheet by remember { mutableStateOf(false) }
    val sourceSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    AlScaffold {
        Column(Modifier.fillMaxSize().imePadding()) {
            AlBackTopAppBar(title = "Ingreso desde recibo", onBackClick = {
                onAction(ReceiptFoodIntakeAction.BackClick)
            }, enabled = !state.isSubmitting)
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(AlTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.md)) {
                if (state.isLoadingProfile) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Spinner() }
                } else if (state.profileError != null) {
                    AlInlineBanner(state.profileError, variant = AlBannerVariant.Danger)
                    AlButton(onClick = { onAction(ReceiptFoodIntakeAction.RetryProfile) }) { Text("Reintentar") }
                } else {
                    Text(state.selectedEstablishment?.name.orEmpty(), style = AlTheme.typography.subtitle,
                        color = AlTheme.colors.textBase)
                }
                if (!state.isOnline) AlInlineBanner("Necesitás conexión para procesar y guardar el recibo.",
                    variant = AlBannerVariant.Danger)
                state.error?.let { AlInlineBanner(it, variant = AlBannerVariant.Danger) }
                when (state.step) {
                    ReceiptStep.Choose -> SourceChoices { showSourceSheet = true }
                    ReceiptStep.Preview -> {
                        if (photo != null) ReceiptPhotoPreview(photo, state.photoToken) {
                            onAction(ReceiptFoodIntakeAction.RemovePhoto)
                        }
                        SourceChoices { showSourceSheet = true }
                        AlButton(onClick = { onAction(ReceiptFoodIntakeAction.Analyze) },
                            enabled = state.isOnline && state.selectedEstablishment != null) { Text("Procesar recibo") }
                        AlButton(onClick = { onAction(ReceiptFoodIntakeAction.ManualFallback) },
                            variant = AlButtonVariant.Outline) { Text("Cargar manualmente con esta foto") }
                    }
                    ReceiptStep.Processing -> {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Spinner() }
                        Text("Analizando el recibo…", style = AlTheme.typography.body,
                            color = AlTheme.colors.textMuted)
                        AlButton(onClick = { onAction(ReceiptFoodIntakeAction.CancelAnalysis) },
                            variant = AlButtonVariant.Outline) { Text("Cancelar") }
                    }
                    ReceiptStep.Review -> ReviewForm(state, photo, onAction)
                }
            }
        }
    }
    AlConfirmDialog(open = state.showDiscardDialog, title = "Descartar recibo",
        description = "Se perderán la foto y los alimentos cargados.", confirmText = "Descartar",
        onConfirm = { onAction(ReceiptFoodIntakeAction.ConfirmDiscard) },
        onDismiss = { onAction(ReceiptFoodIntakeAction.CancelDiscard) })
    if (showSourceSheet) AlBottomSheet(onDismissRequest = { showSourceSheet = false },
        sheetState = sourceSheetState) {
        Column(Modifier.fillMaxWidth().padding(AlTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.md)) {
            Text("Elegir foto del recibo", style = AlTheme.typography.subtitle,
                color = AlTheme.colors.textBase)
            AlButton(onClick = { scope.launch { sourceSheetState.hide(); showSourceSheet = false
                onAction(ReceiptFoodIntakeAction.CameraClick) } },
                modifier = Modifier.fillMaxWidth()) { Text("Tomar foto") }
            AlButton(onClick = { scope.launch { sourceSheetState.hide(); showSourceSheet = false
                onAction(ReceiptFoodIntakeAction.GalleryClick) } },
                modifier = Modifier.fillMaxWidth(), variant = AlButtonVariant.Outline) {
                Text("Elegir de galería")
            }
        }
    }
}

@Composable
private fun SourceChoices(open: () -> Unit) {
    AlButton(onClick = open, variant = AlButtonVariant.Outline) { Text("Elegir foto") }
}

@Composable
private fun ReviewForm(state: ReceiptFoodIntakeState, photo: ReceiptPhoto?,
    onAction: (ReceiptFoodIntakeAction) -> Unit) {
    val editable = !state.submissionLocked && !state.isSubmitting
    if (photo != null) ReceiptPhotoPreview(photo, state.photoToken) {
        onAction(ReceiptFoodIntakeAction.RemovePhoto)
    }
    if (state.lines.isEmpty()) {
        Text(state.proposalMessage ?: "No encontramos alimentos. Agregalos manualmente con esta foto.",
            style = AlTheme.typography.body, color = AlTheme.colors.textMuted)
        AlButton(onClick = { onAction(ReceiptFoodIntakeAction.ManualFallback) },
            variant = AlButtonVariant.Outline, enabled = editable) { Text("Cargar manualmente con esta foto") }
    }
    AlInput(label = "Fecha de recepción", value = state.date.value,
        onValueChange = { onAction(ReceiptFoodIntakeAction.DateChanged(it)) },
        onFocusChange = { onAction(ReceiptFoodIntakeAction.DateFocusChanged(it)) },
        visualTransformation = DateVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        placeholder = "DD/MM/AAAA", error = state.date.displayError, enabled = editable)
    state.lines.forEach { line ->
        AlCard(Modifier.fillMaxWidth()) {
            Text(line.description, style = AlTheme.typography.subtitle, color = AlTheme.colors.textBase)
            if (line.rawQuantity != null || line.rawUnitText != null) {
                Text("En recibo: ${line.rawQuantity.orEmpty()} ${line.rawUnitText.orEmpty()}",
                    style = AlTheme.typography.caption, color = AlTheme.colors.textMuted)
            }
            line.resolutionError?.let { Text(it, color = AlTheme.colors.danger) }
            Text(line.food?.let { "${it.name} · ${it.unit.shortLabel}" } ?: "Elegí un alimento del catálogo",
                color = AlTheme.colors.textBase)
            AlButton(onClick = { onAction(ReceiptFoodIntakeAction.SearchForLine(line.id)) },
                variant = AlButtonVariant.Outline, enabled = editable) { Text("Buscar alimento") }
            if (state.searchLineId == line.id) {
                val suggestions = remember(state.search.results) { state.search.results.map {
                    AlSuggestion(it.id, it.name, it.categoryName, it.unit.shortLabel)
                } }
                AlAutocompleteInput(label = "Alimento", value = state.search.query,
                    onValueChange = { onAction(ReceiptFoodIntakeAction.FoodSearchChanged(it)) },
                    onFocusChange = { onAction(ReceiptFoodIntakeAction.FoodSearchFocusChanged(it)) },
                    suggestions = suggestions, expanded = state.search.isExpanded,
                    isLoading = state.search.isSearching, enabled = editable && state.isOnline,
                    errorText = "No se pudo buscar en el catálogo.".takeIf { state.search.error },
                    onRetry = { onAction(ReceiptFoodIntakeAction.SearchRetry) }, footer = state.search.footer,
                    onSuggestionClick = { suggestion -> state.search.results.firstOrNull { it.id == suggestion.id }
                        ?.let { onAction(ReceiptFoodIntakeAction.FoodPicked(line.id, it)) } })
            }
            AlInput(label = "Cantidad (${line.food?.unit?.shortLabel ?: "elegí alimento"})",
                value = line.quantity.value,
                onValueChange = { onAction(ReceiptFoodIntakeAction.QuantityChanged(line.id, it)) },
                onFocusChange = { onAction(ReceiptFoodIntakeAction.QuantityFocusChanged(line.id, it)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                error = line.quantity.displayError, enabled = editable && line.food != null)
            AlButton(onClick = { onAction(ReceiptFoodIntakeAction.LineConfirmed(line.id)) },
                variant = if (line.reviewed) AlButtonVariant.Default else AlButtonVariant.Outline,
                enabled = editable && line.food != null) {
                Text(if (line.reviewed) "Revisado ✓" else "Marcar como revisado")
            }
            AlButton(onClick = { onAction(ReceiptFoodIntakeAction.LineRemoved(line.id)) },
                variant = AlButtonVariant.Outline, enabled = editable) { Text("Quitar línea") }
        }
    }
    AlButton(onClick = { onAction(ReceiptFoodIntakeAction.AddLine) },
        variant = AlButtonVariant.Outline, enabled = editable) { Text("Agregar alimento") }
    AlInput(label = "Comentarios (opcional)", value = state.comments.value,
        onValueChange = { onAction(ReceiptFoodIntakeAction.CommentsChanged(it)) },
        onFocusChange = { onAction(ReceiptFoodIntakeAction.CommentsFocusChanged(it)) },
        error = state.comments.displayError, enabled = editable, singleLine = false)
    Text("${state.comments.value.length}/500", color = AlTheme.colors.textMuted)
    if (state.submissionLocked) {
        val retryable = state.submitFailureKind == ReceiptError.Retryable
        if (retryable) AlButton(onClick = { onAction(ReceiptFoodIntakeAction.RetrySubmit) },
            enabled = state.isOnline && !state.isSubmitting) { Text("Reintentar guardado") }
        else if (!state.isSubmitting) AlButton(onClick = { onAction(ReceiptFoodIntakeAction.ReviseAfterFailure) }) {
            Text("Revisar datos")
        }
    } else {
        AlButton(onClick = { onAction(ReceiptFoodIntakeAction.Submit) },
            enabled = state.isOnline && !state.isSubmitting,
            loading = state.isSubmitting) { Text("Guardar ingreso") }
    }
}
