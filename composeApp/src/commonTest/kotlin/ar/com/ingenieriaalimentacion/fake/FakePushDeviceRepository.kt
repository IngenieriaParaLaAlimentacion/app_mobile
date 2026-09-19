package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.repository.PushDeviceRepository
import kotlinx.coroutines.delay

class FakePushDeviceRepository(
    private val sharedEvents: MutableList<String>? = null,
) : PushDeviceRepository {
    val events = mutableListOf<String>()
    var registerResult: Result<Unit> = Result.success(Unit)
    var unregisterResult: Result<Unit> = Result.success(Unit)
    var deleteTokenResult: Result<Unit> = Result.success(Unit)
    var unregisterDelayMillis: Long = 0

    override suspend fun registerCurrentInstallation(tokenOverride: String?): Result<Unit> {
        record("register:${tokenOverride.orEmpty()}")
        return registerResult
    }

    override suspend fun unregisterCurrentInstallation(): Result<Unit> {
        record("unregister")
        delay(unregisterDelayMillis)
        return unregisterResult
    }

    override suspend fun deletePlatformToken(): Result<Unit> {
        record("deletePlatformToken")
        return deleteTokenResult
    }

    private fun record(event: String) {
        events += event
        sharedEvents?.add(event)
    }
}
