package ar.com.ingenieriaalimentacion.presentation.feature.profile.request_establishment

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.domain.model.profile.AvailableEstablishment
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.PagedEstablishments
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetAvailableEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.RequestEstablishmentAccessUseCase
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RequestEstablishmentViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun establishment(id: String) =
        AvailableEstablishment(id, "Est $id", "Calle $id", 0.0, 0.0, "p$id", "Ciudad", "Provincia")

    private val a = establishment("a")
    private val b = establishment("b")
    private val c = establishment("c")
    private val d = establishment("d")

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun pagedRepo(): FakeProfileRepository = FakeProfileRepository().apply {
        availableResponder = { search, page ->
            when {
                search.isNullOrBlank() && page == 1 ->
                    Result.success(PagedEstablishments(listOf(a, b), page = 1, totalPages = 2, total = 4))
                search.isNullOrBlank() && page == 2 ->
                    Result.success(PagedEstablishments(listOf(c, d), page = 2, totalPages = 2, total = 4))
                search == "norte" ->
                    Result.success(PagedEstablishments(listOf(a), page = 1, totalPages = 1, total = 1))
                else ->
                    Result.success(PagedEstablishments(emptyList(), page = 1, totalPages = 1, total = 0))
            }
        }
    }

    private fun viewModel(repo: FakeProfileRepository) = RequestEstablishmentViewModel(
        getAvailable = GetAvailableEstablishmentsUseCase(repo),
        requestAccess = RequestEstablishmentAccessUseCase(repo),
    )

    private suspend fun ReceiveTurbine<RequestEstablishmentState>.awaitState(
        predicate: (RequestEstablishmentState) -> Boolean,
    ): RequestEstablishmentState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun `initial load fetches page 1`() = runTest(dispatcher) {
        val vm = viewModel(pagedRepo())

        vm.state.test {
            val loaded = awaitState { !it.isLoading && it.results.isNotEmpty() }
            assertEquals(listOf(a, b), loaded.results)
            assertEquals(1, loaded.page)
            assertEquals(2, loaded.totalPages)
            assertTrue(loaded.canLoadMore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load more appends next page and respects totalPages`() = runTest(dispatcher) {
        val repo = pagedRepo()
        val vm = viewModel(repo)

        advanceUntilIdle() // page 1
        vm.onAction(RequestEstablishmentAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf(a, b, c, d), vm.state.value.results)
        assertEquals(2, vm.state.value.page)
        assertFalse(vm.state.value.canLoadMore) // page == totalPages

        // Further LoadMore is a no-op.
        val callsBefore = repo.availableCalls.size
        vm.onAction(RequestEstablishmentAction.LoadMore)
        advanceUntilIdle()
        assertEquals(callsBefore, repo.availableCalls.size)
    }

    @Test
    fun `search resets to page 1 and replaces results`() = runTest(dispatcher) {
        val repo = pagedRepo()
        val vm = viewModel(repo)

        advanceUntilIdle() // initial blank page 1
        vm.onAction(RequestEstablishmentAction.LoadMore)
        advanceUntilIdle() // now have a,b,c,d

        vm.onAction(RequestEstablishmentAction.QueryChanged("norte"))
        advanceUntilIdle() // debounced reload

        assertEquals(listOf(a), vm.state.value.results)
        assertEquals(1, vm.state.value.page)
        assertEquals(Triple("norte", 1, 20), repo.availableCalls.last())
    }

    @Test
    fun `toggle adds then removes the id`() = runTest(dispatcher) {
        val vm = viewModel(pagedRepo())

        advanceUntilIdle()
        vm.onAction(RequestEstablishmentAction.EstablishmentToggled("a"))
        assertTrue(vm.state.value.isSelected("a"))

        vm.onAction(RequestEstablishmentAction.EstablishmentToggled("a"))
        assertFalse(vm.state.value.isSelected("a"))
    }

    @Test
    fun `submit success emits Submitted`() = runTest(dispatcher) {
        val repo = pagedRepo().apply {
            requestResult = Result.success(
                listOf(
                    EstablishmentWithStatus(
                        "a", "Est a", "Calle a", 0.0, 0.0, "pa", "Ciudad", "Provincia",
                        EstablishmentStatus.Pending,
                    ),
                ),
            )
        }
        val vm = viewModel(repo)

        advanceUntilIdle()
        vm.onAction(RequestEstablishmentAction.EstablishmentToggled("a"))

        vm.state.test {
            vm.onAction(RequestEstablishmentAction.SubmitClick)
            val state = awaitState { it.destination != null }
            assertEquals(RequestEstablishmentDestination.Submitted, state.destination)
            assertEquals(listOf("a"), repo.lastRequestedIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submit failure sets submitError`() = runTest(dispatcher) {
        val repo = pagedRepo().apply { requestResult = Result.failure(RuntimeException("boom")) }
        val vm = viewModel(repo)

        advanceUntilIdle()
        vm.onAction(RequestEstablishmentAction.EstablishmentToggled("a"))

        vm.state.test {
            vm.onAction(RequestEstablishmentAction.SubmitClick)
            val state = awaitState { it.submitError != null }
            assertTrue(state.submitError != null)
            assertFalse(state.isSubmitting)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
