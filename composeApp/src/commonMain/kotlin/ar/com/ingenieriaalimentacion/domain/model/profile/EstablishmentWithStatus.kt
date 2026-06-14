package ar.com.ingenieriaalimentacion.domain.model.profile

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
)
