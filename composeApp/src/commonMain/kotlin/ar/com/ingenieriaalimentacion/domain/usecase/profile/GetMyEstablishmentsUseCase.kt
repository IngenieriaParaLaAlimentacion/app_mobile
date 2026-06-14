package ar.com.ingenieriaalimentacion.domain.usecase.profile

import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository

class GetMyEstablishmentsUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(): Result<List<EstablishmentWithStatus>> =
        repository.getMyEstablishments()
}
