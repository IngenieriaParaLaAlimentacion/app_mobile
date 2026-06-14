package ar.com.ingenieriaalimentacion.domain.usecase.profile

import ar.com.ingenieriaalimentacion.domain.model.profile.Profile
import ar.com.ingenieriaalimentacion.domain.model.profile.UpdateProfile
import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository

class UpdateMyProfileUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(update: UpdateProfile): Result<Profile> = repository.updateMe(update)
}
