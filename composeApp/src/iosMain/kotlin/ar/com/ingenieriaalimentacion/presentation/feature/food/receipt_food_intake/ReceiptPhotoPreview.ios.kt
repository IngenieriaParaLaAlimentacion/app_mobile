package ar.com.ingenieriaalimentacion.presentation.feature.food.receipt_food_intake

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

actual fun decodeReceiptPreview(bytes: ByteArray): ImageBitmap? = runCatching {
    val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
    val width = codec.imageInfo.width
    val height = codec.imageInfo.height
    if (width <= 0 || height <= 0) return null
    var sample = 1
    while (width / sample > 1600 || height / sample > 1600) sample *= 2
    if (sample == 1) return Image.makeFromBitmap(codec.readPixels()).toComposeImageBitmap()
    val targetWidth = (width / sample).coerceAtLeast(1)
    val targetHeight = (height / sample).coerceAtLeast(1)
    val bitmap = Bitmap().apply { allocPixels(ImageInfo.makeN32Premul(targetWidth, targetHeight)) }
    val sampled = runCatching { codec.readPixels(bitmap); Image.makeFromBitmap(bitmap) }
        .getOrNull() ?: return null
    sampled.toComposeImageBitmap()
}.getOrNull()
