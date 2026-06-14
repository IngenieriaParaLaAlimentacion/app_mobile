package ar.com.ingenieriaalimentacion.domain.usecase.profile

import ar.com.ingenieriaalimentacion.domain.model.profile.Profile
import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository

class GetMyProfileUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(): Result<Profile> = repository.getMe()
}
