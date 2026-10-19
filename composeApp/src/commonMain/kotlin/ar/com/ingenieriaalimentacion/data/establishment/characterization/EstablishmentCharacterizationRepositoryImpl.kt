package ar.com.ingenieriaalimentacion.data.establishment.characterization

import ar.com.ingenieriaalimentacion.data.establishment.characterization.mapper.toCharacterizationStatus
import ar.com.ingenieriaalimentacion.data.establishment.characterization.mapper.toDomain
import ar.com.ingenieriaalimentacion.data.establishment.characterization.mapper.toDto
import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationError
import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationFailure
import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationStatus
import ar.com.ingenieriaalimentacion.domain.model.establishment.EstablishmentCharacterization
import ar.com.ingenieriaalimentacion.domain.repository.EstablishmentCharacterizationRepository

class EstablishmentCharacterizationRepositoryImpl(
    private val api: CharacterizationApi,
) : EstablishmentCharacterizationRepository {

    override suspend fun get(establishmentId: String): Result<EstablishmentCharacterization> =
        api.get(establishmentId).toResult { it.toDomain() }

    override suspend fun replace(
        establishmentId: String,
        characterization: EstablishmentCharacterization,
    ): Result<CharacterizationStatus> =
        api.replace(establishmentId, characterization.toDto()).toResult { wire ->
            // An unrecognized status still means the save went through; fall back to the local rule.
            wire.toCharacterizationStatus() ?: characterization.status
        }

    private inline fun <T, R> CharacterizationOutcome<T>.toResult(map: (T) -> R): Result<R> =
        when (this) {
            is CharacterizationOutcome.Success -> Result.success(map(value))
            is CharacterizationOutcome.InvalidField -> fail(CharacterizationError.InvalidField(field))
            CharacterizationOutcome.Unauthorized -> fail(CharacterizationError.Unauthorized)
            CharacterizationOutcome.Forbidden -> fail(CharacterizationError.Forbidden)
            CharacterizationOutcome.NotFound -> fail(CharacterizationError.NotFound)
            is CharacterizationOutcome.Network -> fail(CharacterizationError.Network, cause)
        }

    private fun fail(error: CharacterizationError, cause: Throwable? = null): Result<Nothing> =
        Result.failure(CharacterizationFailure(error, cause))
}
