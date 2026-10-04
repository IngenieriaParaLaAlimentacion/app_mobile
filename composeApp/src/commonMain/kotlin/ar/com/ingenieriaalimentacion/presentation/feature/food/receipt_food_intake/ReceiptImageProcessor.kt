package ar.com.ingenieriaalimentacion.presentation.feature.food.receipt_food_intake

import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptPhoto

internal const val RECEIPT_MAX_EDGE = 1280
internal const val RECEIPT_WEBP_QUALITY = 80
internal const val MAX_SOURCE_BYTES = 200L * 1024 * 1024
internal const val MAX_SOURCE_PIXELS = 100_000_000L

internal fun receiptTargetSize(width: Int, height: Int): Pair<Int, Int> {
    require(width > 0 && height > 0 && width.toLong() * height <= MAX_SOURCE_PIXELS)
    val scale = minOf(1.0, RECEIPT_MAX_EDGE.toDouble() / maxOf(width, height))
    return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
}

internal fun processedReceiptPhoto(webp: ByteArray): ReceiptPhoto =
    ReceiptPhoto(webp, "image/webp", "recibo.webp")
