package ar.com.ingenieriaalimentacion.presentation.feature.food.establishment_food_movement_detail

import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementDetail
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementDirection
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementError
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementFailure
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementLine
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovementSource
import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import ar.com.ingenieriaalimentacion.domain.model.food.ResponsibleRef
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetEstablishmentFoodMovementDetailUseCase
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.FakeEstablishmentFoodMovementRepository
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
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
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class EstablishmentFoodMovementDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun teardown() = Dispatchers.resetMain()

    private val detail = EstablishmentFoodMovementDetail(
        id = "m1",
        direction = EstablishmentFoodMovementDirection.In,
        occurredAtMillis = 1L,
        source = FoodMovementSource.Manual,
        comments = null,
        responsible = ResponsibleRef("u1", "Ana", "Pérez"),
        items = listOf(
            EstablishmentFoodMovementLine("f1", "Arroz", FoodUnit.Kilogramos, "2.000"),
            EstablishmentFoodMovementLine("f1", "Arroz", FoodUnit.Kilogramos, "1.000"),
        ),
    )

    private fun TestScope.viewModel(
        repository: FakeEstablishmentFoodMovementRepository,
        connectivity: FakeConnectivityObserver = FakeConnectivityObserver(true),
    ): EstablishmentFoodMovementDetailViewModel {
        val vm = EstablishmentFoodMovementDetailViewModel(
            route = Route.EstablishmentFoodMovementDetail("e1", "m1"),
            getDetail = GetEstablishmentFoodMovementDetailUseCase(repository),
            connectivity = connectivity,
        )
        backgroundScope.launch { vm.state.collect {} }
        return vm
    }

    @Test
    fun `initial call uses both route ids and exposes all lines unchanged`() = runTest(dispatcher) {
        val repository = FakeEstablishmentFoodMovementRepository().apply {
            detailResults = listOf(Result.success(detail))
        }
        val vm = viewModel(repository)
        advanceUntilIdle()
        assertEquals(
            FakeEstablishmentFoodMovementRepository.DetailRequest("e1", "m1"),
            repository.detailRequests.single(),
        )
        assertEquals(listOf("f1", "f1"), vm.state.value.detail?.items?.map { it.foodId })
        assertEquals(listOf("2.000", "1.000"), vm.state.value.detail?.items?.map { it.quantity })
    }

    @Test
    fun `retry repeats the same route pair`() = runTest(dispatcher) {
        val repository = FakeEstablishmentFoodMovementRepository().apply {
            detailResults = listOf(
                Result.failure(EstablishmentFoodMovementFailure(EstablishmentFoodMovementError.Network)),
                Result.success(detail),
            )
        }
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onAction(EstablishmentFoodMovementDetailAction.RetryLoad)
        advanceUntilIdle()
        assertEquals(
            listOf("e1" to "m1", "e1" to "m1"),
            repository.detailRequests.map { it.establishmentId to it.movementId },
        )
        assertEquals(detail, vm.state.value.detail)
    }

    @Test
    fun `network failure is retryable and offline state is reflected`() = runTest(dispatcher) {
        val repository = FakeEstablishmentFoodMovementRepository().apply {
            detailResults = listOf(
                Result.failure(EstablishmentFoodMovementFailure(EstablishmentFoodMovementError.Network)),
            )
        }
        val vm = viewModel(repository, FakeConnectivityObserver(false))
        advanceUntilIdle()
        assertEquals(EstablishmentFoodMovementDetailError.Retryable, vm.state.value.error)
        assertFalse(vm.state.value.isOnline)
    }

    @Test
    fun `domain failures map to detail screen errors`() = runTest(dispatcher) {
        val expected = mapOf(
            EstablishmentFoodMovementError.EstablishmentNotFound to
                EstablishmentFoodMovementDetailError.NotFound,
            EstablishmentFoodMovementError.MovementNotFound to
                EstablishmentFoodMovementDetailError.NotFound,
            EstablishmentFoodMovementError.EstablishmentAccessDenied to
                EstablishmentFoodMovementDetailError.AccessDenied,
            EstablishmentFoodMovementError.InvalidRequest to
                EstablishmentFoodMovementDetailError.Terminal,
            EstablishmentFoodMovementError.Unauthorized to
                EstablishmentFoodMovementDetailError.Terminal,
        )
        for ((error, screenError) in expected) {
            val repository = FakeEstablishmentFoodMovementRepository().apply {
                detailResults = listOf(Result.failure(EstablishmentFoodMovementFailure(error)))
            }
            val vm = viewModel(repository)
            advanceUntilIdle()
            assertEquals(screenError, vm.state.value.error)
        }
    }

    @Test
    fun `back emits and clears destination`() = runTest(dispatcher) {
        val repository = FakeEstablishmentFoodMovementRepository().apply {
            detailResults = listOf(Result.success(detail))
        }
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onAction(EstablishmentFoodMovementDetailAction.BackClick)
        assertEquals(EstablishmentFoodMovementDetailDestination.Back, vm.state.value.destination)
        vm.clearDestination()
        assertNull(vm.state.value.destination)
    }
}
