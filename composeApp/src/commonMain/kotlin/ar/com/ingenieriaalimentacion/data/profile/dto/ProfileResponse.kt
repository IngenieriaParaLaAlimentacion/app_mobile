package ar.com.ingenieriaalimentacion.data.profile.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileResponse(
    val id: String,
    val email: String,
    val name: String,
    @SerialName("last_name") val lastName: String,
    val role: String,
    // Three-state preserved: null -> not a manager, [] -> manager w/o relations.
    val establishments: List<EstablishmentWithStatusResponse>? = null,
)

@Serializable
data class EstablishmentWithStatusResponse(
    val id: String,
    val name: String,
    @SerialName("formatted_address") val formattedAddress: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("place_id") val placeId: String,
    val city: String,
    val province: String,
    val status: String,
)
