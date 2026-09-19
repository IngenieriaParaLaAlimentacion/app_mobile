package ar.com.ingenieriaalimentacion.presentation.feature.food.establishment_food_movement_history

import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementDirection
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementError
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementFailure
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementPage
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementSummary
import ar.com.ingenieriaalimentacion.domain.model.food.Food
import ar.com.ingenieriaalimentacion.domain.model.food.FoodSearchResult
import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import ar.com.ingenieriaalimentacion.domain.model.food.ResponsibleRef
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetEstablishmentFoodMovementPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.SearchFoodsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.ResolveCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.SetCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.FakeEstablishmentFoodMovementRepository
import ar.com.ingenieriaalimentacion.fake.FakeFoodCatalogRepository
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EstablishmentFoodMovementHistoryViewModelTest {
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

    private fun item(id: String) = EstablishmentFoodMovementSummary(
        id = id,
        direction = EstablishmentFoodMovementDirection.In,
        occurredAtMillis = 1L,
        responsible = ResponsibleRef("u1", "Ana", "Pérez"),
        foodCount = 1,
    )

    private fun page(vararg ids: String, hasMore: Boolean = false) =
        Result.success(EstablishmentFoodMovementPage(ids.map(::item), hasMore))

    private fun profile(current: String? = "e1", ids: List<String> = listOf("e1")) =
        FakeProfileRepository().apply {
            currentEstablishmentId = current
            myEstablishmentsResult = Result.success(ids.map(::establishment))
        }

    private fun TestScope.viewModel(
        movements: FakeEstablishmentFoodMovementRepository,
        profile: FakeProfileRepository = profile(),
        catalog: FakeFoodCatalogRepository = FakeFoodCatalogRepository(),
        connectivity: FakeConnectivityObserver = FakeConnectivityObserver(true),
    ): EstablishmentFoodMovementHistoryViewModel {
        val vm = EstablishmentFoodMovementHistoryViewModel(
            resolveCurrentEstablishment = ResolveCurrentEstablishmentUseCase(profile),
            setCurrentEstablishment = SetCurrentEstablishmentUseCase(profile),
            getPage = GetEstablishmentFoodMovementPageUseCase(movements),
            searchFoods = SearchFoodsUseCase(catalog),
            connectivity = connectivity,
        )
        backgroundScope.launch { vm.state.collect {} }
        return vm
    }

    @Test
    fun `initial request resolves current establishment and uses page defaults`() = runTest(dispatcher) {
        val repository = FakeEstablishmentFoodMovementRepository().apply {
            pageResults = listOf(page("m1"))
        }
        val vm = viewModel(repository)
        advanceUntilIdle()

        val request = repository.pageRequests.single()
        assertEquals("e1", request.establishmentId)
        assertEquals(1, request.page)
        assertEquals(20, request.pageSize)
        assertNull(request.filters.direction)
        assertNull(request.filters.occurredFromIso)
        assertNull(request.filters.occurredToIso)
        assertNull(request.filters.foodId)
        assertEquals(listOf("m1"), vm.state.value.items.map { it.id })
    }

    @Test
    fun `page two appends duplicates and additional failure keeps content loadable`() = runTest(dispatcher) {
        val repository = FakeEstablishmentFoodMovementRepository().apply {
            pageResults = listOf(
                page("m1", hasMore = true),
                page("m1", "m2", hasMore = true),
                Result.failure(EstablishmentFoodMovementFailure(EstablishmentFoodMovementError.Network)),
            )
        }
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onAction(EstablishmentFoodMovementHistoryAction.LoadMore)
        advanceUntilIdle()
        assertEquals(listOf("m1", "m1", "m2"), vm.state.value.items.map { it.id })
        vm.onAction(EstablishmentFoodMovementHistoryAction.LoadMore)
        advanceUntilIdle()
        assertEquals(listOf("m1", "m1", "m2"), vm.state.value.items.map { it.id })
        assertTrue(vm.state.value.canLoadMore)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `none and stale selection open picker and selection persists then loads`() = runTest(dispatcher) {
        val profile = profile(current = null, ids = listOf("e1", "e2"))
        val repository = FakeEstablishmentFoodMovementRepository().apply {
            pageResults = listOf(page("m1"))
        }
        val vm = viewModel(repository, profile)
        advanceUntilIdle()
        assertTrue(vm.state.value.needsEstablishment)
        assertTrue(vm.state.value.showEstablishmentPicker)

        vm.onAction(EstablishmentFoodMovementHistoryAction.EstablishmentSelected(establishment("e2")))
        advanceUntilIdle()
        assertEquals("e2", profile.currentEstablishmentId)
        assertEquals("e2", repository.pageRequests.single().establishmentId)

        val staleVm = viewModel(
            FakeEstablishmentFoodMovementRepository(),
            profile(current = "gone", ids = listOf("e1")),
        )
        advanceUntilIdle()
        assertTrue(staleVm.state.value.staleSelection)
        assertTrue(staleVm.state.value.showEstablishmentPicker)
    }

    @Test
    fun `profile load failure is an unavailable error and not empty establishments`() = runTest(dispatcher) {
        val failed = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.failure(RuntimeException("offline"))
        }
        val vm = viewModel(FakeEstablishmentFoodMovementRepository(), failed)
        advanceUntilIdle()
        assertEquals(
            EstablishmentFoodMovementHistoryError.EstablishmentsUnavailable,
            vm.state.value.error,
        )
        assertFalse(vm.state.value.needsEstablishment)
    }

    @Test
    fun `first resume is consumed unchanged id does not reload and changed id replaces rows`() = runTest(dispatcher) {
        val profile = profile(current = "e1", ids = listOf("e1", "e2"))
        val repository = FakeEstablishmentFoodMovementRepository().apply {
            pageResults = listOf(page("old"), page("new"))
        }
        val vm = viewModel(repository, profile)
        advanceUntilIdle()
        vm.onResume()
        advanceUntilIdle()
        assertEquals(1, repository.pageRequests.size)
        vm.onResume()
        advanceUntilIdle()
        assertEquals(1, repository.pageRequests.size)
        profile.currentEstablishmentId = "e2"
        vm.onResume()
        advanceUntilIdle()
        assertEquals(listOf("new"), vm.state.value.items.map { it.id })
        assertEquals("e2", repository.pageRequests.last().establishmentId)
    }

    @Test
    fun `filter draft waits for apply and valid bounds are local inclusive instants`() = runTest(dispatcher) {
        val repository = FakeEstablishmentFoodMovementRepository().apply {
            pageResults = listOf(page("initial"), page("filtered"), page("cleared"))
        }
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onAction(
            EstablishmentFoodMovementHistoryAction.DirectionChanged(
                EstablishmentFoodMovementDirection.Out,
            ),
        )
        vm.onAction(EstablishmentFoodMovementHistoryAction.FromChanged("19092026"))
        vm.onAction(EstablishmentFoodMovementHistoryAction.ToChanged("20092026"))
        assertEquals(1, repository.pageRequests.size)
        vm.onAction(EstablishmentFoodMovementHistoryAction.FilterApply)
        advanceUntilIdle()

        val zone = TimeZone.currentSystemDefault()
        val filters = repository.pageRequests.last().filters
        assertEquals(EstablishmentFoodMovementDirection.Out, filters.direction)
        assertEquals(LocalDate(2026, 9, 19).atStartOfDayIn(zone).toString(), filters.occurredFromIso)
        assertEquals(
            LocalDateTime(LocalDate(2026, 9, 20), LocalTime(23, 59, 59, 999_999_000))
                .toInstant(zone).toString(),
            filters.occurredToIso,
        )
        vm.onAction(EstablishmentFoodMovementHistoryAction.FilterClear)
        advanceUntilIdle()
        assertEquals(3, repository.pageRequests.size)
        assertFalse(vm.state.value.hasActiveFilters)
    }

    @Test
    fun `invalid and inverted dates do not request and display errors`() = runTest(dispatcher) {
        val repository = FakeEstablishmentFoodMovementRepository().apply {
            pageResults = listOf(page("initial"))
        }
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onAction(EstablishmentFoodMovementHistoryAction.FromChanged("99999999"))
        vm.onAction(EstablishmentFoodMovementHistoryAction.FilterApply)
        assertEquals(1, repository.pageRequests.size)
        assertTrue(vm.state.value.filterDraft.from.displayError != null)

        vm.onAction(EstablishmentFoodMovementHistoryAction.FromChanged("21092026"))
        vm.onAction(EstablishmentFoodMovementHistoryAction.ToChanged("20092026"))
        vm.onAction(EstablishmentFoodMovementHistoryAction.FilterApply)
        assertEquals(1, repository.pageRequests.size)
        assertTrue(vm.state.value.filterDraft.from.displayError != null)
    }

    @Test
    fun `food search debounces cancels stale query and requires selected suggestion`() = runTest(dispatcher) {
        val food = Food("f1", "A", "Arroz", FoodUnit.Kilogramos, "Cereales")
        val catalog = FakeFoodCatalogRepository().apply {
            result = Result.success(FoodSearchResult(listOf(food), 1))
        }
        val repository = FakeEstablishmentFoodMovementRepository().apply {
            pageResults = listOf(page("initial"), page("filtered"))
        }
        val vm = viewModel(repository, catalog = catalog)
        advanceUntilIdle()
        vm.onAction(EstablishmentFoodMovementHistoryAction.FoodQueryChanged("a"))
        advanceTimeBy(100)
        vm.onAction(EstablishmentFoodMovementHistoryAction.FoodQueryChanged("arroz"))
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf<String?>("arroz"), catalog.searchCalls)
        vm.onAction(EstablishmentFoodMovementHistoryAction.FilterApply)
        assertEquals("Elegí un alimento de la lista.", vm.state.value.filterDraft.foodError)
        assertEquals(1, repository.pageRequests.size)
        vm.onAction(EstablishmentFoodMovementHistoryAction.FoodSelected(food))
        vm.onAction(EstablishmentFoodMovementHistoryAction.FilterApply)
        advanceUntilIdle()
        assertEquals("f1", repository.pageRequests.last().filters.foodId)
        vm.onAction(EstablishmentFoodMovementHistoryAction.FoodQueryChanged("Arroz x"))
        assertNull(vm.state.value.filterDraft.selectedFood)
    }

    @Test
    fun `errors empty and row destination map as screen contract`() = runTest(dispatcher) {
        val accessRepository = FakeEstablishmentFoodMovementRepository().apply {
            pageResults = listOf(
                Result.failure(
                    EstablishmentFoodMovementFailure(
                        EstablishmentFoodMovementError.EstablishmentAccessDenied,
                    ),
                ),
            )
        }
        val accessVm = viewModel(accessRepository, profile(ids = listOf("e1", "e2")))
        advanceUntilIdle()
        assertEquals(EstablishmentFoodMovementHistoryError.AccessDenied, accessVm.state.value.error)
        assertEquals(2, accessVm.state.value.establishmentOptions.size)

        val emptyRepository = FakeEstablishmentFoodMovementRepository().apply {
            pageResults = listOf(page())
        }
        val emptyVm = viewModel(emptyRepository)
        advanceUntilIdle()
        assertTrue(emptyVm.state.value.isEmpty)

        val rowRepository = FakeEstablishmentFoodMovementRepository().apply {
            pageResults = listOf(page("m1"))
        }
        val rowVm = viewModel(rowRepository)
        advanceUntilIdle()
        rowVm.onAction(EstablishmentFoodMovementHistoryAction.MovementClick("m1"))
        assertEquals(
            EstablishmentFoodMovementHistoryDestination.Detail("e1", "m1"),
            rowVm.state.value.destination,
        )
        rowVm.clearDestination()
        assertNull(rowVm.state.value.destination)
    }

    @Test
    fun `network offline and terminal domain errors map as blocking screen errors`() =
        runTest(dispatcher) {
            val expected = mapOf(
                EstablishmentFoodMovementError.Network to
                    EstablishmentFoodMovementHistoryError.Retryable,
                EstablishmentFoodMovementError.EstablishmentNotFound to
                    EstablishmentFoodMovementHistoryError.EstablishmentNotFound,
                EstablishmentFoodMovementError.InvalidRequest to
                    EstablishmentFoodMovementHistoryError.Terminal,
                EstablishmentFoodMovementError.Unauthorized to
                    EstablishmentFoodMovementHistoryError.Terminal,
            )
            for ((domainError, screenError) in expected) {
                val repository = FakeEstablishmentFoodMovementRepository().apply {
                    pageResults = listOf(
                        Result.failure(EstablishmentFoodMovementFailure(domainError)),
                    )
                }
                val vm = viewModel(repository, connectivity = FakeConnectivityObserver(false))
                advanceUntilIdle()
                assertEquals(screenError, vm.state.value.error)
                assertTrue(vm.state.value.showsBlockingError)
                assertFalse(vm.state.value.isOnline)
            }
        }
}
