package ar.com.ingenieriaalimentacion.domain.model.profile

/**
 * A field worker's notice that an establishment is missing from the catalog.
 * The backend requires name and address; city and province are optional — the
 * authenticated user travels in the token.
 */
data class EstablishmentAbsenceNotice(
    val name: String,
    val formattedAddress: String,
    val city: String? = null,
    val province: String? = null,
)
