package ar.com.ingenieriaalimentacion.domain.model.profile

/** Catalog item from `GET /establishments?available=true` — no relation status. */
data class AvailableEstablishment(
    val id: String,
    val name: String,
    val formattedAddress: String,
    val latitude: Double,
    val longitude: Double,
    val placeId: String,
    val city: String,
    val province: String,
)
