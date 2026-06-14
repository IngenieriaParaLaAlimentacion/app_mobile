package ar.com.ingenieriaalimentacion.presentation.feature.profile.establishments

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    private fun viewModel(repo: FakeProfileRepository) =
        MyEstablishmentsViewModel(getMyEstablishments = GetMyEstablishmentsUseCase(repo))

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
}
