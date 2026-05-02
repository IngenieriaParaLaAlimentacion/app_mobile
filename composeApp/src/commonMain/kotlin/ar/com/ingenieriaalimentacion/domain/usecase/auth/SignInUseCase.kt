package ar.com.ingenieriaalimentacion.domain.usecase.auth

import ar.com.ingenieriaalimentacion.domain.model.auth.Credentials
import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession
import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository

class SignInUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(credentials: Credentials): Result<UserSession> =
        repository.signIn(credentials)
}
