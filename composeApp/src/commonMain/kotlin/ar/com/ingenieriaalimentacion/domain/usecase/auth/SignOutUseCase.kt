package ar.com.ingenieriaalimentacion.domain.usecase.auth

import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository
import ar.com.ingenieriaalimentacion.domain.usecase.notification.UnregisterPushDeviceUseCase

class SignOutUseCase(
    private val repository: AuthRepository,
    private val unregisterPushDevice: UnregisterPushDeviceUseCase,
) {
    suspend operator fun invoke() {
        unregisterPushDevice()
        repository.signOut()
    }
}
