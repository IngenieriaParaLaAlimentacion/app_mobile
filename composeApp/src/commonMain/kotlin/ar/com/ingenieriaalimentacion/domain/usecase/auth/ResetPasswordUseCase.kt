package ar.com.ingenieriaalimentacion.domain.usecase.auth

import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository
import ar.com.ingenieriaalimentacion.domain.usecase.notification.UnregisterPushDeviceUseCase

class ResetPasswordUseCase(
    private val repository: AuthRepository,
    private val unregisterPushDevice: UnregisterPushDeviceUseCase,
) {
    suspend operator fun invoke(token: String, newPassword: String): Result<Unit> =
        repository.resetPassword(token, newPassword) { unregisterPushDevice() }
}
