package ar.com.ingenieriaalimentacion.data.profile.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Body for `POST /api/v1/establishments/requests`. Every field is optional on
 * the backend (`ValidStr | None`), but blank strings are rejected — callers
 * must pass `null` for fields the user left empty (the use case normalizes).
 */
@Serializable
data class EstablishmentAbsenceRequest(
    val name: String? = null,
    @SerialName("formatted_address")
    val formattedAddress: String? = null,
    val city: String? = null,
    val province: String? = null,
)
