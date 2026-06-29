package ar.com.ingenieriaalimentacion.domain.usecase.profile

import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository

/** Clears the establishment cache on logout so no previous user's data leaks. */
class ClearEstablishmentCacheUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke() = repository.clearEstablishmentCache()
}
