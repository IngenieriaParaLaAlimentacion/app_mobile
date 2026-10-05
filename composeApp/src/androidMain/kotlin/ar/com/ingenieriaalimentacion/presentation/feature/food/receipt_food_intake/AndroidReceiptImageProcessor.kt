package ar.com.ingenieriaalimentacion.presentation.feature.food.receipt_food_intake

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptError
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptFailure
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptPhoto
import java.io.ByteArrayOutputStream

internal fun processReceiptUri(resolver: ContentResolver, uri: Uri): Result<ReceiptPhoto> = runCatching {
    resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
        if (descriptor.length > MAX_SOURCE_BYTES) throw ReceiptFailure(ReceiptError.SourceTooLarge)
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: throw ReceiptFailure(ReceiptError.InvalidImage)
    try { receiptTargetSize(bounds.outWidth, bounds.outHeight) }
    catch (_: IllegalArgumentException) { throw ReceiptFailure(ReceiptError.SourceTooLarge) }

    // Power-of-two sampling keeps the decoded longest edge below 2560 even for thin images.
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample >= RECEIPT_MAX_EDGE * 2) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: throw ReceiptFailure(ReceiptError.InvalidImage)
    try {
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val transform = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
            }
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true)
        try {
            val (width, height) = receiptTargetSize(upright.width, upright.height)
            val scaled = Bitmap.createScaledBitmap(upright, width, height, true)
            try {
                val output = ByteArrayOutputStream()
                @Suppress("DEPRECATION")
                val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY
                    else Bitmap.CompressFormat.WEBP
                if (!scaled.compress(format, RECEIPT_WEBP_QUALITY, output))
                    throw ReceiptFailure(ReceiptError.InvalidImage)
                validateReceiptPhoto(processedReceiptPhoto(output.toByteArray())).getOrThrow()
            } finally { if (scaled !== upright) scaled.recycle() }
        } finally { if (upright !== decoded) upright.recycle() }
    } finally { decoded.recycle() }
}
