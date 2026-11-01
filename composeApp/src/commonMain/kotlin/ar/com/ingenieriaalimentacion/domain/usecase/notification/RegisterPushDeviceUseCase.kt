package ar.com.ingenieriaalimentacion.domain.usecase.notification

import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository
import ar.com.ingenieriaalimentacion.domain.repository.PushDeviceRepository

class RegisterPushDeviceUseCase(
    private val authRepository: AuthRepository,
    private val pushRepository: PushDeviceRepository,
) {
    suspend operator fun invoke(tokenOverride: String? = null): Result<Unit> {
        if (authRepository.sessionStatus.value !is SessionStatus.Authenticated) {
            return Result.success(Unit)
        }
        return pushRepository.registerCurrentInstallation(tokenOverride)
    }
}
