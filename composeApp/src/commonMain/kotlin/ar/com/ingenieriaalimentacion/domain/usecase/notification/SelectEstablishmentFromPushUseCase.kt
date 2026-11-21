package ar.com.ingenieriaalimentacion.domain.usecase.notification

import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository

class SelectEstablishmentFromPushUseCase(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(establishmentId: String, requireMembership: Boolean): Boolean {
        if (requireMembership) {
            val establishments = profileRepository.getMyEstablishments().getOrNull()
            if (establishments.orEmpty().none { it.id == establishmentId && it.status == EstablishmentStatus.Approved }) {
                return false
            }
        }
        profileRepository.setCurrentEstablishmentId(establishmentId)
        return true
    }
}
