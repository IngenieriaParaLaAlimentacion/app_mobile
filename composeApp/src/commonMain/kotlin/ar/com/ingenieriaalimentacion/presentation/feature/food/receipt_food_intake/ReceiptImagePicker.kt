package ar.com.ingenieriaalimentacion.presentation.feature.food.receipt_food_intake

import androidx.compose.runtime.Composable
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptError
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptFailure
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptPhoto

interface ReceiptImagePicker {
    fun launchCamera()
    fun launchGallery()
}

@Composable
expect fun rememberReceiptImagePicker(onResult: (Result<ReceiptPhoto>?) -> Unit): ReceiptImagePicker

internal const val MAX_RECEIPT_BYTES = 10 * 1024 * 1024
private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)

internal fun sniffAcceptedMime(bytes: ByteArray): String? = when {
    bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() ->
        "image/jpeg"
    bytes.size >= 8 && pngSignature.indices.all { bytes[it] == pngSignature[it] } ->
        "image/png"
    bytes.size >= 12 && "RIFF".indices.all { bytes[it].toInt() == "RIFF"[it].code } &&
        "WEBP".indices.all { bytes[it + 8].toInt() == "WEBP"[it].code } -> "image/webp"
    else -> null
}

internal fun validateReceiptPhoto(photo: ReceiptPhoto): Result<ReceiptPhoto> = when {
    photo.bytes.isEmpty() -> Result.failure(ReceiptFailure(ReceiptError.InvalidImage))
    photo.size > MAX_RECEIPT_BYTES -> Result.failure(ReceiptFailure(ReceiptError.TooLarge))
    photo.contentType !in setOf("image/jpeg", "image/png", "image/webp") ->
        Result.failure(ReceiptFailure(ReceiptError.InvalidImage))
    sniffAcceptedMime(photo.bytes) != photo.contentType ->
        Result.failure(ReceiptFailure(ReceiptError.MismatchedContent))
    else -> Result.success(photo)
}
