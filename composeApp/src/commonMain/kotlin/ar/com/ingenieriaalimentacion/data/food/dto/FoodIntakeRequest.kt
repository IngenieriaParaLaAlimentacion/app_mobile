package ar.com.ingenieriaalimentacion.data.food.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Body of `POST /api/v1/food-intakes`.
 *
 * [id] is the client-generated UUID (idempotency key): a replay of the same id answers 200 and
 * leaves the stock untouched. [occurredAt] is ISO 8601 UTC with zone (`…Z`) and is rejected by the
 * server if it is in the future.
 */
@Serializable
data class FoodIntakeRequest(
    val id: String,
    @SerialName("establishment_id") val establishmentId: String,
    @SerialName("occurred_at") val occurredAt: String,
    val comments: String? = null,
    val source: String = MANUAL_SOURCE,
    @SerialName("origin_type") val originType: String,
    @SerialName("is_recurring") val isRecurring: Boolean,
    val items: List<FoodLineRequest>,
)

/** No unit travels with a line — the server resolves it from the referenced food's `base_unit`. */
@Serializable
data class FoodLineRequest(
    @SerialName("food_id") val foodId: String,
    @Serializable(with = QuantitySerializer::class) val quantity: String,
)

const val MANUAL_SOURCE = "manual"
