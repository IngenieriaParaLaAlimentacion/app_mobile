package ar.com.ingenieriaalimentacion.domain.usecase

import ar.com.ingenieriaalimentacion.domain.model.registration.FieldWorkerRegistration
import ar.com.ingenieriaalimentacion.domain.model.registration.RegistrationResult
import ar.com.ingenieriaalimentacion.domain.usecase.registration.RegisterFieldWorkerUseCase
import ar.com.ingenieriaalimentacion.fake.FakeRegistrationRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RegisterFieldWorkerUseCaseTest {

    private val registration = FieldWorkerRegistration(
        email = "manager@example.com",
        password = "password123",
        name = "Ana",
        lastName = "García",
        establishmentIds = listOf("1"),
    )

    @Test
    fun `delegates to repository and returns its result`() = runTest {
        val repo = FakeRegistrationRepository().apply {
            result = Result.success(RegistrationResult("id-9", "manager@example.com", "pending_validation"))
        }
        val useCase = RegisterFieldWorkerUseCase(repo)

        val result = useCase(registration)

        assertEquals("id-9", result.getOrNull()?.id)
        assertEquals(registration, repo.lastRegistration)
    }
}
