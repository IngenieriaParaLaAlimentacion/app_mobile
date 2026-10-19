package ar.com.ingenieriaalimentacion.domain.model.profile

import ar.com.ingenieriaalimentacion.domain.model.establishment.EstablishmentCharacterization

/**
 * A field worker's notice that an establishment is missing from the catalog.
 * The backend requires name and address; city and province are optional — the
 * authenticated user travels in the token. [characterization] is the ficha proposed with it, or
 * `null` when the user chose "Contestar luego"; the admin reviews it on approval.
 */
data class EstablishmentAbsenceNotice(
    val name: String,
    val formattedAddress: String,
    val city: String? = null,
    val province: String? = null,
    val characterization: EstablishmentCharacterization? = null,
)
