package ar.com.ingenieriaalimentacion.presentation.feature.food.receipt_food_intake

import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptError
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptFailure
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptPhoto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReceiptImagePickerTest {
    private fun webpBytes(size: Int = 12) = ByteArray(size).also { bytes ->
        "RIFF".forEachIndexed { index, char -> bytes[index] = char.code.toByte() }
        "WEBP".forEachIndexed { index, char -> bytes[index + 8] = char.code.toByte() }
    }

    @Test fun acceptsProcessedWebp() {
        assertTrue(validateReceiptPhoto(processedReceiptPhoto(webpBytes())).isSuccess)
    }

    @Test fun rejectsSourceRepresentationAndMismatchedWebp() {
        val jpeg = ReceiptPhoto(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()),
            "image/jpeg", "recibo.jpg")
        assertEquals(ReceiptError.InvalidImage,
            (validateReceiptPhoto(jpeg).exceptionOrNull() as ReceiptFailure).kind)
        val mismatch = processedReceiptPhoto(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()))
        assertEquals(ReceiptError.MismatchedContent,
            (validateReceiptPhoto(mismatch).exceptionOrNull() as ReceiptFailure).kind)
    }

    @Test fun rejectsOversizedProcessedWebp() {
        val large = processedReceiptPhoto(webpBytes(MAX_RECEIPT_BYTES + 1))
        assertEquals(ReceiptError.TooLarge,
            (validateReceiptPhoto(large).exceptionOrNull() as ReceiptFailure).kind)
    }

    @Test fun longestEdgeIs1280WithoutUpscaling() {
        assertEquals(1280 to 640, receiptTargetSize(4000, 2000))
        assertEquals(640 to 1280, receiptTargetSize(2000, 4000))
        assertEquals(800 to 600, receiptTargetSize(800, 600))
        assertEquals(1280 to 1, receiptTargetSize(100_000_000, 1))
    }

    @Test fun rejectsInvalidOrExcessiveSourcePixels() {
        assertFailsWith<IllegalArgumentException> { receiptTargetSize(0, 500) }
        assertFailsWith<IllegalArgumentException> { receiptTargetSize(20_000, 20_000) }
    }
}
