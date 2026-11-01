package ar.com.ingenieriaalimentacion.domain.usecase.auth

import ar.com.ingenieriaalimentacion.domain.usecase.notification.UnregisterPushDeviceUseCase
import ar.com.ingenieriaalimentacion.fake.FakeAuthRepository
import ar.com.ingenieriaalimentacion.fake.FakePushDeviceRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SignOutUseCaseTest {
    @Test
    fun `unregisters and deletes platform token before auth sign out`() = runTest {
        val events = mutableListOf<String>()
        val push = FakePushDeviceRepository(events)
        val auth = FakeAuthRepository(events)

        SignOutUseCase(auth, UnregisterPushDeviceUseCase(push))()

        assertEquals(listOf("unregister", "deletePlatformToken", "authSignOut"), events)
    }

    @Test
    fun `backend unregister failure does not prevent token deletion or auth sign out`() = runTest {
        val events = mutableListOf<String>()
        val push = FakePushDeviceRepository(events).apply {
            unregisterResult = Result.failure(IllegalStateException("offline"))
        }
        val auth = FakeAuthRepository(events)

        SignOutUseCase(auth, UnregisterPushDeviceUseCase(push))()

        assertEquals(listOf("unregister", "deletePlatformToken", "authSignOut"), events)
    }

    @Test
    fun `backend unregister timeout does not prevent token deletion or auth sign out`() = runTest {
        val events = mutableListOf<String>()
        val push = FakePushDeviceRepository(events).apply {
            unregisterDelayMillis = 6_000
        }
        val auth = FakeAuthRepository(events)

        SignOutUseCase(auth, UnregisterPushDeviceUseCase(push))()

        assertEquals(listOf("unregister", "deletePlatformToken", "authSignOut"), events)
    }
}
