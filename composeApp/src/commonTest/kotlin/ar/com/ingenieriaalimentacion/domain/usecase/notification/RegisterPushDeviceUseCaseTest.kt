package ar.com.ingenieriaalimentacion.domain.usecase.notification

import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession
import ar.com.ingenieriaalimentacion.fake.FakeAuthRepository
import ar.com.ingenieriaalimentacion.fake.FakePushDeviceRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RegisterPushDeviceUseCaseTest {
    @Test
    fun `does not register before authentication`() = runTest {
        val auth = FakeAuthRepository()
        val push = FakePushDeviceRepository()

        RegisterPushDeviceUseCase(auth, push)("token")

        assertEquals(emptyList(), push.events)
    }

    @Test
    fun `forwards refreshed token when authenticated`() = runTest {
        val auth = FakeAuthRepository().apply {
            emit(SessionStatus.Authenticated(UserSession("access", "refresh")))
        }
        val push = FakePushDeviceRepository()

        RegisterPushDeviceUseCase(auth, push)("new-token")

        assertEquals(listOf("register:new-token"), push.events)
    }
}
