package ar.com.ingenieriaalimentacion.data.food.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FoodOutflowRequest(
    val id: String,
    @SerialName("establishment_id") val establishmentId: String,
    @SerialName("occurred_at") val occurredAt: String,
    val comments: String? = null,
    val source: String = MANUAL_SOURCE,
    val items: List<FoodLineRequest>,
)
