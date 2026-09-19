package ar.com.ingenieriaalimentacion.domain.repository

interface PushDeviceRepository {
    suspend fun registerCurrentInstallation(tokenOverride: String? = null): Result<Unit>
    suspend fun unregisterCurrentInstallation(): Result<Unit>
    suspend fun deletePlatformToken(): Result<Unit>
}
