package ar.com.ingenieriaalimentacion.presentation.feature.register

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.data.registration.RegistrationFailure
import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment
import ar.com.ingenieriaalimentacion.domain.model.registration.RegistrationError
import ar.com.ingenieriaalimentacion.domain.usecase.establishment.GetEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.registration.RegisterManagerUseCase
import ar.com.ingenieriaalimentacion.fake.FakeEstablishmentRepository
import ar.com.ingenieriaalimentacion.fake.FakeRegistrationRepository
import kotlinx.coroutines.CompletableDeferred
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
        establishmentRepo: FakeEstablishmentRepository = FakeEstablishmentRepository().apply {
            result = Result.success(establishments)
        },
        registrationRepo: FakeRegistrationRepository = FakeRegistrationRepository(),
    ) = RegisterViewModel(
        getEstablishments = GetEstablishmentsUseCase(establishmentRepo),
        registerManager = RegisterManagerUseCase(registrationRepo),
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
        vm.onAction(RegisterAction.EstablishmentToggled("1"))
    }

    @Test
    fun `loads establishments into state on init`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.state.test {
            val loaded = awaitState { it.establishments.isNotEmpty() }
            assertEquals(establishments, loaded.establishments)
            assertFalse(loaded.isLoadingEstablishments)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `field changes update state`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.state.test {
            awaitState { it.establishments.isNotEmpty() }
            vm.onAction(RegisterAction.EmailChanged("a@b.com"))
            vm.onAction(RegisterAction.NameChanged("Ana"))
            val state = awaitState { it.email == "a@b.com" && it.name == "Ana" }
            assertEquals("a@b.com", state.email)
            assertEquals("Ana", state.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling selection adds and removes ids`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.state.test {
            awaitState { it.establishments.isNotEmpty() }

            vm.onAction(RegisterAction.EstablishmentToggled("1"))
            vm.onAction(RegisterAction.EstablishmentToggled("2"))
            assertEquals(setOf("1", "2"), awaitState { it.selectedEstablishmentIds.size == 2 }.selectedEstablishmentIds)

            vm.onAction(RegisterAction.EstablishmentToggled("1"))
            assertEquals(setOf("2"), awaitState { it.selectedEstablishmentIds == setOf("2") }.selectedEstablishmentIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `canSubmit is false when fields blank or no establishment selected`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.state.test {
            val initial = awaitState { it.establishments.isNotEmpty() }
            assertFalse(initial.canSubmit)

            vm.onAction(RegisterAction.EmailChanged("manager@example.com"))
            vm.onAction(RegisterAction.PasswordChanged("password123"))
            vm.onAction(RegisterAction.NameChanged("Ana"))
            vm.onAction(RegisterAction.LastNameChanged("García"))
            // Still no establishment selected.
            val withoutEstablishment = awaitState { it.lastName == "García" }
            assertFalse(withoutEstablishment.canSubmit)

            vm.onAction(RegisterAction.EstablishmentToggled("1"))
            val ready = awaitState { it.selectedEstablishmentIds.isNotEmpty() }
            assertTrue(ready.canSubmit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submit with invalid email sets field error and does not navigate`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.state.test {
            awaitState { it.establishments.isNotEmpty() }
            vm.onAction(RegisterAction.EmailChanged("not-an-email"))
            vm.onAction(RegisterAction.PasswordChanged("password123"))
            vm.onAction(RegisterAction.NameChanged("Ana"))
            vm.onAction(RegisterAction.LastNameChanged("García"))
            vm.onAction(RegisterAction.EstablishmentToggled("1"))
            vm.onAction(RegisterAction.Submit)

            val state = awaitState { it.fieldErrors.email != null }
            assertNotNull(state.fieldErrors.email)
            assertNull(state.destination)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submit success sets destination to Success`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.state.test {
            awaitState { it.establishments.isNotEmpty() }
            fillValidForm(vm)
            vm.onAction(RegisterAction.Submit)

            val state = awaitState { it.destination != null }
            assertEquals(RegisterDestination.Success, state.destination)
            assertFalse(state.isSubmitting)
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
            awaitState { it.establishments.isNotEmpty() }
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
    fun `isSubmitting toggles around the call`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val registrationRepo = FakeRegistrationRepository().apply { this.gate = gate }
        val vm = viewModel(registrationRepo = registrationRepo)

        vm.state.test {
            awaitState { it.establishments.isNotEmpty() }
            fillValidForm(vm)
            vm.onAction(RegisterAction.Submit)

            // Request in flight (gate not yet completed).
            assertTrue(awaitState { it.isSubmitting }.isSubmitting)

            gate.complete(Unit)
            val done = awaitState { it.destination != null }
            assertFalse(done.isSubmitting)
            assertEquals(RegisterDestination.Success, done.destination)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
