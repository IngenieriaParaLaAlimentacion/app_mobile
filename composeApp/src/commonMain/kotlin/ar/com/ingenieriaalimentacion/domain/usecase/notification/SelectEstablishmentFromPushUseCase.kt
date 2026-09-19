package ar.com.ingenieriaalimentacion.domain.usecase.notification

import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository

class SelectEstablishmentFromPushUseCase(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(establishmentId: String) {
        profileRepository.setCurrentEstablishmentId(establishmentId)
    }
}
