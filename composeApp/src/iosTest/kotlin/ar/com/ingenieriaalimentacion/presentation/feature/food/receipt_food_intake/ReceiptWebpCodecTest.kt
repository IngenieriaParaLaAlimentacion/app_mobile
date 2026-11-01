package ar.com.ingenieriaalimentacion.presentation.feature.food.receipt_food_intake

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ReceiptWebpCodecTest {
    @Test fun skikoEncodesLossyWebpAtReceiptQuality() {
        val pixels = Bitmap().apply {
            allocPixels(ImageInfo.makeN32Premul(32, 24))
            erase(0xff4080c0.toInt())
        }
        val image = Image.makeFromBitmap(pixels)
        try {
            val encoded = assertNotNull(image.encodeToData(
                EncodedImageFormat.WEBP, RECEIPT_WEBP_QUALITY,
            ))
            try {
                val bytes = encoded.bytes
                assertEquals("RIFF", bytes.asciiAt(0))
                assertEquals("WEBP", bytes.asciiAt(8))
                assertEquals("VP8 ", bytes.asciiAt(12))
                val decoded = Image.makeFromEncoded(bytes)
                try {
                    assertEquals(32, decoded.width)
                    assertEquals(24, decoded.height)
                } finally { decoded.close() }
            } finally { encoded.close() }
        } finally { image.close() }
    }

    private fun ByteArray.asciiAt(offset: Int): String =
        (offset until offset + 4).map { this[it].toInt().toChar() }.joinToString("")
}
