package ar.com.ingenieriaalimentacion.domain.usecase.auth

import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository

class RequestPasswordRecoveryUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String): Result<Unit> =
        repository.requestPasswordRecovery(email)
}
