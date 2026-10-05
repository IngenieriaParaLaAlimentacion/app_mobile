package ar.com.ingenieriaalimentacion.presentation.feature.food.receipt_food_intake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.banner.AlBannerVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.banner.AlInlineBanner
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlIconButton
import ar.com.ingenieriaalimentacion.core.design_system.components.card.AlCard
import ar.com.ingenieriaalimentacion.core.design_system.components.dialog.AlConfirmDialog
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlAutocompleteInput
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlInput
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlSuggestion
import ar.com.ingenieriaalimentacion.core.design_system.components.input.DateVisualTransformation
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptError
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptPhoto
import com.komoui.components.Spinner
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_delete
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ReceiptFoodIntakeScreenRoot(
    initialSource: ReceiptCaptureSource?,
    navigate: (ReceiptFoodIntakeDestination) -> Unit,
    viewModel: ReceiptFoodIntakeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var initialCapturePending by remember(initialSource) { mutableStateOf(initialSource != null) }
    val picker = rememberReceiptImagePicker { result ->
        val wasInitialCapture = initialCapturePending
        initialCapturePending = false
        if (wasInitialCapture && result == null) {
            viewModel.onAction(ReceiptFoodIntakeAction.BackClick)
        } else {
            viewModel.onAction(ReceiptFoodIntakeAction.PhotoPicked(result))
        }
    }
    LaunchedEffect(initialSource) {
        when (initialSource) {
            ReceiptCaptureSource.Camera -> picker.launchCamera()
            ReceiptCaptureSource.Gallery -> picker.launchGallery()
            null -> Unit
        }
    }
    LaunchedEffect(state.destination) { state.destination?.let { navigate(it); viewModel.clearDestination() } }
    if (!initialCapturePending) {
        ReceiptFoodIntakeScreen(state, viewModel.previewPhoto()) { action ->
            when (action) {
                ReceiptFoodIntakeAction.CameraClick -> picker.launchCamera()
                ReceiptFoodIntakeAction.GalleryClick -> picker.launchGallery()
                else -> viewModel.onAction(action)
            }
        }
    }
}

@Composable
fun ReceiptFoodIntakeScreen(
    state: ReceiptFoodIntakeState,
    photo: ReceiptPhoto?,
    onAction: (ReceiptFoodIntakeAction) -> Unit,
) {
    val scroll = rememberScrollState()
    var showSourceSheet by remember { mutableStateOf(false) }
    AlScaffold {
        Column(Modifier.fillMaxSize().imePadding()) {
            AlBackTopAppBar(title = "Ingreso desde recibo", onBackClick = {
                onAction(ReceiptFoodIntakeAction.BackClick)
            }, enabled = !state.isSubmitting)
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(AlTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.lg)) {
                if (state.isLoadingProfile) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Spinner() }
                } else if (state.profileError != null) {
                    AlInlineBanner(state.profileError, variant = AlBannerVariant.Danger)
                    AlButton(onClick = { onAction(ReceiptFoodIntakeAction.RetryProfile) }) { Text("Reintentar") }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs)) {
                        Text("Establecimiento", style = AlTheme.typography.label,
                            color = AlTheme.colors.textMuted)
                        Text(state.selectedEstablishment?.name.orEmpty(), style = AlTheme.typography.body,
                            color = AlTheme.colors.textBase)
                    }
                }
                if (!state.isOnline) AlInlineBanner("Necesitás conexión para procesar y guardar el recibo.",
                    variant = AlBannerVariant.Danger)
                state.error?.let { AlInlineBanner(it, variant = AlBannerVariant.Danger) }
                when (state.step) {
                    ReceiptStep.Choose -> {
                        ReceiptPhotoHeading()
                        SourceChoices { showSourceSheet = true }
                    }
                    ReceiptStep.Preview -> {
                        ReceiptPhotoHeading()
                        if (photo != null) ReceiptPhotoPreview(
                            photo = photo,
                            token = state.photoToken,
                            onRemove = { onAction(ReceiptFoodIntakeAction.RemovePhoto) },
                            onChange = { showSourceSheet = true },
                        )
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
            if (state.step == ReceiptStep.Preview) {
                HorizontalDivider(color = AlTheme.colors.border)
                Column(Modifier.fillMaxWidth().padding(horizontal = AlTheme.spacing.lg)
                    .padding(top = AlTheme.spacing.md, bottom = AlTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                    AlButton(onClick = { onAction(ReceiptFoodIntakeAction.Analyze) },
                        modifier = Modifier.fillMaxWidth(), fullWidth = true,
                        enabled = state.isOnline && state.selectedEstablishment != null) {
                        Text("Procesar recibo")
                    }
                    AlButton(onClick = { onAction(ReceiptFoodIntakeAction.ManualFallback) },
                        modifier = Modifier.fillMaxWidth(), fullWidth = true,
                        variant = AlButtonVariant.Outline) {
                        Text("Cargar manualmente con esta foto")
                    }
                }
            }
        }
    }
    AlConfirmDialog(open = state.showDiscardDialog, title = "Descartar recibo",
        description = "Se perderán la foto y los alimentos cargados.", confirmText = "Descartar",
        onConfirm = { onAction(ReceiptFoodIntakeAction.ConfirmDiscard) },
        onDismiss = { onAction(ReceiptFoodIntakeAction.CancelDiscard) })
    if (showSourceSheet) ReceiptPhotoSourceSheet(
        onDismiss = { showSourceSheet = false },
        onSourceSelected = { source ->
            showSourceSheet = false
            onAction(when (source) {
                ReceiptCaptureSource.Camera -> ReceiptFoodIntakeAction.CameraClick
                ReceiptCaptureSource.Gallery -> ReceiptFoodIntakeAction.GalleryClick
            })
        },
    )
}

