package ar.com.ingenieriaalimentacion.domain.usecase.notification

import ar.com.ingenieriaalimentacion.domain.repository.PushDeviceRepository
import kotlinx.coroutines.withTimeoutOrNull

class UnregisterPushDeviceUseCase(
    private val repository: PushDeviceRepository,
) {
    suspend operator fun invoke() {
        withTimeoutOrNull(5_000) { repository.unregisterCurrentInstallation() }
        repository.deletePlatformToken()
    }
}
