package ar.com.ingenieriaalimentacion.domain.usecase.auth

import ar.com.ingenieriaalimentacion.domain.model.auth.EmailVerificationError
import ar.com.ingenieriaalimentacion.domain.model.auth.EmailVerificationFailure
import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository

class VerifyEmailUseCase(private val repository: AuthRepository) {
    /**
     * A link that lost its `token` query reaches the screen with an empty token. The backend
     * would answer 422 for it; skip the round trip and report it as an invalid link.
     */
    suspend operator fun invoke(token: String): Result<Unit> {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(EmailVerificationFailure(EmailVerificationError.InvalidToken))
        }
        return repository.verifyEmail(trimmed)
    }
}
