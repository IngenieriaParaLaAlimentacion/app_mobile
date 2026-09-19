package ar.com.ingenieriaalimentacion.domain.usecase.auth

import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository
import ar.com.ingenieriaalimentacion.domain.usecase.notification.UnregisterPushDeviceUseCase

class ResetPasswordUseCase(
    private val repository: AuthRepository,
    private val unregisterPushDevice: UnregisterPushDeviceUseCase,
) {
    suspend operator fun invoke(token: String, newPassword: String): Result<Unit> {
        val result = repository.resetPassword(token, newPassword)
        if (result.isFailure) return result
        unregisterPushDevice()
        repository.signOut()
        return Result.success(Unit)
    }
}
