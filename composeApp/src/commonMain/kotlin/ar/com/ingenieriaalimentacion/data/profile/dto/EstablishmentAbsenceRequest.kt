package ar.com.ingenieriaalimentacion.data.profile.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Body for `POST /api/v1/establishments/requests`. The backend requires
 * `name` and `formatted_address` (`ValidStr`); `city` and `province` stay
 * optional, but blank strings are rejected — callers must pass `null` for
 * optional fields the user left empty (the use case normalizes).
 */
@Serializable
data class EstablishmentAbsenceRequest(
    val name: String,
    @SerialName("formatted_address")
    val formattedAddress: String,
    val city: String? = null,
    val province: String? = null,
)
