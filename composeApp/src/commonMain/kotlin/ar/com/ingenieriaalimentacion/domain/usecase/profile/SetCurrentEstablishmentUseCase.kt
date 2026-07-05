package ar.com.ingenieriaalimentacion.domain.usecase.profile

import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository

class SetCurrentEstablishmentUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(establishment: EstablishmentWithStatus) {
        repository.setCurrentEstablishmentId(establishment.id)
    }
}
