package ar.com.ingenieriaalimentacion.presentation.feature.profile.notify_establishment

import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.data.profile.EstablishmentAbsenceFailure
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentAbsenceError
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentAbsenceNotice
import ar.com.ingenieriaalimentacion.domain.usecase.profile.NotifyEstablishmentAbsenceUseCase
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationTextField
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationMultiField
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationFormAction
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationCopy
import ar.com.ingenieriaalimentacion.domain.model.establishment.Meal
import ar.com.ingenieriaalimentacion.domain.model.establishment.EstablishmentCharacterization
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
    fun `submit with empty name and address blocks and force-shows both required errors`() = runTest(dispatcher) {
        val repo = FakeProfileRepository()
        val vm = viewModel(repo)

        vm.onAction(NotifyEstablishmentAction.NextClick)
        advanceUntilIdle()

        assertEquals(0, repo.notifyAbsenceCount)
        assertEquals(NotifyEstablishmentStep.Basics, vm.state.value.step)
        assertNull(vm.state.value.destination)
        assertEquals("Ingresá el nombre del comedor.", vm.state.value.name.displayError)
        assertEquals("Ingresá la dirección.", vm.state.value.address.displayError)
        // Optional fields stay quiet.
        assertNull(vm.state.value.city.displayError)
        assertNull(vm.state.value.province.displayError)
    }

    @Test
    fun `submit with name but no address blocks with only the address error`() = runTest(dispatcher) {
        val repo = FakeProfileRepository()
        val vm = viewModel(repo)

        vm.onAction(NotifyEstablishmentAction.NameChanged("Comedor Norte"))
        vm.onAction(NotifyEstablishmentAction.NextClick)
        advanceUntilIdle()

        assertEquals(0, repo.notifyAbsenceCount)
        assertEquals(NotifyEstablishmentStep.Basics, vm.state.value.step)
        assertNull(vm.state.value.name.displayError)
        assertEquals("Ingresá la dirección.", vm.state.value.address.displayError)
    }

    @Test
    fun `submit trims values and turns blank optional fields into null`() = runTest(dispatcher) {
        val repo = FakeProfileRepository()
        val vm = viewModel(repo)

        vm.onAction(NotifyEstablishmentAction.NameChanged("  Comedor Norte  "))
        vm.onAction(NotifyEstablishmentAction.AddressChanged(" Calle 123 "))
        vm.onAction(NotifyEstablishmentAction.CityChanged("   "))
        vm.onAction(NotifyEstablishmentAction.ProvinceChanged("Buenos Aires"))
        vm.onAction(NotifyEstablishmentAction.NextClick)
        vm.onAction(NotifyEstablishmentAction.SubmitClick)
        advanceUntilIdle()

        assertEquals(
            EstablishmentAbsenceNotice(
                name = "Comedor Norte",
                formattedAddress = "Calle 123",
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
        vm.onAction(NotifyEstablishmentAction.NameChanged("Comedor Norte"))
        vm.onAction(NotifyEstablishmentAction.AddressChanged("Calle 123"))
        vm.onAction(NotifyEstablishmentAction.NextClick)

        vm.state.test {
            assertFalse(awaitItem().isSubmitting) // initial (form already filled)

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
        vm.onAction(NotifyEstablishmentAction.NextClick)
        advanceUntilIdle()

        assertEquals(0, repo.notifyAbsenceCount)
        assertEquals(NotifyEstablishmentStep.Basics, vm.state.value.step)
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
        vm.onAction(NotifyEstablishmentAction.AddressChanged("Calle 123"))
        vm.onAction(NotifyEstablishmentAction.NextClick)
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

        vm.onAction(NotifyEstablishmentAction.NameChanged("Comedor Norte"))
        vm.onAction(NotifyEstablishmentAction.AddressChanged("Calle 123"))
        vm.onAction(NotifyEstablishmentAction.NextClick)
        vm.onAction(NotifyEstablishmentAction.SubmitClick)
        advanceUntilIdle()

        assertTrue(vm.state.value.submitError is EstablishmentAbsenceError.Unknown)
    }

    @Test
    fun `retry after failure clears the previous error`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply {
            notifyAbsenceResult = Result.failure(
                EstablishmentAbsenceFailure(EstablishmentAbsenceError.Validation(), null),
            )
        }
        val vm = viewModel(repo)

        vm.onAction(NotifyEstablishmentAction.NameChanged("Comedor Norte"))
        vm.onAction(NotifyEstablishmentAction.AddressChanged("Calle 123"))
        vm.onAction(NotifyEstablishmentAction.NextClick)
        vm.onAction(NotifyEstablishmentAction.SubmitClick)
        advanceUntilIdle()
        assertNotNull(vm.state.value.submitError)

        repo.notifyAbsenceResult = Result.success(Unit)
        vm.onAction(NotifyEstablishmentAction.SubmitClick)
        advanceUntilIdle()

        assertNull(vm.state.value.submitError)
        assertEquals(NotifyEstablishmentDestination.Submitted, vm.state.value.destination)
    }

    private fun filledViewModel(repo: FakeProfileRepository) = viewModel(repo).apply {
        onAction(NotifyEstablishmentAction.NameChanged("Comedor Norte"))
        onAction(NotifyEstablishmentAction.AddressChanged("Calle 123"))
        onAction(NotifyEstablishmentAction.NextClick)
    }

    private fun NotifyEstablishmentViewModel.ficha(action: CharacterizationFormAction) =
        onAction(NotifyEstablishmentAction.Characterization(action))

    @Test
    fun `next with valid basics moves to the ficha step without sending anything`() = runTest(dispatcher) {
        val repo = FakeProfileRepository()
        val vm = filledViewModel(repo)
        advanceUntilIdle()

        assertEquals(NotifyEstablishmentStep.Characterization, vm.state.value.step)
        assertEquals(0, repo.notifyAbsenceCount)
    }

    @Test
    fun `submit sends the ficha with the request`() = runTest(dispatcher) {
        val repo = FakeProfileRepository()
        val vm = filledViewModel(repo)

        vm.ficha(CharacterizationFormAction.OptionToggled(CharacterizationMultiField.Meals, "lunch"))
        vm.ficha(CharacterizationFormAction.TextChanged(CharacterizationTextField.District, " Moreno "))
        vm.onAction(NotifyEstablishmentAction.SubmitClick)
        advanceUntilIdle()

        assertEquals(
            EstablishmentCharacterization(district = "Moreno", meals = setOf(Meal.Lunch)),
            repo.lastAbsenceNotice?.characterization,
        )
    }

    @Test
    fun `answer later sends the request without the ficha, even an invalid one`() = runTest(dispatcher) {
        val repo = FakeProfileRepository()
        val vm = filledViewModel(repo)

        vm.ficha(CharacterizationFormAction.TextChanged(CharacterizationTextField.ContactEmail, "no-es-mail"))
        vm.onAction(NotifyEstablishmentAction.AnswerLaterClick)
        advanceUntilIdle()

        assertEquals(1, repo.notifyAbsenceCount)
        assertNull(repo.lastAbsenceNotice?.characterization)
        assertEquals(NotifyEstablishmentDestination.Submitted, vm.state.value.destination)
    }

    @Test
    fun `an untouched ficha is sent as no ficha`() = runTest(dispatcher) {
        val repo = FakeProfileRepository()
        val vm = filledViewModel(repo)

        vm.onAction(NotifyEstablishmentAction.SubmitClick)
        advanceUntilIdle()

        assertNull(repo.lastAbsenceNotice?.characterization)
    }

    @Test
    fun `an invalid ficha blocks submit and flags it inline`() = runTest(dispatcher) {
        val repo = FakeProfileRepository()
        val vm = filledViewModel(repo)

        vm.ficha(CharacterizationFormAction.TextChanged(CharacterizationTextField.ContactEmail, "no-es-mail"))
        vm.onAction(NotifyEstablishmentAction.SubmitClick)
        advanceUntilIdle()

        assertEquals(0, repo.notifyAbsenceCount)
        assertTrue(vm.state.value.hasInvalidCharacterization)
        assertEquals(
            CharacterizationCopy.INVALID_EMAIL,
            vm.state.value.characterization.displayError(CharacterizationTextField.ContactEmail),
        )
    }

    @Test
    fun `back on the ficha step returns to the basics and keeps everything`() = runTest(dispatcher) {
        val vm = filledViewModel(FakeProfileRepository())
        vm.ficha(CharacterizationFormAction.TextChanged(CharacterizationTextField.Neighborhood, "Centro"))

        vm.onAction(NotifyEstablishmentAction.BackClick)

        val state = vm.state.value
        assertEquals(NotifyEstablishmentStep.Basics, state.step)
        assertNull(state.destination)
        assertEquals("Comedor Norte", state.name.value)
        assertEquals("Centro", state.characterization.text(CharacterizationTextField.Neighborhood).value)
    }

    @Test
    fun `a 422 on a ficha field flags it and stays on the ficha step`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply {
            notifyAbsenceResult = Result.failure(
                EstablishmentAbsenceFailure(EstablishmentAbsenceError.Validation("characterization.district"), null),
            )
        }
        val vm = filledViewModel(repo)
        vm.ficha(CharacterizationFormAction.TextChanged(CharacterizationTextField.District, "Moreno"))

        vm.onAction(NotifyEstablishmentAction.SubmitClick)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(NotifyEstablishmentStep.Characterization, state.step)
        assertEquals("Moreno", state.characterization.text(CharacterizationTextField.District).value)
        assertEquals(
            CharacterizationCopy.SERVER_INVALID,
            state.characterization.displayError(CharacterizationTextField.District),
        )
    }

    @Test
    fun `a 422 on the address goes back to the basics with the address flagged`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply {
            notifyAbsenceResult = Result.failure(
                EstablishmentAbsenceFailure(EstablishmentAbsenceError.Validation("formatted_address"), null),
            )
        }
        val vm = filledViewModel(repo)

        vm.onAction(NotifyEstablishmentAction.AnswerLaterClick)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(NotifyEstablishmentStep.Basics, state.step)
        assertEquals("Calle 123", state.address.value)
        assertNotNull(state.address.displayError)
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
