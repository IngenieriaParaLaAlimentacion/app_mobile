package ar.com.ingenieriaalimentacion.domain.usecase.profile

import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository

class RequestEstablishmentAccessUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(ids: List<String>): Result<List<EstablishmentWithStatus>> =
        repository.requestEstablishmentAccess(ids)
}
