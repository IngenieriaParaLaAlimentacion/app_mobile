package ar.com.ingenieriaalimentacion.presentation.feature.food.food_movement_history

import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovement
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovementDirection
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovementHistoryPage
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetFoodMovementHistoryPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.ObserveFoodMovementsUseCase
import ar.com.ingenieriaalimentacion.fake.FakeFoodMovementHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FoodMovementHistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun teardown() = Dispatchers.resetMain()

    /**
     * `state` is a `stateIn(WhileSubscribed)`, so it only mirrors the ViewModel's internal flow
     * while something collects it. Every test therefore keeps a background collector alive.
     */
    private fun TestScope.viewModel(repo: FakeFoodMovementHistoryRepository): FoodMovementHistoryViewModel {
        val vm = FoodMovementHistoryViewModel(
            observeFoodMovements = ObserveFoodMovementsUseCase(repo),
            getHistoryPage = GetFoodMovementHistoryPageUseCase(repo),
        )
        backgroundScope.launch { vm.state.collect {} }
        return vm
    }

    private fun movement(id: String, occurredAt: String) = FoodMovement(
        id = id,
        direction = FoodMovementDirection.Intake,
        occurredAt = occurredAt,
        occurredAtMillis = Instant.parse(occurredAt).toEpochMilliseconds(),
        establishmentName = "Comedor",
    )

    private fun page(
        cutoffMillis: Long? = null,
        hasMore: Boolean = false,
    ) = Result.success(FoodMovementHistoryPage(emptyList(), cutoffMillis, hasMore))

    @Test
    fun `items come from the cache flow and a page success advances the pagination state`() = runTest(dispatcher) {
        val repo = FakeFoodMovementHistoryRepository()
        repo.localMerged.value = listOf(movement("a", "2026-04-03T00:00:00Z"))
        repo.pageResults = listOf(page(cutoffMillis = 10L, hasMore = true))
        val vm = viewModel(repo)

        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf("a"), state.items.map { it.id })
        assertEquals(false, state.isLoading)
        assertEquals(1, state.page)
        assertEquals(true, state.hasMore)
        assertEquals(10L, state.cutoffMillis)
        assertNull(state.error)
        assertEquals(listOf(1 to 20), repo.requestedPages)
    }

    @Test
    fun `a page-1 failure produces the blocking retryable error state`() = runTest(dispatcher) {
        val repo = FakeFoodMovementHistoryRepository()
        repo.pageResults = listOf(Result.failure(IllegalStateException("offline")))
        val vm = viewModel(repo)

        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(FoodMovementHistoryError.Retryable, state.error)
        assertTrue(state.showsBlockingError)
        assertEquals(false, state.canLoadMore)
        assertEquals(false, state.isEmpty)
    }

    @Test
    fun `RetryLoad clears the error and a successful refresh restores the section`() = runTest(dispatcher) {
        val repo = FakeFoodMovementHistoryRepository()
        repo.pageResults = listOf(Result.failure(IllegalStateException("offline")), page())
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onAction(FoodMovementHistoryAction.RetryLoad)
        advanceUntilIdle()

        assertNull(vm.state.value.error)
        assertEquals(false, vm.state.value.showsBlockingError)
        assertEquals(listOf(1 to 20, 1 to 20), repo.requestedPages)
    }

    @Test
    fun `a loadMore failure keeps the content and never sets the error`() = runTest(dispatcher) {
        val repo = FakeFoodMovementHistoryRepository()
        repo.localMerged.value = listOf(movement("a", "2026-04-03T00:00:00Z"))
        repo.pageResults = listOf(page(hasMore = true), Result.failure(IllegalStateException("boom")))
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onAction(FoodMovementHistoryAction.LoadMore)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(false, state.isLoadingMore)
        assertNull(state.error)
        assertEquals(listOf("a"), state.items.map { it.id })
        assertEquals(true, state.hasMore)
    }

    @Test
    fun `the cutoff is monotonically non-increasing and a null from a later page wins`() = runTest(dispatcher) {
        val repo = FakeFoodMovementHistoryRepository()
        repo.pageResults = listOf(
            page(cutoffMillis = 100L, hasMore = true),
            page(cutoffMillis = 200L, hasMore = true), // a higher cut must not hide visible rows
            page(cutoffMillis = null, hasMore = false),
        )
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onAction(FoodMovementHistoryAction.LoadMore)
        advanceUntilIdle()
        assertEquals(100L, vm.state.value.cutoffMillis)

        vm.onAction(FoodMovementHistoryAction.LoadMore)
        advanceUntilIdle()
        assertNull(vm.state.value.cutoffMillis)
    }

    @Test
    fun `visibleItems hides rows below the cutoff and reveals them once it drops`() = runTest(dispatcher) {
        val repo = FakeFoodMovementHistoryRepository()
        val newer = movement("a", "2026-04-03T00:00:00Z")
        val older = movement("b", "2026-04-01T00:00:00Z")
        repo.localMerged.value = listOf(newer, older)
        repo.pageResults = listOf(
            page(cutoffMillis = newer.occurredAtMillis, hasMore = true),
            page(cutoffMillis = null, hasMore = false),
        )
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertEquals(listOf("a"), vm.state.value.visibleItems.map { it.id })

        vm.onAction(FoodMovementHistoryAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), vm.state.value.visibleItems.map { it.id })
    }

    @Test
    fun `an empty feed that finished loading reports isEmpty, not an error`() = runTest(dispatcher) {
        val vm = viewModel(FakeFoodMovementHistoryRepository())

        advanceUntilIdle()

        assertTrue(vm.state.value.isEmpty)
        assertNull(vm.state.value.error)
    }
}
