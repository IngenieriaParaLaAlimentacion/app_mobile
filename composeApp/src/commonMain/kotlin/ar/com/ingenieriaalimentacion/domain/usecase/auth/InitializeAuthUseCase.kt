package ar.com.ingenieriaalimentacion.domain.usecase.auth

import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository

class InitializeAuthUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke() = repository.bootstrap()
}
