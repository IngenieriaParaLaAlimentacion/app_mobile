package ar.com.ingenieriaalimentacion.domain.usecase.auth

import ar.com.ingenieriaalimentacion.domain.model.auth.EmailVerificationError
import ar.com.ingenieriaalimentacion.domain.model.auth.EmailVerificationFailure
import ar.com.ingenieriaalimentacion.fake.FakeAuthRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VerifyEmailUseCaseTest {
    @Test
    fun `blank token fails as InvalidToken without calling the repository`() = runTest {
        val auth = FakeAuthRepository()

        val result = VerifyEmailUseCase(auth)("   ")

        val failure = assertIs<EmailVerificationFailure>(result.exceptionOrNull())
        assertEquals(EmailVerificationError.InvalidToken, failure.error)
        assertTrue(auth.verifyEmailTokens.isEmpty())
    }

    @Test
    fun `token is trimmed and forwarded`() = runTest {
        val auth = FakeAuthRepository()

        val result = VerifyEmailUseCase(auth)(" tok ")

        assertTrue(result.isSuccess)
        assertEquals(listOf("tok"), auth.verifyEmailTokens)
    }
}
