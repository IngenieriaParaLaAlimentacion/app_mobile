package ar.com.ingenieriaalimentacion.presentation.feature.food.food_stock

import ar.com.ingenieriaalimentacion.domain.model.food.FoodCategory
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockError
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockFailure
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockItem
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockPage
import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import ar.com.ingenieriaalimentacion.domain.model.food.StockSortOrder
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetFoodCategoriesUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetFoodStockPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.ResolveCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.SetCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.FakeFoodCatalogRepository
import ar.com.ingenieriaalimentacion.fake.FakeFoodStockRepository
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class FoodStockViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun teardown() = Dispatchers.resetMain()

    private fun establishment(id: String) = EstablishmentWithStatus(
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

    private fun row(id: String) = FoodStockItem(
        foodId = id,
        foodName = "Alimento $id",
        categoryId = "c1",
        categoryName = "Cereales",
        unit = FoodUnit.Kilogramos,
        quantity = "1.000",
        updatedAtMillis = 0L,
    )

    private fun page(vararg ids: String, hasMore: Boolean = false) =
        Result.success(FoodStockPage(ids.map { row(it) }, hasMore))

    /** Keeps a background collector alive, so every test sees the same flow the screen sees. */
    private fun TestScope.viewModel(
        stock: FakeFoodStockRepository,
        profile: FakeProfileRepository = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(listOf(establishment("e1")))
            currentEstablishmentId = "e1"
        },
        catalog: FakeFoodCatalogRepository = FakeFoodCatalogRepository(),
        // FakeConnectivityObserver's `initial` defaults to false (offline); this screen is online
        // by default in every test that is not about connectivity.
        connectivity: FakeConnectivityObserver = FakeConnectivityObserver(initial = true),
    ): FoodStockViewModel {
        val vm = FoodStockViewModel(
            resolveCurrentEstablishment = ResolveCurrentEstablishmentUseCase(profile),
            setCurrentEstablishment = SetCurrentEstablishmentUseCase(profile),
            getFoodStockPage = GetFoodStockPageUseCase(stock),
            getFoodCategories = GetFoodCategoriesUseCase(catalog),
            connectivity = connectivity,
        )
        backgroundScope.launch { vm.state.collect {} }
        return vm
    }

    @Test
    fun `first load scopes to the current establishment and defaults only_available to true`() = runTest(dispatcher) {
        val stock = FakeFoodStockRepository().apply { pageResults = listOf(page("a")) }
        val vm = viewModel(stock)

        advanceUntilIdle()

        val (establishmentId, filters, requestedPage) = stock.requests.single()
        assertEquals("e1", establishmentId)
        assertEquals(true, filters.onlyAvailable)
        assertNull(filters.categoryId)
        assertEquals(StockSortOrder.NameAsc, filters.sortOrder)
        assertEquals(1, requestedPage)
        assertEquals(listOf(20), stock.requestedPageSizes)
        assertEquals(listOf("a"), vm.state.value.items.map { it.foodId })
        assertEquals(false, vm.state.value.isLoading)
        assertEquals("e1", vm.state.value.establishment?.id)
    }

    @Test
    fun `loadMore ACCUMULATES — there is no cache flow to own the items`() = runTest(dispatcher) {
        val stock = FakeFoodStockRepository().apply {
            pageResults = listOf(page("a", "b", hasMore = true), page("c", hasMore = false))
        }
        val vm = viewModel(stock)
        advanceUntilIdle()

        vm.onAction(FoodStockAction.LoadMore)
        advanceUntilIdle()

        // The history precedent would still show ["a","b"] here — its items come from the cache.
        assertEquals(listOf("a", "b", "c"), vm.state.value.items.map { it.foodId })
        assertEquals(2, vm.state.value.page)
        assertEquals(false, vm.state.value.hasMore)
    }

    @Test
    fun `a duplicate row across pages is NOT deduped — the backend tiebreaker owns that`() = runTest(dispatcher) {
        val stock = FakeFoodStockRepository().apply {
            pageResults = listOf(page("a", hasMore = true), page("a", hasMore = false))
        }
        val vm = viewModel(stock)
        advanceUntilIdle()

        vm.onAction(FoodStockAction.LoadMore)
        advanceUntilIdle()

        // Documents the contract, not a desirable UX: a client-side distinctBy would hide skipped
        // rows while only papering over duplicated ones. Fixed by the ORDER BY id tiebreaker.
        assertEquals(listOf("a", "a"), vm.state.value.items.map { it.foodId })
    }

    @Test
    fun `a sort change resets to page 1 and REPLACES the items`() = runTest(dispatcher) {
        val stock = FakeFoodStockRepository().apply {
            pageResults = listOf(
                page("a", "b", hasMore = true),
                page("c", "d", hasMore = true),
                page("z", hasMore = false),
            )
        }
        val vm = viewModel(stock)
        advanceUntilIdle()
        vm.onAction(FoodStockAction.LoadMore)
        advanceUntilIdle()
        assertEquals(4, vm.state.value.items.size)

        vm.onAction(FoodStockAction.SortSelected(StockSortOrder.QuantityDesc))
        advanceUntilIdle()

        assertEquals(listOf("z"), vm.state.value.items.map { it.foodId })
        assertEquals(1, vm.state.value.page)
        assertEquals(StockSortOrder.QuantityDesc, vm.state.value.filters.sortOrder)
        assertEquals(false, vm.state.value.showSortSheet)
        assertEquals(listOf(1, 2, 1), stock.requests.map { it.third })
        assertEquals("quantity_desc", stock.requests.last().second.sortOrder.wireValue)
    }

    @Test
    fun `a category change resets to page 1 and forwards the id`() = runTest(dispatcher) {
        val stock = FakeFoodStockRepository().apply { pageResults = listOf(page("a"), page("b")) }
        val vm = viewModel(stock)
        advanceUntilIdle()

        vm.onAction(FoodStockAction.CategorySelected("c-9"))
        advanceUntilIdle()

        assertEquals("c-9", stock.requests.last().second.categoryId)
        assertEquals(1, stock.requests.last().third)
        assertEquals(listOf("b"), vm.state.value.items.map { it.foodId })
        assertEquals(false, vm.state.value.showCategorySheet)
    }

    @Test
    fun `turning off only-available re-requests without the flag`() = runTest(dispatcher) {
        val stock = FakeFoodStockRepository().apply {
            pageResults = listOf(page("a"), page("a", "z"))
        }
        val vm = viewModel(stock)
        advanceUntilIdle()

        vm.onAction(FoodStockAction.OnlyAvailableChanged(false))
        advanceUntilIdle()

        assertEquals(false, stock.requests.last().second.onlyAvailable)
        assertEquals(listOf("a", "z"), vm.state.value.items.map { it.foodId })
    }

    @Test
    fun `a page-1 failure produces the blocking retryable error`() = runTest(dispatcher) {
        val stock = FakeFoodStockRepository().apply {
            pageResults = listOf(Result.failure(FoodStockFailure(FoodStockError.Network, null)))
        }
        val vm = viewModel(stock)

        advanceUntilIdle()

        assertEquals(FoodStockScreenError.Retryable, vm.state.value.error)
        assertTrue(vm.state.value.showsBlockingError)
        assertEquals(false, vm.state.value.canLoadMore)
        assertEquals(false, vm.state.value.isEmpty)
    }

    @Test
    fun `the picker offered by an access error has something to pick`() = runTest(dispatcher) {
        val profile = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(
                listOf(establishment("e1"), establishment("e2")),
            )
            currentEstablishmentId = "e1"
        }
        val stock = FakeFoodStockRepository().apply {
            pageResults = listOf(
                Result.failure(FoodStockFailure(FoodStockError.EstablishmentAccessDenied, null)),
            )
        }
        val vm = viewModel(stock, profile)
        advanceUntilIdle()

        // The only action the 403 surface offers.
        vm.onAction(FoodStockAction.EstablishmentPickerOpen)

        assertEquals(FoodStockScreenError.AccessDenied, vm.state.value.error)
        assertTrue(vm.state.value.showEstablishmentPicker)
        assertEquals(listOf("e1", "e2"), vm.state.value.establishmentOptions.map { it.id })
    }

    @Test
    fun `every domain error maps to its screen error`() = runTest(dispatcher) {
        val expected = mapOf(
            FoodStockError.EstablishmentAccessDenied to FoodStockScreenError.AccessDenied,
            FoodStockError.EstablishmentNotFound to FoodStockScreenError.EstablishmentNotFound,
            FoodStockError.InvalidRequest to FoodStockScreenError.Terminal,
            FoodStockError.Unauthorized to FoodStockScreenError.Terminal,
            FoodStockError.Network to FoodStockScreenError.Retryable,
        )

        val actual = expected.keys.associateWith { error ->
            val stock = FakeFoodStockRepository().apply {
                pageResults = listOf(Result.failure(FoodStockFailure(error, null)))
            }
            val vm = viewModel(stock)
            advanceUntilIdle()
            vm.state.value.error
        }

        assertEquals(expected, actual)
    }

    @Test
    fun `retry after a page-1 failure re-requests the same filters`() = runTest(dispatcher) {
        val stock = FakeFoodStockRepository().apply {
            pageResults = listOf(
                Result.failure(FoodStockFailure(FoodStockError.Network, null)),
                page("a"),
            )
        }
        val vm = viewModel(stock)
        advanceUntilIdle()

        vm.onAction(FoodStockAction.RetryLoad)
        advanceUntilIdle()

        assertNull(vm.state.value.error)
        assertEquals(listOf("a"), vm.state.value.items.map { it.foodId })
        assertEquals(listOf(1, 1), stock.requests.map { it.third })
    }

    @Test
    fun `a loadMore failure keeps the content and never sets the error`() = runTest(dispatcher) {
        val stock = FakeFoodStockRepository().apply {
            pageResults = listOf(
                page("a", hasMore = true),
                Result.failure(FoodStockFailure(FoodStockError.Network, null)),
            )
        }
        val vm = viewModel(stock)
        advanceUntilIdle()

        vm.onAction(FoodStockAction.LoadMore)
        advanceUntilIdle()

        assertNull(vm.state.value.error)
        assertEquals(listOf("a"), vm.state.value.items.map { it.foodId })
        assertEquals(true, vm.state.value.hasMore)
        assertEquals(false, vm.state.value.isLoadingMore)
    }

    @Test
    fun `an empty page that finished loading reports isEmpty, not an error`() = runTest(dispatcher) {
        val vm = viewModel(FakeFoodStockRepository())

        advanceUntilIdle()

        assertTrue(vm.state.value.isEmpty)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `no stored establishment opens the picker instead of calling the stock endpoint`() = runTest(dispatcher) {
        val stock = FakeFoodStockRepository()
        val profile = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(listOf(establishment("e1")))
            currentEstablishmentId = null
        }
        val vm = viewModel(stock, profile = profile)

        advanceUntilIdle()

        assertTrue(vm.state.value.needsEstablishment)
        assertTrue(vm.state.value.showEstablishmentPicker)
        assertEquals(emptyList(), stock.requests)
    }

    @Test
    fun `a user with no approved establishment gets the surface, not an auto-opened empty sheet`() = runTest(dispatcher) {
        val stock = FakeFoodStockRepository()
        val profile = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(emptyList())
            currentEstablishmentId = null
        }
        val vm = viewModel(stock, profile = profile)

        advanceUntilIdle()

        assertTrue(vm.state.value.needsEstablishment)
        assertEquals(false, vm.state.value.showEstablishmentPicker)
        assertEquals(emptyList(), stock.requests)
    }

    @Test
    fun `a failed establishment list is a retryable error, NOT the picker`() = runTest(dispatcher) {
        val stock = FakeFoodStockRepository()
        val profile = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.failure(IllegalStateException("offline"))
        }
        val vm = viewModel(stock, profile = profile)

        advanceUntilIdle()

        assertEquals(FoodStockScreenError.EstablishmentsUnavailable, vm.state.value.error)
        assertEquals(false, vm.state.value.showEstablishmentPicker)
        assertEquals(emptyList(), stock.requests)
    }

    @Test
    fun `picking an establishment persists it and loads page 1`() = runTest(dispatcher) {
        val stock = FakeFoodStockRepository().apply { pageResults = listOf(page("a")) }
        val profile = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(listOf(establishment("e7")))
            currentEstablishmentId = null
        }
        val vm = viewModel(stock, profile = profile)
        advanceUntilIdle()

        vm.onAction(FoodStockAction.EstablishmentSelected(establishment("e7")))
        advanceUntilIdle()

        assertEquals("e7", profile.currentEstablishmentId)
        assertEquals("e7", stock.requests.single().first)
        assertEquals(false, vm.state.value.showEstablishmentPicker)
        assertEquals(false, vm.state.value.needsEstablishment)
    }

    @Test
    fun `the first onResume is a no-op — init already resolved`() = runTest(dispatcher) {
        val stock = FakeFoodStockRepository().apply { pageResults = listOf(page("a")) }
        val vm = viewModel(stock)
        advanceUntilIdle()

        vm.onResume()
        advanceUntilIdle()

        assertEquals(1, stock.requests.size)
    }

    @Test
    fun `a later onResume re-reads and only re-fetches when the establishment changed`() = runTest(dispatcher) {
        val stock = FakeFoodStockRepository().apply { pageResults = listOf(page("a")) }
        val profile = FakeProfileRepository().apply {
            myEstablishmentsResult =
                Result.success(listOf(establishment("e1"), establishment("e2")))
            currentEstablishmentId = "e1"
        }
        val vm = viewModel(stock, profile = profile)
        advanceUntilIdle()

        vm.onResume() // consumes the first-resume guard
        vm.onResume() // same id -> no new request
        advanceUntilIdle()
        assertEquals(1, stock.requests.size)

        profile.currentEstablishmentId = "e2" // as if Home's picker had run
        vm.onResume()
        advanceUntilIdle()

        assertEquals(2, stock.requests.size)
        assertEquals("e2", stock.requests.last().first)
        assertEquals("e2", vm.state.value.establishment?.id)
    }

    @Test
    fun `a failed category call disables the filter but never blocks the list`() = runTest(dispatcher) {
        val stock = FakeFoodStockRepository().apply { pageResults = listOf(page("a")) }
        val catalog = FakeFoodCatalogRepository().apply {
            categoriesResult = Result.failure(IllegalStateException("offline"))
        }
        val vm = viewModel(stock, catalog = catalog)

        advanceUntilIdle()

        assertEquals(emptyList(), vm.state.value.categories)
        assertEquals(listOf("a"), vm.state.value.items.map { it.foodId })
        assertNull(vm.state.value.error)
    }

    @Test
    fun `loaded categories resolve the selected category's name for the filter field`() = runTest(dispatcher) {
        val stock = FakeFoodStockRepository().apply { pageResults = listOf(page("a"), page("a")) }
        val catalog = FakeFoodCatalogRepository().apply {
            categoriesResult = Result.success(
                listOf(FoodCategory("c-9", "Cereales"), FoodCategory("c-1", "Lácteos")),
            )
        }
        val vm = viewModel(stock, catalog = catalog)
        advanceUntilIdle()

        vm.onAction(FoodStockAction.CategorySelected("c-9"))
        advanceUntilIdle()

        assertEquals(2, vm.state.value.categories.size)
        assertEquals("Cereales", vm.state.value.selectedCategoryName)
        assertEquals(1, catalog.categoriesCallCount)
    }
}
