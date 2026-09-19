package ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_outflow

import ar.com.ingenieriaalimentacion.domain.model.food.FoodCategory
import ar.com.ingenieriaalimentacion.domain.model.food.FoodOutflowError
import ar.com.ingenieriaalimentacion.domain.model.food.FoodOutflowFailure
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockItem
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockPage
import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import ar.com.ingenieriaalimentacion.domain.model.food.StockSortOrder
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.usecase.food.CreateFoodOutflowUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetFoodCategoriesUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetFoodStockPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.FakeFoodCatalogRepository
import ar.com.ingenieriaalimentacion.fake.FakeFoodOutflowRepository
import ar.com.ingenieriaalimentacion.fake.FakeFoodStockRepository
import ar.com.ingenieriaalimentacion.fake.FakeIdGenerator
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class CreateFoodOutflowViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val establishment = establishment("e-1")
    private val arroz = stock("food-1", FoodUnit.Kilogramos, "10.000")
    private val huevos = stock("food-2", FoodUnit.Unidades, "10.000")

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun teardown() = Dispatchers.resetMain()

    private class Fixture(
        val stock: FakeFoodStockRepository,
        val outflows: FakeFoodOutflowRepository,
        val ids: FakeIdGenerator,
        val viewModel: CreateFoodOutflowViewModel,
    )

    private fun fixture(
        online: Boolean = true,
        establishments: List<EstablishmentWithStatus> = listOf(establishment),
        currentEstablishmentId: String? = establishment.id,
        stockPages: List<Result<FoodStockPage>> = listOf(Result.success(page(arroz))),
        categories: Result<List<FoodCategory>> = Result.success(
            listOf(FoodCategory("category-1", "Cereales")),
        ),
    ): Fixture {
        val stock = FakeFoodStockRepository().apply { pageResults = stockPages }
        val outflows = FakeFoodOutflowRepository()
        val ids = FakeIdGenerator()
        val profile = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(establishments)
            this.currentEstablishmentId = currentEstablishmentId
        }
        val catalog = FakeFoodCatalogRepository().apply { categoriesResult = categories }
        return Fixture(
            stock = stock,
            outflows = outflows,
            ids = ids,
            viewModel = CreateFoodOutflowViewModel(
                createFoodOutflow = CreateFoodOutflowUseCase(outflows),
                getFoodStockPage = GetFoodStockPageUseCase(stock),
                getFoodCategories = GetFoodCategoriesUseCase(catalog),
                getMyEstablishments = GetMyEstablishmentsUseCase(profile),
                getCurrentEstablishment = GetCurrentEstablishmentUseCase(profile),
                connectivity = FakeConnectivityObserver(initial = online),
                idGenerator = ids,
            ),
        )
    }

    private fun readyFixture(
        stockPages: List<Result<FoodStockPage>> = listOf(Result.success(page(arroz))),
    ) = fixture(stockPages = stockPages)

    private fun submitValid(fixture: Fixture, quantity: String = "4") {
        fixture.viewModel.onAction(CreateFoodOutflowAction.FoodPicked(arroz))
        fixture.viewModel.onAction(
            CreateFoodOutflowAction.LineQuantityChanged(arroz.foodId, quantity),
        )
        fixture.viewModel.onAction(CreateFoodOutflowAction.Submit)
    }

    @Test
    fun `initial stock request is current establishment available page one`() = runTest(dispatcher) {
        val f = fixture(stockPages = listOf(Result.success(page(arroz, hasMore = true))))
        advanceUntilIdle()

        val (_, filters, requestedPage) = f.stock.requests.single()
        assertTrue(filters.onlyAvailable)
        assertNull(filters.categoryId)
        assertEquals(StockSortOrder.NameAsc, filters.sortOrder)
        assertEquals(1, requestedPage)
        assertEquals(20, f.stock.requestedPageSizes.single())
    }

    @Test
    fun `category change replaces items and restarts page one`() = runTest(dispatcher) {
        val filtered = stock("food-3", FoodUnit.Litros, "2.000")
        val f = fixture(
            stockPages = listOf(
                Result.success(page(arroz, hasMore = true)),
                Result.success(page(filtered)),
            ),
        )
        advanceUntilIdle()

        f.viewModel.onAction(CreateFoodOutflowAction.CategorySelected("category-1"))
        advanceUntilIdle()

        assertEquals(listOf(filtered), f.viewModel.state.value.stock.items)
        assertEquals(listOf(1, 1), f.stock.requests.map { it.third })
        assertEquals("category-1", f.stock.requests.last().second.categoryId)
    }

    @Test
    fun `load more appends page two without client search`() = runTest(dispatcher) {
        val second = stock("food-3", FoodUnit.Litros, "2.000")
        val f = fixture(
            stockPages = listOf(
                Result.success(page(arroz, hasMore = true)),
                Result.success(page(second)),
            ),
        )
        advanceUntilIdle()

        f.viewModel.onAction(CreateFoodOutflowAction.LoadMoreStock)
        advanceUntilIdle()

        assertEquals(listOf(arroz, second), f.viewModel.state.value.stock.items)
        assertEquals(listOf(1, 2), f.stock.requests.map { it.third })
    }

    @Test
    fun `duplicate food selection creates only one line`() = runTest(dispatcher) {
        val f = readyFixture()
        advanceUntilIdle()

        f.viewModel.onAction(CreateFoodOutflowAction.FoodPicked(arroz))
        f.viewModel.onAction(CreateFoodOutflowAction.FoodPicked(arroz))

        assertEquals(1, f.viewModel.state.value.lines.size)
    }

    @Test
    fun `changing establishment clears lines and refreshes its stock`() = runTest(dispatcher) {
        val other = establishment("e-2")
        val f = fixture(establishments = listOf(establishment, other))
        advanceUntilIdle()
        f.viewModel.onAction(CreateFoodOutflowAction.FoodPicked(arroz))

        f.viewModel.onAction(CreateFoodOutflowAction.EstablishmentSelected(other))
        advanceUntilIdle()

        assertTrue(f.viewModel.state.value.lines.isEmpty())
        assertEquals("e-2", f.stock.requests.last().first)
        assertEquals(1, f.stock.requests.last().third)
    }

    @Test
    fun `unidades rejects decimals and quantity cannot exceed available`() = runTest(dispatcher) {
        val f = fixture(stockPages = listOf(Result.success(page(huevos))))
        advanceUntilIdle()
        f.viewModel.onAction(CreateFoodOutflowAction.FoodPicked(huevos))
        f.viewModel.onAction(CreateFoodOutflowAction.LineQuantityChanged(huevos.foodId, "1.5"))

        assertEquals("15", f.viewModel.state.value.lines.single().quantity.value)
        f.viewModel.onAction(CreateFoodOutflowAction.Submit)
        advanceUntilIdle()

        assertTrue(f.outflows.created.isEmpty())
        assertEquals("Disponible: 10.000.", f.viewModel.state.value.lines.single().quantity.displayError)
    }

    @Test
    fun `kilograms accept comma and preserve exact normalized value`() = runTest(dispatcher) {
        val f = readyFixture()
        advanceUntilIdle()
        submitValid(f, quantity = "0,750")
        advanceUntilIdle()

        assertEquals("0.750", f.outflows.created.single().items.single().quantity)
    }

    @Test
    fun `offline blocks stock load and submit`() = runTest(dispatcher) {
        val f = fixture(online = false)
        advanceUntilIdle()

        assertTrue(f.stock.requests.isEmpty())
        f.viewModel.onAction(CreateFoodOutflowAction.FoodPicked(arroz))
        f.viewModel.onAction(CreateFoodOutflowAction.LineQuantityChanged(arroz.foodId, "1"))
        f.viewModel.onAction(CreateFoodOutflowAction.Submit)
        advanceUntilIdle()

        assertTrue(f.outflows.created.isEmpty())
        assertFalse(f.viewModel.state.value.canSubmit)
    }

    @Test
    fun `past date submits and future date is rejected`() = runTest(dispatcher) {
        val past = readyFixture()
        advanceUntilIdle()
        past.viewModel.onAction(CreateFoodOutflowAction.OccurredAtChanged(dateDigits(-7)))
        submitValid(past)
        advanceUntilIdle()
        assertEquals(1, past.outflows.created.size)

        val future = readyFixture()
        advanceUntilIdle()
        future.viewModel.onAction(CreateFoodOutflowAction.OccurredAtChanged(dateDigits(1)))
        submitValid(future)
        advanceUntilIdle()
        assertTrue(future.outflows.created.isEmpty())
        assertEquals(
            "La fecha no puede ser futura.",
            future.viewModel.state.value.occurredAt.displayError,
        )
    }

    @Test
    fun `failed submit retry keeps the same id and form`() = runTest(dispatcher) {
        val f = readyFixture()
        f.outflows.result = Result.failure(FoodOutflowFailure(FoodOutflowError.Network, null))
        advanceUntilIdle()
        submitValid(f)
        advanceUntilIdle()
        f.viewModel.onAction(CreateFoodOutflowAction.Submit)
        advanceUntilIdle()

        assertEquals(2, f.outflows.created.size)
        assertEquals(f.outflows.created[0].id, f.outflows.created[1].id)
        assertTrue(f.viewModel.state.value.lines.isNotEmpty())
    }

    @Test
    fun `success resets form mints id keeps establishment and refreshes stock`() = runTest(dispatcher) {
        val f = readyFixture()
        advanceUntilIdle()
        val firstId = f.viewModel.state.value.outflowId
        f.viewModel.onAction(CreateFoodOutflowAction.CommentsChanged("Almuerzo"))
        submitValid(f)
        advanceUntilIdle()

        with(f.viewModel.state.value) {
            assertTrue(outflowId != firstId)
            assertTrue(lines.isEmpty())
            assertEquals("", comments.value)
            assertEquals(establishment, selectedEstablishment)
            assertEquals("Egreso registrado", successMessage)
        }
        assertTrue(f.stock.requests.size >= 2)
    }

    @Test
    fun `409 preserves id and form updates availability and refreshes stock`() = runTest(dispatcher) {
        val f = readyFixture()
        advanceUntilIdle()
        val id = f.viewModel.state.value.outflowId
        f.outflows.result = Result.failure(
            FoodOutflowFailure(
                FoodOutflowError.InsufficientStock(arroz.foodId, "4", "2.000"),
                null,
            ),
        )
        submitValid(f)
        advanceUntilIdle()

        assertEquals(id, f.viewModel.state.value.outflowId)
        assertEquals("2.000", f.viewModel.state.value.lines.single().stock.quantity)
        assertTrue(f.viewModel.state.value.submitError!!.contains("Disponible: 2"))
        assertTrue(f.stock.requests.size >= 2)
    }

    private fun dateDigits(daysFromToday: Int): String {
        val date = Clock.System.now()
            .plus(kotlin.time.Duration.parse("${daysFromToday}d"))
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        return date.day.toString().padStart(2, '0') +
            date.month.number.toString().padStart(2, '0') + date.year
    }

    private companion object {
        fun establishment(id: String) = EstablishmentWithStatus(
            id = id,
            name = "Comedor $id",
            formattedAddress = "Calle 1",
            latitude = 0.0,
            longitude = 0.0,
            placeId = "p-$id",
            city = "Córdoba",
            province = "Córdoba",
            status = EstablishmentStatus.Approved,
        )

        fun stock(id: String, unit: FoodUnit, quantity: String) = FoodStockItem(
            foodId = id,
            foodName = "Alimento $id",
            categoryId = "category-1",
            categoryName = "Cereales",
            unit = unit,
            quantity = quantity,
            updatedAtMillis = 0L,
        )

        fun page(vararg items: FoodStockItem, hasMore: Boolean = false) =
            FoodStockPage(items.toList(), hasMore)
    }
}
