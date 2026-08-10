package ar.com.ingenieriaalimentacion.presentation.feature.profile.notify_establishment

import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.data.profile.EstablishmentAbsenceFailure
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentAbsenceError
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentAbsenceNotice
import ar.com.ingenieriaalimentacion.domain.usecase.profile.NotifyEstablishmentAbsenceUseCase
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotifyEstablishmentViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repo: FakeProfileRepository) =
        NotifyEstablishmentViewModel(notifyAbsence = NotifyEstablishmentAbsenceUseCase(repo))

    @Test
    fun `submit with every field empty sends an all-null notice and navigates back`() = runTest(dispatcher) {
        val repo = FakeProfileRepository()
        val vm = viewModel(repo)

        vm.onAction(NotifyEstablishmentAction.SubmitClick)
        advanceUntilIdle()

        assertEquals(1, repo.notifyAbsenceCount)
        assertEquals(EstablishmentAbsenceNotice(), repo.lastAbsenceNotice)
        assertEquals(NotifyEstablishmentDestination.Submitted, vm.state.value.destination)
    }

    @Test
    fun `submit trims values and turns blank fields into null`() = runTest(dispatcher) {
        val repo = FakeProfileRepository()
        val vm = viewModel(repo)

        vm.onAction(NotifyEstablishmentAction.NameChanged("  Comedor Norte  "))
        vm.onAction(NotifyEstablishmentAction.CityChanged("   "))
        vm.onAction(NotifyEstablishmentAction.ProvinceChanged("Buenos Aires"))
        vm.onAction(NotifyEstablishmentAction.SubmitClick)
        advanceUntilIdle()

        assertEquals(
            EstablishmentAbsenceNotice(
                name = "Comedor Norte",
                formattedAddress = null,
                city = null,
                province = "Buenos Aires",
            ),
            repo.lastAbsenceNotice,
        )
    }

    @Test
    fun `submit shows loading until success keeps it until navigation`() = runTest(dispatcher) {
        val repo = FakeProfileRepository()
        val gate = CompletableDeferred<Result<Unit>>()
        repo.notifyAbsenceResponder = { gate.await() }
        val vm = viewModel(repo)

        vm.state.test {
            assertFalse(awaitItem().isSubmitting) // initial

            vm.onAction(NotifyEstablishmentAction.SubmitClick)
            // Skip the re-validated (forceShow) emission that precedes the loading flip.
            var loading = awaitItem()
            while (!loading.isSubmitting) loading = awaitItem()
            assertNull(loading.destination)

            gate.complete(Result.success(Unit))
            val submitted = awaitItem()
            // Stays submitting so the button doesn't flash back before the pop.
            assertTrue(submitted.isSubmitting)
            assertEquals(NotifyEstablishmentDestination.Submitted, submitted.destination)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `field over 100 chars blocks submit and shows the error`() = runTest(dispatcher) {
        val repo = FakeProfileRepository()
        val vm = viewModel(repo)

        vm.onAction(NotifyEstablishmentAction.NameChanged("a".repeat(101)))
        vm.onAction(NotifyEstablishmentAction.SubmitClick)
        advanceUntilIdle()

        assertEquals(0, repo.notifyAbsenceCount)
        assertNull(vm.state.value.destination)
        assertEquals("Máximo 100 caracteres.", vm.state.value.name.displayError)
    }

    @Test
    fun `typed failure surfaces the mapped error and stops loading`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply {
            notifyAbsenceResult = Result.failure(
                EstablishmentAbsenceFailure(EstablishmentAbsenceError.Network, null),
            )
        }
        val vm = viewModel(repo)

        vm.onAction(NotifyEstablishmentAction.NameChanged("Comedor Sur"))
        vm.onAction(NotifyEstablishmentAction.SubmitClick)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(EstablishmentAbsenceError.Network, state.submitError)
        assertFalse(state.isSubmitting)
        assertNull(state.destination)
        // The form keeps what the user already completed.
        assertEquals("Comedor Sur", state.name.value)
    }

    @Test
    fun `untyped failure maps to Unknown`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply {
            notifyAbsenceResult = Result.failure(IllegalStateException("boom"))
        }
        val vm = viewModel(repo)

        vm.onAction(NotifyEstablishmentAction.SubmitClick)
        advanceUntilIdle()

        assertTrue(vm.state.value.submitError is EstablishmentAbsenceError.Unknown)
    }

    @Test
    fun `retry after failure clears the previous error`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply {
            notifyAbsenceResult = Result.failure(
                EstablishmentAbsenceFailure(EstablishmentAbsenceError.Validation, null),
            )
        }
        val vm = viewModel(repo)

        vm.onAction(NotifyEstablishmentAction.SubmitClick)
        advanceUntilIdle()
        assertNotNull(vm.state.value.submitError)

        repo.notifyAbsenceResult = Result.success(Unit)
        vm.onAction(NotifyEstablishmentAction.SubmitClick)
        advanceUntilIdle()

        assertNull(vm.state.value.submitError)
        assertEquals(NotifyEstablishmentDestination.Submitted, vm.state.value.destination)
    }

    @Test
    fun `back click emits Back destination`() = runTest(dispatcher) {
        val vm = viewModel(FakeProfileRepository())

        vm.onAction(NotifyEstablishmentAction.BackClick)

        assertEquals(NotifyEstablishmentDestination.Back, vm.state.value.destination)
        vm.clearDestination()
        assertNull(vm.state.value.destination)
    }
}
