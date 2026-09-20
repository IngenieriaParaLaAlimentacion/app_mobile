package ar.com.ingenieriaalimentacion.presentation.feature.food.food_overview

import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementDirection
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementError
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementFailure
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementPage
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementSummary
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockError
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockFailure
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockFilters
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockItem
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockPage
import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import ar.com.ingenieriaalimentacion.domain.model.food.ResponsibleRef
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.repository.FoodStockRepository
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetEstablishmentFoodMovementPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetFoodCategoriesUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetFoodStockPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.ResolveCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.FakeEstablishmentFoodMovementRepository
import ar.com.ingenieriaalimentacion.fake.FakeFoodCatalogRepository
import ar.com.ingenieriaalimentacion.fake.FakeFoodStockRepository
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FoodOverviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun teardown() = Dispatchers.resetMain()

    private data class Fixture(
        val stockRepo: FakeFoodStockRepository,
        val movementRepo: FakeEstablishmentFoodMovementRepository,
        val viewModel: FoodOverviewViewModel,
    )

    private fun fixture(
        currentEstablishmentId: String? = "e1",
        establishments: List<EstablishmentWithStatus> = listOf(establishment()),
        stockResults: List<Result<FoodStockPage>> = listOf(
            Result.success(FoodStockPage(listOf(stock("s1")), true)),
        ),
        movementResults: List<Result<EstablishmentFoodMovementPage>> = listOf(
            Result.success(EstablishmentFoodMovementPage(listOf(movement("m1")), false)),
        ),
    ): Fixture {
        val profile = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(establishments)
            this.currentEstablishmentId = currentEstablishmentId
        }
        val stockRepo = FakeFoodStockRepository().apply { pageResults = stockResults }
        val movementRepo = FakeEstablishmentFoodMovementRepository().apply {
            pageResults = movementResults
        }
        val viewModel = FoodOverviewViewModel(
            resolveCurrentEstablishment = ResolveCurrentEstablishmentUseCase(profile),
            getStockPage = GetFoodStockPageUseCase(stockRepo),
            getMovementPage = GetEstablishmentFoodMovementPageUseCase(movementRepo),
            getFoodCategories = GetFoodCategoriesUseCase(FakeFoodCatalogRepository()),
            connectivity = FakeConnectivityObserver(initial = true),
        )
        return Fixture(stockRepo, movementRepo, viewModel)
    }

    @Test
    fun `initial load scopes both sections and requests a five item preview`() = runTest(dispatcher) {
        val fixture = fixture()
        advanceUntilIdle()

        assertEquals("e1", fixture.stockRepo.requests.single().first)
        assertEquals("e1", fixture.movementRepo.pageRequests.single().establishmentId)
        assertEquals(5, fixture.movementRepo.pageRequests.single().pageSize)
        assertEquals(listOf("s1"), fixture.viewModel.state.value.stock.items.map { it.foodId })
        assertEquals(listOf("m1"), fixture.viewModel.state.value.movements.items.map { it.id })
    }

    @Test
    fun `missing or stale selection makes no section requests`() = runTest(dispatcher) {
        val missing = fixture(currentEstablishmentId = null)
        advanceUntilIdle()
        assertEquals(EstablishmentProblem.NoneSelected, missing.viewModel.state.value.establishmentProblem)
        assertTrue(missing.stockRepo.requests.isEmpty())
        assertTrue(missing.movementRepo.pageRequests.isEmpty())

        val stale = fixture(currentEstablishmentId = "revoked")
        advanceUntilIdle()
        assertEquals(EstablishmentProblem.StaleSelection, stale.viewModel.state.value.establishmentProblem)
        assertTrue(stale.stockRepo.requests.isEmpty())
        assertTrue(stale.movementRepo.pageRequests.isEmpty())
    }

    @Test
    fun `stock and movement errors are independent`() = runTest(dispatcher) {
        val fixture = fixture(
            stockResults = listOf(Result.failure(FoodStockFailure(FoodStockError.Network, null))),
            movementResults = listOf(
                Result.success(EstablishmentFoodMovementPage(listOf(movement("m1")), false)),
            ),
        )
        advanceUntilIdle()

        assertEquals(FoodOverviewSectionError.Retryable, fixture.viewModel.state.value.stock.error)
        assertNull(fixture.viewModel.state.value.movements.error)
        assertEquals(listOf("m1"), fixture.viewModel.state.value.movements.items.map { it.id })
    }

    @Test
    fun `filter change replaces page one without refreshing movements`() = runTest(dispatcher) {
        val fixture = fixture(
            stockResults = listOf(
                Result.success(FoodStockPage(listOf(stock("s1")), true)),
                Result.success(FoodStockPage(listOf(stock("filtered")), false)),
            ),
        )
        advanceUntilIdle()

        fixture.viewModel.onAction(FoodOverviewAction.OnlyAvailableChanged(false))
        advanceUntilIdle()

        assertEquals(listOf(1, 1), fixture.stockRepo.requests.map { it.third })
        assertFalse(fixture.stockRepo.requests.last().second.onlyAvailable)
        assertEquals(listOf("filtered"), fixture.viewModel.state.value.stock.items.map { it.foodId })
        assertEquals(1, fixture.movementRepo.pageRequests.size)
    }

    @Test
    fun `late cancelled stock response cannot overwrite a newer filter`() = runTest(dispatcher) {
        val oldResponseGate = CompletableDeferred<Unit>()
        val stockRepo = object : FoodStockRepository {
            var calls = 0

            override suspend fun getStockPage(
                establishmentId: String,
                filters: FoodStockFilters,
                page: Int,
                pageSize: Int,
            ): Result<FoodStockPage> {
                calls++
                return if (calls == 1) {
                    withContext(NonCancellable) { oldResponseGate.await() }
                    Result.success(FoodStockPage(listOf(stock("old")), false))
                } else {
                    Result.success(FoodStockPage(listOf(stock("new")), false))
                }
            }
        }
        val profile = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(listOf(establishment()))
            currentEstablishmentId = "e1"
        }
        val movementRepo = FakeEstablishmentFoodMovementRepository().apply {
            pageResults = listOf(
                Result.success(EstablishmentFoodMovementPage(listOf(movement("m1")), false)),
            )
        }
        val viewModel = FoodOverviewViewModel(
            resolveCurrentEstablishment = ResolveCurrentEstablishmentUseCase(profile),
            getStockPage = GetFoodStockPageUseCase(stockRepo),
            getMovementPage = GetEstablishmentFoodMovementPageUseCase(movementRepo),
            getFoodCategories = GetFoodCategoriesUseCase(FakeFoodCatalogRepository()),
            connectivity = FakeConnectivityObserver(initial = true),
        )
        runCurrent()

        viewModel.onAction(FoodOverviewAction.OnlyAvailableChanged(false))
        runCurrent()
        assertEquals(listOf("new"), viewModel.state.value.stock.items.map { it.foodId })

        oldResponseGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("new"), viewModel.state.value.stock.items.map { it.foodId })
    }

    @Test
    fun `load more appends and a double tap starts one request`() = runTest(dispatcher) {
        val fixture = fixture(
            stockResults = listOf(
                Result.success(FoodStockPage(listOf(stock("s1")), true)),
                Result.success(FoodStockPage(listOf(stock("s2")), false)),
            ),
        )
        advanceUntilIdle()

        fixture.viewModel.onAction(FoodOverviewAction.LoadMoreStock)
        fixture.viewModel.onAction(FoodOverviewAction.LoadMoreStock)
        advanceUntilIdle()

        assertEquals(listOf(1, 2), fixture.stockRepo.requests.map { it.third })
        assertEquals(listOf("s1", "s2"), fixture.viewModel.state.value.stock.items.map { it.foodId })
        assertEquals(1, fixture.movementRepo.pageRequests.size)
    }

    @Test
    fun `retry reloads only the requested failed sections`() = runTest(dispatcher) {
        val fixture = fixture(
            stockResults = listOf(
                Result.failure(FoodStockFailure(FoodStockError.Network, null)),
                Result.success(FoodStockPage(listOf(stock("s2")), false)),
            ),
            movementResults = listOf(
                Result.failure(
                    EstablishmentFoodMovementFailure(EstablishmentFoodMovementError.Network),
                ),
                Result.success(EstablishmentFoodMovementPage(listOf(movement("m2")), false)),
            ),
        )
        advanceUntilIdle()

        fixture.viewModel.onAction(FoodOverviewAction.RetryStock)
        fixture.viewModel.onAction(FoodOverviewAction.RetryMovements)
        advanceUntilIdle()

        assertEquals(listOf("s2"), fixture.viewModel.state.value.stock.items.map { it.foodId })
        assertEquals(listOf("m2"), fixture.viewModel.state.value.movements.items.map { it.id })
        assertEquals(2, fixture.stockRepo.requests.size)
        assertEquals(2, fixture.movementRepo.pageRequests.size)
    }

    @Test
    fun `mutation refreshes page one of both sections`() = runTest(dispatcher) {
        val fixture = fixture(
            movementResults = listOf(
                Result.success(EstablishmentFoodMovementPage(listOf(movement("m1")), false)),
                Result.success(EstablishmentFoodMovementPage(listOf(movement("m2")), false)),
            ),
        )
        advanceUntilIdle()

        fixture.viewModel.refreshAfterMutation()
        advanceUntilIdle()

        assertEquals(listOf(1, 1), fixture.stockRepo.requests.map { it.third })
        assertEquals(listOf(1, 1), fixture.movementRepo.pageRequests.map { it.page })
    }

    @Test
    fun `movement click carries establishment and destination clears`() = runTest(dispatcher) {
        val fixture = fixture()
        advanceUntilIdle()

        fixture.viewModel.onAction(FoodOverviewAction.MovementClick("m1"))
        assertEquals(
            FoodOverviewDestination.MovementDetail("e1", "m1"),
            fixture.viewModel.state.value.destination,
        )
        fixture.viewModel.clearDestination()
        assertNull(fixture.viewModel.state.value.destination)
    }

    private companion object {
        fun establishment() = EstablishmentWithStatus(
            id = "e1",
            name = "Comedor",
            formattedAddress = "Calle 1",
            latitude = 0.0,
            longitude = 0.0,
            placeId = "p1",
            city = "Ciudad",
            province = "Provincia",
            status = EstablishmentStatus.Approved,
        )

        fun stock(id: String) = FoodStockItem(
            foodId = id,
            foodName = "Alimento $id",
            categoryId = "c1",
            categoryName = "Cereales",
            unit = FoodUnit.Kilogramos,
            quantity = "1.000",
            updatedAtMillis = 0L,
        )

        fun movement(id: String) = EstablishmentFoodMovementSummary(
            id = id,
            direction = EstablishmentFoodMovementDirection.In,
            occurredAtMillis = 0L,
            responsible = ResponsibleRef("u1", "Ana", "García"),
            foodCount = 1,
        )
    }
}
