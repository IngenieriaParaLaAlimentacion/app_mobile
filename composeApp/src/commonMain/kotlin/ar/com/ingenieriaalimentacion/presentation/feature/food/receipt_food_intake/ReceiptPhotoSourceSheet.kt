package ar.com.ingenieriaalimentacion.presentation.feature.food.receipt_food_intake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.sheet.AlBottomSheet
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptPhotoSourceSheet(
    onDismiss: () -> Unit,
    onSourceSelected: (ReceiptCaptureSource) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    fun select(source: ReceiptCaptureSource) {
        scope.launch {
            sheetState.hide()
            onSourceSelected(source)
        }
    }
    AlBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(AlTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.md)) {
            Text("Elegir foto del recibo", style = AlTheme.typography.subtitle,
                color = AlTheme.colors.textBase)
            AlButton(onClick = { select(ReceiptCaptureSource.Camera) },
                modifier = Modifier.fillMaxWidth()) { Text("Tomar foto") }
            AlButton(onClick = { select(ReceiptCaptureSource.Gallery) },
                modifier = Modifier.fillMaxWidth(), variant = AlButtonVariant.Outline) {
                Text("Elegir de galería")
            }
        }
    }
}
