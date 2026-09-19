package ar.com.ingenieriaalimentacion.domain.usecase.auth

import ar.com.ingenieriaalimentacion.domain.usecase.notification.UnregisterPushDeviceUseCase
import ar.com.ingenieriaalimentacion.fake.FakeAuthRepository
import ar.com.ingenieriaalimentacion.fake.FakePushDeviceRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResetPasswordUseCaseTest {
    @Test
    fun `successful reset unregisters before signing out`() = runTest {
        val events = mutableListOf<String>()
        val auth = FakeAuthRepository(events)
        val push = FakePushDeviceRepository(events)

        val result = ResetPasswordUseCase(auth, UnregisterPushDeviceUseCase(push))("token", "new-password")

        assertTrue(result.isSuccess)
        assertEquals(
            listOf("resetPassword", "unregister", "deletePlatformToken", "authSignOut"),
            events,
        )
    }

    @Test
    fun `failed reset preserves session and device registration`() = runTest {
        val events = mutableListOf<String>()
        val auth = FakeAuthRepository(events).apply {
            resetPasswordResult = Result.failure(IllegalStateException("rejected"))
        }
        val push = FakePushDeviceRepository(events)

        val result = ResetPasswordUseCase(auth, UnregisterPushDeviceUseCase(push))("token", "new-password")

        assertTrue(result.isFailure)
        assertEquals(listOf("resetPassword"), events)
        assertEquals(0, auth.signOutCount)
    }
}
