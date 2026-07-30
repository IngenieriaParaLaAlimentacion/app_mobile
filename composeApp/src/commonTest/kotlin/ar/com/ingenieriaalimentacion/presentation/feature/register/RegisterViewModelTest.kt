package ar.com.ingenieriaalimentacion.presentation.feature.register

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.data.registration.RegistrationFailure
import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment
import ar.com.ingenieriaalimentacion.domain.model.registration.RegistrationError
import ar.com.ingenieriaalimentacion.domain.usecase.registration.RegisterFieldWorkerUseCase
import ar.com.ingenieriaalimentacion.fake.FakeRegistrationRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {

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
        registrationRepo: FakeRegistrationRepository = FakeRegistrationRepository(),
    ) = RegisterViewModel(
        registerFieldWorker = RegisterFieldWorkerUseCase(registrationRepo),
    )

    /** Awaits emissions (auto-advancing virtual time) until [predicate] holds, then returns that state. */
    private suspend fun ReceiveTurbine<RegisterState>.awaitState(
        predicate: (RegisterState) -> Boolean,
    ): RegisterState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    private fun fillValidForm(vm: RegisterViewModel) {
        vm.onAction(RegisterAction.EmailChanged("manager@example.com"))
        vm.onAction(RegisterAction.PasswordChanged("password123"))
        vm.onAction(RegisterAction.NameChanged("Ana"))
        vm.onAction(RegisterAction.LastNameChanged("García"))
        vm.onAction(RegisterAction.EstablishmentsSelected(listOf(establishments[0])))
    }

    @Test
    fun `field changes update state`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.state.test {
            awaitItem() // initial
            vm.onAction(RegisterAction.EmailChanged("a@b.com"))
            vm.onAction(RegisterAction.NameChanged("Ana"))
            val state = awaitState { it.email == "a@b.com" && it.name == "Ana" }
            assertEquals("a@b.com", state.email)
            assertEquals("Ana", state.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `EstablishmentsSelected updates selection and clears field error`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.state.test {
            awaitItem() // initial
            // Force the establishment field error via an empty-selection submit.
            vm.onAction(RegisterAction.Submit)
            awaitState { it.fieldErrors.establishments != null }

            vm.onAction(RegisterAction.EstablishmentsSelected(establishments))
            val state = awaitState { it.selectedEstablishments.size == 2 }
            assertEquals(establishments, state.selectedEstablishments)
            assertNull(state.fieldErrors.establishments)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `OpenEstablishmentSelector emits destination with current selection JSON`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.state.test {
            awaitItem() // initial
            vm.onAction(RegisterAction.EstablishmentsSelected(listOf(establishments[0])))
            awaitState { it.selectedEstablishments.isNotEmpty() }

            vm.onAction(RegisterAction.OpenEstablishmentSelector)
            val state = awaitState { it.destination != null }
            val destination = state.destination
            assertTrue(destination is RegisterDestination.SelectEstablishment)
            assertEquals(
                listOf(establishments[0]),
                Json.decodeFromString<List<Establishment>>(destination.preselectedJson),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `canSubmit is false when fields blank or no establishment selected`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.state.test {
            val initial = awaitItem()
            assertFalse(initial.canSubmit)

            vm.onAction(RegisterAction.EmailChanged("manager@example.com"))
            vm.onAction(RegisterAction.PasswordChanged("password123"))
            vm.onAction(RegisterAction.NameChanged("Ana"))
            vm.onAction(RegisterAction.LastNameChanged("García"))
            // Still no establishment selected.
            val withoutEstablishment = awaitState { it.lastName == "García" }
            assertFalse(withoutEstablishment.canSubmit)

            vm.onAction(RegisterAction.EstablishmentsSelected(listOf(establishments[0])))
            val ready = awaitState { it.selectedEstablishments.isNotEmpty() }
            assertTrue(ready.canSubmit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submit with invalid email sets field error and does not navigate`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.state.test {
            awaitItem() // initial
            vm.onAction(RegisterAction.EmailChanged("not-an-email"))
            vm.onAction(RegisterAction.PasswordChanged("password123"))
            vm.onAction(RegisterAction.NameChanged("Ana"))
            vm.onAction(RegisterAction.LastNameChanged("García"))
            vm.onAction(RegisterAction.EstablishmentsSelected(listOf(establishments[0])))
            vm.onAction(RegisterAction.Submit)

            val state = awaitState { it.fieldErrors.email != null }
            assertNotNull(state.fieldErrors.email)
            assertNull(state.destination)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submit maps selected establishment ids`() = runTest(dispatcher) {
        val registrationRepo = FakeRegistrationRepository()
        val vm = viewModel(registrationRepo = registrationRepo)

        vm.state.test {
            awaitItem() // initial
            vm.onAction(RegisterAction.EmailChanged("manager@example.com"))
            vm.onAction(RegisterAction.PasswordChanged("password123"))
            vm.onAction(RegisterAction.NameChanged("Ana"))
            vm.onAction(RegisterAction.LastNameChanged("García"))
            vm.onAction(RegisterAction.EstablishmentsSelected(establishments))
            vm.onAction(RegisterAction.Submit)

            awaitState { it.destination != null }
            assertEquals(listOf("1", "2"), registrationRepo.lastRegistration?.establishmentIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `optional fields left empty travel as null`() = runTest(dispatcher) {
        val registrationRepo = FakeRegistrationRepository()
        val vm = viewModel(registrationRepo = registrationRepo)

        vm.state.test {
            awaitItem() // initial
            fillValidForm(vm)
            // Blank-but-touched fields must not be sent as empty strings.
            vm.onAction(RegisterAction.LocalityChanged("   "))
            vm.onAction(RegisterAction.Submit)

            awaitState { it.destination != null }
            val sent = registrationRepo.lastRegistration
            assertNull(sent?.locality)
            assertNull(sent?.description)
            assertNull(sent?.contactPhone)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `optional fields are trimmed and sent when filled`() = runTest(dispatcher) {
        val registrationRepo = FakeRegistrationRepository()
        val vm = viewModel(registrationRepo = registrationRepo)

        vm.state.test {
            awaitItem() // initial
            fillValidForm(vm)
            vm.onAction(RegisterAction.LocalityChanged("  Yerba Buena "))
            vm.onAction(RegisterAction.DescriptionChanged(" Relevo comedores del sur "))
            vm.onAction(RegisterAction.ContactPhoneChanged(" +54 (381) 555-5555 "))
            vm.onAction(RegisterAction.Submit)

            awaitState { it.destination != null }
            val sent = registrationRepo.lastRegistration
            assertEquals("Yerba Buena", sent?.locality)
            assertEquals("Relevo comedores del sur", sent?.description)
            assertEquals("+54 (381) 555-5555", sent?.contactPhone)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `canSubmit stays true when optional fields are empty`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.state.test {
            awaitItem() // initial
            fillValidForm(vm)
            val ready = awaitState { it.selectedEstablishments.isNotEmpty() }
            assertTrue(ready.canSubmit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `canSubmit is false while contact phone has invalid characters`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.state.test {
            awaitItem() // initial
            fillValidForm(vm)
            vm.onAction(RegisterAction.ContactPhoneChanged("no-es-un-telefono"))
            val invalid = awaitState { it.contactPhone.value == "no-es-un-telefono" }
            assertFalse(invalid.canSubmit)
            assertNotNull(invalid.contactPhone.error)

            vm.onAction(RegisterAction.ContactPhoneChanged("+54 381 555 5555"))
            val valid = awaitState { it.contactPhone.value == "+54 381 555 5555" }
            assertTrue(valid.canSubmit)
            assertNull(valid.contactPhone.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `canSubmit is false when contact phone exceeds 20 characters`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.state.test {
            awaitItem() // initial
            fillValidForm(vm)
            vm.onAction(RegisterAction.ContactPhoneChanged("1".repeat(21)))
            val state = awaitState { it.contactPhone.value.length == 21 }
            assertFalse(state.canSubmit)
            assertNotNull(state.contactPhone.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `locality and description enforce their length limits`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.state.test {
            awaitItem() // initial
            fillValidForm(vm)
            vm.onAction(RegisterAction.LocalityChanged("a".repeat(101)))
            vm.onAction(RegisterAction.DescriptionChanged("b".repeat(501)))
            val tooLong = awaitState { it.locality.error != null && it.description.error != null }
            assertFalse(tooLong.canSubmit)

            vm.onAction(RegisterAction.LocalityChanged("a".repeat(100)))
            vm.onAction(RegisterAction.DescriptionChanged("b".repeat(500)))
            val atLimit = awaitState { it.locality.error == null && it.description.error == null }
            assertTrue(atLimit.canSubmit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `optional field errors stay hidden while typing and show after blur`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.state.test {
            awaitItem() // initial
            vm.onAction(RegisterAction.ContactPhoneFocusChanged(true))
            vm.onAction(RegisterAction.ContactPhoneChanged("abc"))
            val typing = awaitState { it.contactPhone.value == "abc" }
            assertNull(typing.contactPhone.displayError)

            vm.onAction(RegisterAction.ContactPhoneFocusChanged(false))
            val blurred = awaitState { !it.contactPhone.isFocused && it.contactPhone.hasBlurred }
            assertNotNull(blurred.contactPhone.displayError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submit with invalid optional field forces its error and does not navigate`() = runTest(dispatcher) {
        val registrationRepo = FakeRegistrationRepository()
        val vm = viewModel(registrationRepo = registrationRepo)

        vm.state.test {
            awaitItem() // initial
            fillValidForm(vm)
            vm.onAction(RegisterAction.ContactPhoneChanged("abc"))
            vm.onAction(RegisterAction.Submit)

            val state = awaitState { it.contactPhone.displayError != null }
            assertNull(state.destination)
            assertNull(registrationRepo.lastRegistration)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submit success sets destination to Success`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.state.test {
            awaitItem() // initial
            fillValidForm(vm)
            vm.onAction(RegisterAction.Submit)

            val state = awaitState { it.destination != null }
            assertEquals(RegisterDestination.Success, state.destination)
            // Stays loading through navigation; the screen is torn down before the spinner clears.
            assertTrue(state.isSubmitting)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submit maps UserAlreadyExists to a message`() = runTest(dispatcher) {
        assertSubmitErrorContains(RegistrationError.UserAlreadyExists, "Ya existe")
    }

    @Test
    fun `submit maps EstablishmentNotFound to a message`() = runTest(dispatcher) {
        assertSubmitErrorContains(RegistrationError.EstablishmentNotFound, "establecimientos")
    }

    @Test
    fun `submit maps InvalidData to a message`() = runTest(dispatcher) {
        assertSubmitErrorContains(RegistrationError.InvalidData, "no son válidos")
    }

    @Test
    fun `submit maps Network to a message`() = runTest(dispatcher) {
        assertSubmitErrorContains(RegistrationError.Network, "conectarnos")
    }

    private suspend fun assertSubmitErrorContains(error: RegistrationError, expected: String) {
        val registrationRepo = FakeRegistrationRepository().apply {
            result = Result.failure(RegistrationFailure(error, null))
        }
        val vm = viewModel(registrationRepo = registrationRepo)

        vm.state.test {
            awaitItem() // initial
            fillValidForm(vm)
            vm.onAction(RegisterAction.Submit)

            val state = awaitState { it.submitError != null }
            assertNull(state.destination)
            assertTrue(
                state.submitError?.contains(expected) == true,
                "Expected submitError to contain '$expected' but was '${state.submitError}'",
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isSubmitting turns true in flight and stays true on success`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val registrationRepo = FakeRegistrationRepository().apply { this.gate = gate }
        val vm = viewModel(registrationRepo = registrationRepo)

        vm.state.test {
            awaitItem() // initial
            fillValidForm(vm)
            vm.onAction(RegisterAction.Submit)

            // Request in flight (gate not yet completed).
            assertTrue(awaitState { it.isSubmitting }.isSubmitting)

            gate.complete(Unit)
            // On success the flag stays true so the button keeps loading until navigation
            // tears down the screen (avoids the idle-button flash).
            val done = awaitState { it.destination != null }
            assertTrue(done.isSubmitting)
            assertEquals(RegisterDestination.Success, done.destination)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
