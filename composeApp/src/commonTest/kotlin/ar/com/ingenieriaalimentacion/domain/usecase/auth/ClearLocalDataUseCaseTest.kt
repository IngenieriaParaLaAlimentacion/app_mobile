package ar.com.ingenieriaalimentacion.domain.usecase.auth

import ar.com.ingenieriaalimentacion.fake.FakeDatabaseCleaner
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ClearLocalDataUseCaseTest {

    @Test
    fun `invoke clears all local data once`() = runTest {
        val cleaner = FakeDatabaseCleaner()
        val useCase = ClearLocalDataUseCase(cleaner)

        useCase()

        assertEquals(1, cleaner.clearAllCount)
    }
}
