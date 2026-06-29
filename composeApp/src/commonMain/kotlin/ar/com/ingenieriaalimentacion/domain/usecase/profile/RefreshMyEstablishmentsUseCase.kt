package ar.com.ingenieriaalimentacion.domain.usecase.profile

import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository

/** Best-effort prefetch that warms the establishment cache at authed-shell entry. */
class RefreshMyEstablishmentsUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(): Result<Unit> = repository.refreshMyEstablishments()
}
