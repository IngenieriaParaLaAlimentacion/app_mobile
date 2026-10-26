package ar.com.ingenieriaalimentacion.domain.model.food

data class ReceiptPhoto(val bytes: ByteArray, val contentType: String, val fileName: String) {
    val size: Int get() = bytes.size
}

data class ReceiptProposal(val establishmentId: String, val lines: List<ReceiptLine>, val message: String?)

data class ReceiptLine(
    val description: String,
    val matchedFoodId: String?,
    val proposedQuantity: String?,
    val proposedUnit: FoodUnit?,
    val rawQuantity: String?,
    val rawUnitText: String?,
)

enum class ReceiptSource { Photo, ManualFallback }

enum class ReceiptError {
    InvalidImage, SourceTooLarge, TooLarge, MismatchedContent, InvalidData, AccessDenied,
    EstablishmentNotFound, FoodNotFound, Unauthorized, Retryable,
    AiQuotaExhausted, AiUnavailable, RateLimited,
}

class ReceiptFailure(val kind: ReceiptError, cause: Throwable? = null) : Exception(kind.name, cause)
