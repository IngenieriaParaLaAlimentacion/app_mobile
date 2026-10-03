package ar.com.ingenieriaalimentacion.data.food.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class ReceiptProposalDto(
    @SerialName("establishment_id") val establishmentId: String,
    val lines: List<ReceiptLineDto>,
    val message: String? = null,
)

@Serializable
data class ReceiptLineDto(
    val description: String,
    val food: IdentifierRefDto? = null,
    val quantity: JsonPrimitive? = null,
    val unit: String? = null,
    @SerialName("raw_quantity") val rawQuantity: JsonPrimitive? = null,
    @SerialName("raw_unit_text") val rawUnitText: String? = null,
    @SerialName("needs_review") val needsReview: Boolean,
)
