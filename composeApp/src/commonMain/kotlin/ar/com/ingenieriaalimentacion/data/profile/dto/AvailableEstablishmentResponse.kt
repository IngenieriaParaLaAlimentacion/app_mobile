package ar.com.ingenieriaalimentacion.data.profile.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AvailableEstablishmentResponse(
    val id: String,
    val name: String,
    @SerialName("formatted_address") val formattedAddress: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("place_id") val placeId: String,
    val city: String,
    val province: String,
)

@Serializable
data class PaginatedAvailableResponse(
    val items: List<AvailableEstablishmentResponse>,
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    @SerialName("total_pages") val totalPages: Int,
)
