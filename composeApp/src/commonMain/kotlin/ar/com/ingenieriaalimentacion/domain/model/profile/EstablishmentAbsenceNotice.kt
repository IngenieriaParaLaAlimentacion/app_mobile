package ar.com.ingenieriaalimentacion.domain.model.profile

/**
 * A field worker's notice that an establishment is missing from the catalog.
 * Every field is optional — the worker provides as much as they know, possibly
 * nothing; the authenticated user travels in the token.
 */
data class EstablishmentAbsenceNotice(
    val name: String? = null,
    val formattedAddress: String? = null,
    val city: String? = null,
    val province: String? = null,
)
