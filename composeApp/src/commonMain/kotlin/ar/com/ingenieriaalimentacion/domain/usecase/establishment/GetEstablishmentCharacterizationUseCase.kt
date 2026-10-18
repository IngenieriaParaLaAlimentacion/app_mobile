package ar.com.ingenieriaalimentacion.domain.usecase.establishment

import ar.com.ingenieriaalimentacion.domain.model.establishment.EstablishmentCharacterization
import ar.com.ingenieriaalimentacion.domain.repository.EstablishmentCharacterizationRepository

class GetEstablishmentCharacterizationUseCase(
    private val repository: EstablishmentCharacterizationRepository,
) {
    suspend operator fun invoke(establishmentId: String): Result<EstablishmentCharacterization> =
        repository.get(establishmentId)
}
