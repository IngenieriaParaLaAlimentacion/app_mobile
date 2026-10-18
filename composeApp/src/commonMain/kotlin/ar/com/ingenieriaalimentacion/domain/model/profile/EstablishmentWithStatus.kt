package ar.com.ingenieriaalimentacion.domain.model.profile

import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationStatus

data class EstablishmentWithStatus(
    val id: String,
    val name: String,
    val formattedAddress: String,
    val latitude: Double,
    val longitude: Double,
    val placeId: String,
    val city: String,
    val province: String,
    val status: EstablishmentStatus,
    /** `null` when the backend didn't report it (older build); no badge is shown then. */
    val characterizationStatus: CharacterizationStatus? = null,
)
