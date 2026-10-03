package ar.com.ingenieriaalimentacion.presentation.feature.food.receipt_food_intake

import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptError
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptFailure
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptPhoto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReceiptImagePickerTest {
    @Test fun acceptsMatchingJpegSignature() {
        val photo = ReceiptPhoto(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0),
            "image/jpeg", "recibo.jpg")
        assertTrue(validateReceiptPhoto(photo).isSuccess)
    }

    @Test fun rejectsMismatchedAndOversizedImagesBeforeUpload() {
        val mismatch = ReceiptPhoto(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()),
            "image/png", "recibo.png")
        assertEquals(ReceiptError.MismatchedContent,
            (validateReceiptPhoto(mismatch).exceptionOrNull() as ReceiptFailure).kind)
        val large = ReceiptPhoto(ByteArray(MAX_RECEIPT_BYTES + 1), "image/jpeg", "recibo.jpg")
        assertEquals(ReceiptError.TooLarge,
            (validateReceiptPhoto(large).exceptionOrNull() as ReceiptFailure).kind)
    }
}
