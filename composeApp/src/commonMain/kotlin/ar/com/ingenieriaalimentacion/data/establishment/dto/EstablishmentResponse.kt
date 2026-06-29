package ar.com.ingenieriaalimentacion.data.establishment.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EstablishmentResponse(
    val id: String,
    val name: String,
    @SerialName("formatted_address") val address: String,
)

@Serializable
data class PaginatedEstablishmentsResponse(
    val items: List<EstablishmentResponse>,
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    @SerialName("total_pages") val totalPages: Int,
)
