package ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.di.AppScope
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary
import ar.com.ingenieriaalimentacion.domain.usecase.subject.CreateSubjectUseCase
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.FakeSubjectRepository
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import kotlinx.serialization.json.Json
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
class CreateSubjectViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repo: FakeSubjectRepository,
        route: Route.CreateSubject = Route.CreateSubject(),
    ): CreateSubjectViewModel {
        val coordinator = SyncCoordinator(listOf(repo), FakeConnectivityObserver(), AppScope())
        return CreateSubjectViewModel(route, CreateSubjectUseCase(repo), coordinator)
    }

    private fun CreateSubjectViewModel.fillValidForm() {
        onAction(CreateSubjectAction.DniChanged("12345678"))
        onAction(CreateSubjectAction.SexChanged(Sex.Male))
        onAction(CreateSubjectAction.NameChanged("Juan"))
        onAction(CreateSubjectAction.LastNameChanged("Pérez"))
        onAction(CreateSubjectAction.BirthDateChanged("01011990"))
    }

    private suspend fun ReceiveTurbine<CreateSubjectState>.awaitState(
        predicate: (CreateSubjectState) -> Boolean,
    ): CreateSubjectState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun `enqueue success clears the form and sets confirmation`() = runTest(dispatcher) {
        val repo = FakeSubjectRepository()
        val vm = viewModel(repo)

        vm.fillValidForm()

        vm.state.test {
            vm.onAction(CreateSubjectAction.Submit)
            val state = awaitState { it.successMessage != null }
            assertEquals("Sujeto guardado", state.successMessage)
            assertEquals("", state.dni.value)
            assertEquals("", state.name.value)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, repo.enqueued.size)
        assertEquals(12345678, repo.enqueued.first().dni)
    }

    @Test
    fun `submit on empty form blocks enqueue and forces field errors to show`() = runTest(dispatcher) {
        val repo = FakeSubjectRepository()
        val vm = viewModel(repo)

        vm.state.test {
            awaitItem() // initial
            vm.onAction(CreateSubjectAction.Submit)
            val state = awaitState { it.dni.displayError != null }
            assertEquals("Ingresá el DNI.", state.dni.displayError)
            assertEquals("Seleccioná el sexo.", state.sexError)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(repo.enqueued.isEmpty())
    }

    @Test
    fun `invalid dni stays hidden while typing then surfaces on blur`() = runTest(dispatcher) {
        val repo = FakeSubjectRepository()
        val vm = viewModel(repo)

        vm.state.test {
            awaitItem() // initial
            vm.onAction(CreateSubjectAction.DniFocusChanged(true))
            vm.onAction(CreateSubjectAction.DniChanged("0"))
            // While focused/typing the error is computed but not displayed.
            val typing = awaitState { it.dni.value == "0" }
            assertTrue(typing.dni.error != null)
            assertEquals(null, typing.dni.displayError)

            // Leaving the field reveals it.
            vm.onAction(CreateSubjectAction.DniFocusChanged(false))
            val blurred = awaitState { it.dni.displayError != null }
            assertEquals("Ingresá un DNI válido (1 a 8 dígitos).", blurred.dni.displayError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `prefilled dni seeds the form when opened from the picker`() = runTest(dispatcher) {
        val repo = FakeSubjectRepository()
        val vm = viewModel(repo, Route.CreateSubject(prefillDni = "12345678", returnsResult = true))

        vm.state.test {
            assertEquals("12345678", awaitItem().dni.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `returnsResult hands the new subject back instead of showing the banner`() = runTest(dispatcher) {
        val repo = FakeSubjectRepository()
        repo.enqueueResult = Result.success("new-local-id")
        val vm = viewModel(repo, Route.CreateSubject(prefillDni = "12345678", returnsResult = true))

        vm.fillValidForm()

        vm.state.test {
            vm.onAction(CreateSubjectAction.Submit)
            val state = awaitState { it.destination != null }
            val destination = state.destination
            assertTrue(destination is CreateSubjectDestination.Created)
            // The measurement form reads this back as a SubjectSummary; the id must be the localId
            // the queue just generated, which is what the measurement will reference as its FK.
            val summary = Json.decodeFromString<SubjectSummary>(destination.subjectJson)
            assertEquals("new-local-id", summary.id)
            assertEquals(12345678, summary.dni)
            assertEquals("Juan", summary.name)
            assertEquals("Pérez", summary.lastName)
            assertEquals(Sex.Male, summary.sex)
            // Stays on the form-confirmation path only when nobody is waiting for the subject.
            assertEquals(null, state.successMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `blurring an empty field does not show a required error`() = runTest(dispatcher) {
        val repo = FakeSubjectRepository()
        val vm = viewModel(repo)

        vm.state.test {
            awaitItem() // initial
            vm.onAction(CreateSubjectAction.NameFocusChanged(true))
            vm.onAction(CreateSubjectAction.NameFocusChanged(false))
            val state = awaitState { it.name.hasBlurred }
            assertEquals(null, state.name.displayError)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
