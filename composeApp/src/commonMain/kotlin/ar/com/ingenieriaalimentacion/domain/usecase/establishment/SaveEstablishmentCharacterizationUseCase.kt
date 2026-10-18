package ar.com.ingenieriaalimentacion.domain.usecase.establishment

import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationStatus
import ar.com.ingenieriaalimentacion.domain.model.establishment.EstablishmentCharacterization
import ar.com.ingenieriaalimentacion.domain.repository.EstablishmentCharacterizationRepository
import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository

/**
 * Normalizes the profile (see [EstablishmentCharacterization.normalized]) and replaces it. On
 * success it also refreshes the cached establishments, which carry the status the "Pendiente de
 * ficha" badge reads; that refresh is best effort, since the save itself already went through.
 */
class SaveEstablishmentCharacterizationUseCase(
    private val characterizations: EstablishmentCharacterizationRepository,
    private val profile: ProfileRepository,
) {
    suspend operator fun invoke(
        establishmentId: String,
        characterization: EstablishmentCharacterization,
    ): Result<CharacterizationStatus> =
        characterizations.replace(establishmentId, characterization.normalized())
            .onSuccess { profile.refreshMyEstablishments() }
}
