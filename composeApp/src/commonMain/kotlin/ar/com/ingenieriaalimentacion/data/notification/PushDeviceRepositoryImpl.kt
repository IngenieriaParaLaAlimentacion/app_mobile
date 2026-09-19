package ar.com.ingenieriaalimentacion.data.notification

import ar.com.ingenieriaalimentacion.data.device.DeviceIdProvider
import ar.com.ingenieriaalimentacion.data.notification.dto.RegisterDeviceRequest
import ar.com.ingenieriaalimentacion.data.notification.mapper.toWire
import ar.com.ingenieriaalimentacion.domain.repository.PushDeviceRepository

class PushDeviceRepositoryImpl(
    private val api: PushDeviceApi,
    private val deviceIdProvider: DeviceIdProvider,
    private val tokenProvider: PushTokenProvider,
) : PushDeviceRepository {
    override suspend fun registerCurrentInstallation(tokenOverride: String?): Result<Unit> = runCatching {
        val token = tokenOverride ?: tokenProvider.currentToken().getOrThrow()
        api.register(
            RegisterDeviceRequest(
                installationId = deviceIdProvider.getOrCreate(),
                fcmToken = token,
                platform = tokenProvider.platform.toWire(),
                deviceName = tokenProvider.deviceName,
            ),
        )
    }

    override suspend fun unregisterCurrentInstallation(): Result<Unit> = runCatching {
        api.unregister(deviceIdProvider.getOrCreate())
    }

    override suspend fun deletePlatformToken(): Result<Unit> = runCatching {
        tokenProvider.deleteToken().getOrThrow()
    }
}
