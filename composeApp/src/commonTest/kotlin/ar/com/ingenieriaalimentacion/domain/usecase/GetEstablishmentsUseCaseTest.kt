package ar.com.ingenieriaalimentacion.domain.usecase

import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment
import ar.com.ingenieriaalimentacion.domain.usecase.establishment.GetEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.fake.FakeEstablishmentRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetEstablishmentsUseCaseTest {

    @Test
    fun `returns establishments from repository`() = runTest {
        val repo = FakeEstablishmentRepository().apply {
            result = Result.success(listOf(Establishment("1", "Comedor Norte", "Calle 1")))
        }
        val useCase = GetEstablishmentsUseCase(repo)

        val result = useCase()

        assertEquals(listOf(Establishment("1", "Comedor Norte", "Calle 1")), result.getOrNull())
    }

    @Test
    fun `propagates repository failure`() = runTest {
        val repo = FakeEstablishmentRepository().apply {
            result = Result.failure(IllegalStateException("boom"))
        }
        val useCase = GetEstablishmentsUseCase(repo)

        val result = useCase()

        assertTrue(result.isFailure)
    }
}
