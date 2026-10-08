package ar.com.ingenieriaalimentacion.presentation.feature.auth.verify_email

import ar.com.ingenieriaalimentacion.domain.model.auth.EmailVerificationError
import ar.com.ingenieriaalimentacion.domain.model.auth.EmailVerificationFailure
import ar.com.ingenieriaalimentacion.domain.usecase.auth.VerifyEmailUseCase
import ar.com.ingenieriaalimentacion.fake.FakeAuthRepository
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VerifyEmailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun teardown() = Dispatchers.resetMain()

    private fun viewModel(
        repository: FakeAuthRepository = FakeAuthRepository(),
        token: String = "tok",
    ) = VerifyEmailViewModel(
        route = Route.VerifyEmail(token),
        verifyEmail = VerifyEmailUseCase(repository),
    )

    private fun failing(error: EmailVerificationError) = FakeAuthRepository().apply {
        verifyEmailResult = Result.failure(EmailVerificationFailure(error))
    }

    @Test
    fun `verifies the route token on entry without any action`() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val vm = viewModel(repository, token = "tok-123")
        advanceUntilIdle()

        assertEquals(listOf("tok-123"), repository.verifyEmailTokens)
        assertEquals(VerifyEmailStatus.Verified, vm.state.value.status)
    }

    @Test
    fun `stays Verifying while the request is in flight`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeAuthRepository().apply { verifyEmailGate = gate }
        val vm = viewModel(repository)
        advanceUntilIdle()

        assertEquals(VerifyEmailStatus.Verifying, vm.state.value.status)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(VerifyEmailStatus.Verified, vm.state.value.status)
    }

    @Test
    fun `TokenUsed shows AlreadyUsed`() = runTest(dispatcher) {
        val vm = viewModel(failing(EmailVerificationError.TokenUsed))
        advanceUntilIdle()
        assertEquals(VerifyEmailStatus.AlreadyUsed, vm.state.value.status)
    }

    @Test
    fun `TokenExpired shows Expired`() = runTest(dispatcher) {
        val vm = viewModel(failing(EmailVerificationError.TokenExpired))
        advanceUntilIdle()
        assertEquals(VerifyEmailStatus.Expired, vm.state.value.status)
    }

    @Test
    fun `InvalidToken shows InvalidLink`() = runTest(dispatcher) {
        val vm = viewModel(failing(EmailVerificationError.InvalidToken))
        advanceUntilIdle()
        assertEquals(VerifyEmailStatus.InvalidLink, vm.state.value.status)
    }

    @Test
    fun `Network shows ConnectionError`() = runTest(dispatcher) {
        val vm = viewModel(failing(EmailVerificationError.Network))
        advanceUntilIdle()
        assertEquals(VerifyEmailStatus.ConnectionError, vm.state.value.status)
    }

    @Test
    fun `an untyped failure is treated as a retryable ConnectionError`() = runTest(dispatcher) {
        val repository = FakeAuthRepository().apply {
            verifyEmailResult = Result.failure(IllegalStateException("boom"))
        }
        val vm = viewModel(repository)
        advanceUntilIdle()
        assertEquals(VerifyEmailStatus.ConnectionError, vm.state.value.status)
    }

    @Test
    fun `empty token shows InvalidLink and never calls the backend`() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val vm = viewModel(repository, token = "")
        advanceUntilIdle()

        assertEquals(VerifyEmailStatus.InvalidLink, vm.state.value.status)
        assertTrue(repository.verifyEmailTokens.isEmpty())
    }

    @Test
    fun `Retry after a connection error re-sends the same token`() = runTest(dispatcher) {
        val repository = failing(EmailVerificationError.Network)
        val vm = viewModel(repository, token = "tok-123")
        advanceUntilIdle()
        assertEquals(VerifyEmailStatus.ConnectionError, vm.state.value.status)

        repository.verifyEmailResult = Result.success(Unit)
        vm.onAction(VerifyEmailAction.Retry)
        advanceUntilIdle()

        assertEquals(listOf("tok-123", "tok-123"), repository.verifyEmailTokens)
        assertEquals(VerifyEmailStatus.Verified, vm.state.value.status)
    }

    @Test
    fun `Retry is ignored while a request is in flight`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeAuthRepository().apply { verifyEmailGate = gate }
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onAction(VerifyEmailAction.Retry)
        advanceUntilIdle()
        assertEquals(1, repository.verifyEmailTokens.size)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, repository.verifyEmailTokens.size)
        assertEquals(VerifyEmailStatus.Verified, vm.state.value.status)
    }

    @Test
    fun `Continue emits Exit and clearDestination resets it`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onAction(VerifyEmailAction.Continue)
        assertEquals(VerifyEmailDestination.Exit, vm.state.value.destination)

        vm.clearDestination()
        assertNull(vm.state.value.destination)
    }
}
