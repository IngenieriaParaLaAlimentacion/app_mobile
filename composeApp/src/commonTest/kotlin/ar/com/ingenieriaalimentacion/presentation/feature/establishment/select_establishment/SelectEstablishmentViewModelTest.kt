package ar.com.ingenieriaalimentacion.presentation.feature.establishment.select_establishment

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment
import ar.com.ingenieriaalimentacion.domain.usecase.establishment.GetEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.fake.FakeEstablishmentRepository
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SelectEstablishmentViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val establishments = listOf(
        Establishment("1", "Comedor Norte", "Calle 1"),
        Establishment("2", "Comedor Sur", "Calle 2"),
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
        preselected: List<Establishment> = emptyList(),
        repo: FakeEstablishmentRepository = FakeEstablishmentRepository().apply {
            result = Result.success(establishments)
        },
        singleSelect: Boolean = false,
    ) = SelectEstablishmentViewModel(
        route = Route.SelectEstablishment(Json.encodeToString(preselected), singleSelect),
        getEstablishments = GetEstablishmentsUseCase(repo),
    )

    private suspend fun ReceiveTurbine<SelectEstablishmentState>.awaitState(
        predicate: (SelectEstablishmentState) -> Boolean,
    ): SelectEstablishmentState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun `seeds selection from preselected payload`() = runTest(dispatcher) {
        val vm = viewModel(preselected = listOf(establishments[0]))

        vm.state.test {
            val initial = awaitItem()
            assertEquals(listOf(establishments[0]), initial.selected)
            assertTrue(initial.isSelected("1"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `query change triggers debounced search with the term`() = runTest(dispatcher) {
        val repo = FakeEstablishmentRepository().apply { result = Result.success(establishments) }
        val vm = viewModel(repo = repo)

        advanceUntilIdle() // initial blank load settles
        vm.onAction(SelectEstablishmentAction.QueryChanged("norte"))
        advanceUntilIdle() // debounce window elapses and the search fires

        assertEquals("norte", repo.lastSearch)
        assertEquals("norte", vm.state.value.query)
    }

    @Test
    fun `toggle adds then removes retaining the object`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.state.test {
            awaitState { !it.isLoading }

            vm.onAction(SelectEstablishmentAction.EstablishmentToggled(establishments[1]))
            assertEquals(listOf(establishments[1]), awaitState { it.isSelected("2") }.selected)

            vm.onAction(SelectEstablishmentAction.EstablishmentToggled(establishments[1]))
            assertTrue(awaitState { !it.isSelected("2") }.selected.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirm emits destination with JSON of the selection`() = runTest(dispatcher) {
        val vm = viewModel(preselected = listOf(establishments[0]))

        vm.state.test {
            awaitState { !it.isLoading }

            vm.onAction(SelectEstablishmentAction.ConfirmClick)
            val state = awaitState { it.destination != null }
            val destination = state.destination
            assertTrue(destination is SelectEstablishmentDestination.Confirm)
            assertEquals(
                listOf(establishments[0]),
                Json.decodeFromString<List<Establishment>>(destination.selectedJson),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load failure sets loadError`() = runTest(dispatcher) {
        val repo = FakeEstablishmentRepository().apply { result = Result.failure(RuntimeException("boom")) }
        val vm = viewModel(repo = repo)

        vm.state.test {
            val state = awaitState { it.loadError }
            assertTrue(state.loadError)
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `single-select replaces the selection instead of accumulating and confirms on the spot`() =
        runTest(dispatcher) {
            val vm = viewModel(singleSelect = true)
            advanceUntilIdle()

            vm.state.test {
                awaitState { !it.isLoading }

                vm.onAction(SelectEstablishmentAction.EstablishmentToggled(establishments[0]))
                val first = awaitState { it.destination != null }
                assertEquals(listOf(establishments[0]), first.selected)
                assertEquals(
                    SelectEstablishmentDestination.Confirm(Json.encodeToString(listOf(establishments[0]))),
                    first.destination,
                )

                vm.clearDestination()
                vm.onAction(SelectEstablishmentAction.EstablishmentToggled(establishments[1]))
                val second = awaitState { it.destination != null }
                // Replaced, not accumulated — and re-confirmed with the new choice.
                assertEquals(listOf(establishments[1]), second.selected)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `multi-select still accumulates and waits for Listo`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.state.test {
            awaitState { !it.isLoading }

            vm.onAction(SelectEstablishmentAction.EstablishmentToggled(establishments[0]))
            vm.onAction(SelectEstablishmentAction.EstablishmentToggled(establishments[1]))

            val both = awaitState { it.selectedCount == 2 }
            assertEquals(establishments, both.selected)
            assertNull(both.destination)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
