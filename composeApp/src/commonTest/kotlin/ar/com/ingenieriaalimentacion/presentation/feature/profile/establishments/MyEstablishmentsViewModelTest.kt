package ar.com.ingenieriaalimentacion.presentation.feature.profile.establishments

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.data.profile.EstablishmentWithdrawalFailure
import ar.com.ingenieriaalimentacion.domain.model.auth.EstablishmentLinkMode
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithdrawalError
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.WithdrawFromEstablishmentUseCase
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class MyEstablishmentsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun establishment(id: String, name: String, status: EstablishmentStatus) =
        EstablishmentWithStatus(
            id = id, name = name, formattedAddress = "Calle $id", latitude = 0.0,
            longitude = 0.0, placeId = "p$id", city = "Ciudad $id", province = "Prov",
            status = status,
        )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repo: FakeProfileRepository,
        mode: EstablishmentLinkMode = EstablishmentLinkMode.RequestAccess,
    ) =
        MyEstablishmentsViewModel(
            mode = mode,
            getMyEstablishments = GetMyEstablishmentsUseCase(repo),
            withdrawFromEstablishment = WithdrawFromEstablishmentUseCase(repo),
        )

    private suspend fun ReceiveTurbine<MyEstablishmentsState>.awaitState(
        predicate: (MyEstablishmentsState) -> Boolean,
    ): MyEstablishmentsState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun `load shows all establishments ordered approved then pending then rejected`() =
        runTest(dispatcher) {
            val repo = FakeProfileRepository().apply {
                myEstablishmentsResult = Result.success(
                    listOf(
                        establishment("2", "Sur", EstablishmentStatus.Pending),
                        establishment("3", "Este", EstablishmentStatus.Rejected),
                        establishment("1", "Norte", EstablishmentStatus.Approved),
                        establishment("4", "Oeste", EstablishmentStatus.Approved),
                    ),
                )
            }
            val vm = viewModel(repo)

            vm.state.test {
                val state = awaitState { !it.isLoading }
                // Approved first (preserving server order), then pending, then rejected.
                assertEquals(
                    listOf("Norte", "Oeste", "Sur", "Este"),
                    state.establishments.map { it.name },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `load with no establishments yields empty list`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply { myEstablishmentsResult = Result.success(emptyList()) }
        val vm = viewModel(repo)

        vm.state.test {
            val state = awaitState { !it.isLoading }
            assertTrue(state.establishments.isEmpty())
            assertEquals(false, state.loadError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load failure sets loadError`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.failure(RuntimeException("boom"))
        }
        val vm = viewModel(repo)

        vm.state.test {
            val state = awaitState { it.loadError }
            assertTrue(state.loadError)
            assertEquals(false, state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `RequestAccessClick emits RequestEstablishment`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply { myEstablishmentsResult = Result.success(emptyList()) }
        val vm = viewModel(repo)

        vm.state.test {
            awaitState { !it.isLoading }
            vm.onAction(MyEstablishmentsAction.RequestAccessClick)
            val state = awaitState { it.destination != null }
            assertEquals(MyEstablishmentsDestination.RequestEstablishment, state.destination)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `BackClick emits Back`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply { myEstablishmentsResult = Result.success(emptyList()) }
        val vm = viewModel(repo)

        vm.state.test {
            awaitState { !it.isLoading }
            vm.onAction(MyEstablishmentsAction.BackClick)
            val state = awaitState { it.destination != null }
            assertEquals(MyEstablishmentsDestination.Back, state.destination)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `WithdrawClick opens the confirm dialog for the matching establishment`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(
                listOf(establishment("1", "Norte", EstablishmentStatus.Approved)),
            )
        }
        val vm = viewModel(repo)

        vm.state.test {
            awaitState { !it.isLoading }
            vm.onAction(MyEstablishmentsAction.WithdrawClick("1"))
            val state = awaitState { it.pendingWithdraw != null }
            assertEquals("Norte", state.pendingWithdraw?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `WithdrawClick with an unknown id is a no-op`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply { myEstablishmentsResult = Result.success(emptyList()) }
        val vm = viewModel(repo)

        vm.state.test {
            val loaded = awaitState { !it.isLoading }
            assertEquals(null, loaded.pendingWithdraw)
            vm.onAction(MyEstablishmentsAction.WithdrawClick("missing"))
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(null, vm.state.value.pendingWithdraw)
    }

    @Test
    fun `DismissWithdrawConfirm closes the dialog without withdrawing`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(
                listOf(establishment("1", "Norte", EstablishmentStatus.Approved)),
            )
        }
        val vm = viewModel(repo)

        vm.state.test {
            awaitState { !it.isLoading }
            vm.onAction(MyEstablishmentsAction.WithdrawClick("1"))
            awaitState { it.pendingWithdraw != null }
            vm.onAction(MyEstablishmentsAction.DismissWithdrawConfirm)
            val state = awaitState { it.pendingWithdraw == null }
            assertEquals(0, repo.withdrawCount)
            assertEquals(1, state.establishments.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ConfirmWithdraw success removes the establishment and shows a confirmation toast`() =
        runTest(dispatcher) {
            val repo = FakeProfileRepository().apply {
                myEstablishmentsResult = Result.success(
                    listOf(
                        establishment("1", "Norte", EstablishmentStatus.Approved),
                        establishment("2", "Sur", EstablishmentStatus.Pending),
                    ),
                )
                withdrawResult = Result.success(Unit)
            }
            val vm = viewModel(repo)

            vm.state.test {
                awaitState { !it.isLoading }
                vm.onAction(MyEstablishmentsAction.WithdrawClick("1"))
                awaitState { it.pendingWithdraw != null }
                vm.onAction(MyEstablishmentsAction.ConfirmWithdraw)
                val state = awaitState { it.successMessage != null }

                assertEquals("1", repo.lastWithdrawnId)
                assertEquals(listOf("2"), state.establishments.map { it.id })
                assertEquals(null, state.pendingWithdraw)
                assertEquals(false, state.isWithdrawing)
                assertTrue(state.successMessage!!.contains("Norte"))
                assertNull(state.withdrawError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ConfirmWithdraw network failure keeps the establishment and shows an error toast`() =
        runTest(dispatcher) {
            val repo = FakeProfileRepository().apply {
                myEstablishmentsResult = Result.success(
                    listOf(establishment("1", "Norte", EstablishmentStatus.Approved)),
                )
                withdrawResult = Result.failure(
                    EstablishmentWithdrawalFailure(EstablishmentWithdrawalError.Network, null),
                )
            }
            val vm = viewModel(repo)

            vm.state.test {
                awaitState { !it.isLoading }
                vm.onAction(MyEstablishmentsAction.WithdrawClick("1"))
                awaitState { it.pendingWithdraw != null }
                vm.onAction(MyEstablishmentsAction.ConfirmWithdraw)
                val state = awaitState { it.withdrawError != null }

                assertEquals(EstablishmentWithdrawalError.Network, state.withdrawError)
                assertEquals(listOf("1"), state.establishments.map { it.id })
                assertEquals(null, state.pendingWithdraw)
                assertEquals(false, state.isWithdrawing)
                assertNull(state.successMessage)
                assertEquals(1, repo.getMyEstablishmentsCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ConfirmWithdraw conflict failure resyncs the list from the server`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(
                listOf(establishment("1", "Norte", EstablishmentStatus.Approved)),
            )
            withdrawResult = Result.failure(
                EstablishmentWithdrawalFailure(EstablishmentWithdrawalError.NotWithdrawable, null),
            )
        }
        val vm = viewModel(repo)

        vm.state.test {
            awaitState { !it.isLoading }
            vm.onAction(MyEstablishmentsAction.WithdrawClick("1"))
            awaitState { it.pendingWithdraw != null }
            vm.onAction(MyEstablishmentsAction.ConfirmWithdraw)
            awaitState { it.withdrawError != null }
            // The failure re-triggers load(), which re-fetches from the (still-successful) repo.
            val reloaded = awaitState { !it.isLoading && it.withdrawError != null }
            assertEquals(2, repo.getMyEstablishmentsCount)
            assertEquals(listOf("1"), reloaded.establishments.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ToastDismissed clears both success and error messages`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(
                listOf(establishment("1", "Norte", EstablishmentStatus.Approved)),
            )
            withdrawResult = Result.success(Unit)
        }
        val vm = viewModel(repo)

        vm.state.test {
            awaitState { !it.isLoading }
            vm.onAction(MyEstablishmentsAction.WithdrawClick("1"))
            awaitState { it.pendingWithdraw != null }
            vm.onAction(MyEstablishmentsAction.ConfirmWithdraw)
            awaitState { it.successMessage != null }
            vm.onAction(MyEstablishmentsAction.ToastDismissed)
            val state = awaitState { it.successMessage == null }
            assertNull(state.withdrawError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Subscribe mode unsubscribes, flags the change and uses the unsubscribe copy`() =
        runTest(dispatcher) {
            val repo = FakeProfileRepository().apply {
                myEstablishmentsResult = Result.success(
                    listOf(establishment("1", "Norte", EstablishmentStatus.Approved)),
                )
            }
            val vm = viewModel(repo, mode = EstablishmentLinkMode.Subscribe)

            vm.state.test {
                val loaded = awaitState { !it.isLoading }
                assertEquals(EstablishmentLinkMode.Subscribe, loaded.mode)
                assertFalse(loaded.establishmentsChanged)
                vm.onAction(MyEstablishmentsAction.WithdrawClick("1"))
                awaitState { it.pendingWithdraw != null }
                vm.onAction(MyEstablishmentsAction.ConfirmWithdraw)
                val state = awaitState { it.successMessage != null }

                assertEquals(listOf("1"), repo.lastUnsubscribedIds)
                assertEquals(0, repo.withdrawCount)
                assertEquals("Te desuscribiste de Norte.", state.successMessage)
                assertTrue(state.establishments.isEmpty())
                assertTrue(state.establishmentsChanged)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onEstablishmentsRequested reloads and flags the change`() = runTest(dispatcher) {
        val repo = FakeProfileRepository()
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onEstablishmentsRequested()
        advanceUntilIdle()

        assertEquals(2, repo.getMyEstablishmentsCount)
        assertTrue(vm.state.value.establishmentsChanged)
        assertFalse(vm.state.value.isLoading)
    }
}
