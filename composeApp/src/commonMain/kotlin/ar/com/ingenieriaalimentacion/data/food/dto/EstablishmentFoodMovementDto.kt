package ar.com.ingenieriaalimentacion.data.food.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResponsibleRefDto(
    val id: String,
    val name: String,
    @SerialName("last_name") val lastName: String,
)

@Serializable
data class EstablishmentFoodMovementSummaryDto(
    val id: String,
    val direction: String,
    @SerialName("occurred_at") val occurredAt: String,
    val responsible: ResponsibleRefDto,
    @SerialName("food_count") val foodCount: Int,
    @SerialName("origin_type") val originType: String? = null,
    @SerialName("is_recurring") val isRecurring: Boolean? = null,
    val category: String? = null,
)

@Serializable
data class PaginatedEstablishmentFoodMovementsResponse(
    val items: List<EstablishmentFoodMovementSummaryDto>,
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
data class EstablishmentFoodMovementLineDto(
    val food: IdentifierRefDto,
    val unit: String,
    @Serializable(with = QuantitySerializer::class) val quantity: String,
)

@Serializable
data class EstablishmentFoodMovementDetailDto(
    val id: String,
    val direction: String,
    @SerialName("occurred_at") val occurredAt: String,
    val source: String,
    @SerialName("origin_type") val originType: String? = null,
    @SerialName("is_recurring") val isRecurring: Boolean? = null,
    val comments: String? = null,
    val responsible: ResponsibleRefDto,
    val items: List<EstablishmentFoodMovementLineDto>,
    val category: String? = null,
)
