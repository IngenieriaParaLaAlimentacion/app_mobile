package ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.di.AppScope
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.usecase.subject.CreateSubjectUseCase
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.FakeSubjectRepository
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

    private fun viewModel(repo: FakeSubjectRepository): CreateSubjectViewModel {
        val coordinator = SyncCoordinator(repo, FakeConnectivityObserver(), AppScope())
        return CreateSubjectViewModel(CreateSubjectUseCase(repo), coordinator)
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
            assertEquals("Guardado. Se sincronizará automáticamente.", state.successMessage)
            assertEquals("", state.dni)
            assertEquals("", state.name)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, repo.enqueued.size)
        assertEquals(12345678, repo.enqueued.first().dni)
    }

    @Test
    fun `validation blocks enqueue`() = runTest(dispatcher) {
        val repo = FakeSubjectRepository()
        val vm = viewModel(repo)

        vm.state.test {
            awaitItem() // initial
            vm.onAction(CreateSubjectAction.Submit)
            val state = awaitState { it.fieldErrors.dni != null }
            assertTrue(state.fieldErrors.dni != null)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(repo.enqueued.isEmpty())
    }
}
