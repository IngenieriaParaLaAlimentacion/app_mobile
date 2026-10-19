package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationStatus
import ar.com.ingenieriaalimentacion.domain.model.establishment.EstablishmentCharacterization

/**
 * Live read and replace of an establishment's characterization; there is no offline queue behind
 * it. A failed [Result] carries a
 * [ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationFailure].
 */
interface EstablishmentCharacterizationRepository {
    suspend fun get(establishmentId: String): Result<EstablishmentCharacterization>

    /** Replaces the whole profile; returns the status the backend computed for it. */
    suspend fun replace(
        establishmentId: String,
        characterization: EstablishmentCharacterization,
    ): Result<CharacterizationStatus>
}
