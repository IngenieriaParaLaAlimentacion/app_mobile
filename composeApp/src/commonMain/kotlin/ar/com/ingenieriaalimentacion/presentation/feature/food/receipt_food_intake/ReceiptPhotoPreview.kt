package ar.com.ingenieriaalimentacion.presentation.feature.food.receipt_food_intake

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

expect fun decodeReceiptPreview(bytes: ByteArray): ImageBitmap?

@Composable
fun ReceiptPhotoPreview(photo: ReceiptPhoto, token: Int, onRemove: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, token) {
        value = withContext(Dispatchers.Default) { decodeReceiptPreview(photo.bytes) }
    }
    Column {
        if (bitmap != null) {
            Image(bitmap!!, contentDescription = "Recibo seleccionado", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp))
        } else {
            Text("No se pudo mostrar la vista previa del recibo.", color = AlTheme.colors.textMuted)
        }
        Text("${photo.fileName} · ${photo.size / 1024} KB", color = AlTheme.colors.textMuted)
        AlButton(onClick = onRemove, variant = AlButtonVariant.Outline) { Text("Quitar foto") }
    }
}