@Composable
private fun SourceChoices(open: () -> Unit) {
    AlCard(Modifier.fillMaxWidth(), contentPadding = AlTheme.spacing.lg) {
        Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.md)) {
            Text("Todavía no hay una foto seleccionada", style = AlTheme.typography.body,
                color = AlTheme.colors.textMuted)
            AlButton(onClick = open, modifier = Modifier.fillMaxWidth(), fullWidth = true) {
                Text("Elegir foto")
            }
        }
    }
}

@Composable
private fun ReceiptPhotoHeading() {
    Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
        Text("Foto del recibo", style = AlTheme.typography.subtitle,
            color = AlTheme.colors.textBase)
        Text("Revisá que el recibo esté completo y que el texto se pueda leer.",
            style = AlTheme.typography.body, color = AlTheme.colors.textMuted)
    }
}

@Composable
private fun ReviewForm(state: ReceiptFoodIntakeState, photo: ReceiptPhoto?,
    onAction: (ReceiptFoodIntakeAction) -> Unit) {
    val editable = !state.submissionLocked && !state.isSubmitting
    val focusManager = LocalFocusManager.current
    if (photo != null) ReceiptPhotoPreview(photo, state.photoToken,
        onRemove = { onAction(ReceiptFoodIntakeAction.RemovePhoto) })
    if (state.lines.isEmpty()) {
        Text(state.proposalMessage ?: "Agregá los alimentos del recibo.",
            style = AlTheme.typography.body, color = AlTheme.colors.textMuted)
    }
    AlInput(label = "Fecha de recepción", value = state.date.value,
        onValueChange = { onAction(ReceiptFoodIntakeAction.DateChanged(it)) },
        onFocusChange = { onAction(ReceiptFoodIntakeAction.DateFocusChanged(it)) },
        visualTransformation = DateVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        placeholder = "DD/MM/AAAA", error = state.date.displayError, enabled = editable)
    state.lines.forEach { line ->
        AlCard(Modifier.fillMaxWidth()) {
            val isSearching = state.searchLineId == line.id
            line.description?.let { description ->
                Text(description, style = AlTheme.typography.subtitle, color = AlTheme.colors.textBase)
            }
            if (line.rawQuantity != null || line.rawUnitText != null) {
                Text("En recibo: ${line.rawQuantity.orEmpty()} ${line.rawUnitText.orEmpty()}",
                    style = AlTheme.typography.caption, color = AlTheme.colors.textMuted)
            }
            line.resolutionError?.let { Text(it, color = AlTheme.colors.danger) }
            val suggestions = remember(isSearching, state.search.results) {
                if (isSearching) state.search.results.map {
                    AlSuggestion(it.id, it.name, it.categoryName, it.unit.shortLabel)
                } else emptyList()
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
                verticalAlignment = Alignment.Top) {
                AlAutocompleteInput(modifier = Modifier.weight(1f), label = "Alimento",
                    value = if (isSearching) state.search.query else line.food?.name.orEmpty(),
                    placeholder = "Buscá por nombre",
                    onValueChange = { onAction(ReceiptFoodIntakeAction.FoodSearchChanged(line.id, it)) },
                    onFocusChange = { onAction(ReceiptFoodIntakeAction.FoodSearchFocusChanged(line.id, it)) },
                    suggestions = suggestions, expanded = isSearching && state.search.isExpanded,
                    isLoading = isSearching && state.search.isSearching,
                    enabled = editable && state.isOnline,
                    errorText = "No se pudo buscar en el catálogo.".takeIf { isSearching && state.search.error },
                    onRetry = { onAction(ReceiptFoodIntakeAction.SearchRetry) },
                    footer = state.search.footer.takeIf { isSearching },
                    onSuggestionClick = { suggestion -> state.search.results.firstOrNull { it.id == suggestion.id }
                        ?.let { onAction(ReceiptFoodIntakeAction.FoodPicked(line.id, it)); focusManager.clearFocus() } })
                AlIconButton(icon = Res.drawable.ic_mingcute_delete, modifier = Modifier.size(48.dp),
                    contentDescription = "Quitar ${line.food?.name ?: line.description ?: "alimento"}",
                    onClick = { onAction(ReceiptFoodIntakeAction.LineRemoved(line.id)) },
                    tint = AlTheme.colors.textMuted, enabled = editable)
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
