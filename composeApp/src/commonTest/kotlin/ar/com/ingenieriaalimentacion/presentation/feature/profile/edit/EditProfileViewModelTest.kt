package ar.com.ingenieriaalimentacion.presentation.feature.profile.edit

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.domain.model.profile.Profile
import ar.com.ingenieriaalimentacion.domain.model.profile.UpdateProfile
import ar.com.ingenieriaalimentacion.domain.usecase.profile.UpdateMyProfileUseCase
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val route = Route.EditProfile(
        name = "Ana", lastName = "García", email = "a@b.com", role = "analyst",
    )

    private val savedProfile = Profile(
        id = "u1", email = "a@b.com", name = "Nuevo", lastName = "García",
        role = "analyst", establishments = null,
    )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repo: FakeProfileRepository) =
        EditProfileViewModel(route = route, updateMyProfile = UpdateMyProfileUseCase(repo))

    private suspend fun ReceiveTurbine<EditProfileState>.awaitState(
        predicate: (EditProfileState) -> Boolean,
    ): EditProfileState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun `prefills from route`() = runTest(dispatcher) {
        val vm = viewModel(FakeProfileRepository())

        val state = vm.state.value
        assertEquals("Ana", state.name)
        assertEquals("García", state.lastName)
        assertEquals("a@b.com", state.email)
        assertEquals("analyst", state.role)
        assertFalse(state.canSave) // no changes yet
    }

    @Test
    fun `no change save skips the network and returns Back`() = runTest(dispatcher) {
        val repo = FakeProfileRepository()
        val vm = viewModel(repo)

        vm.onAction(EditProfileAction.SaveClick)
        advanceUntilIdle()

        assertEquals(0, repo.updateCount)
        assertEquals(EditProfileDestination.Back, vm.state.value.destination)
    }

    @Test
    fun `only the changed field is sent`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply { updateResult = Result.success(savedProfile) }
        val vm = viewModel(repo)

        vm.onAction(EditProfileAction.NameChanged("Nuevo"))
        vm.onAction(EditProfileAction.SaveClick)
        advanceUntilIdle()

        assertEquals(UpdateProfile(name = "Nuevo", lastName = null), repo.lastUpdate)
    }

    @Test
    fun `empty name disables save`() = runTest(dispatcher) {
        val vm = viewModel(FakeProfileRepository())

        vm.onAction(EditProfileAction.NameChanged(""))

        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `successful save emits Saved`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply { updateResult = Result.success(savedProfile) }
        val vm = viewModel(repo)

        vm.state.test {
            awaitItem() // initial
            vm.onAction(EditProfileAction.LastNameChanged("López"))
            vm.onAction(EditProfileAction.SaveClick)
            val state = awaitState { it.destination != null }
            assertEquals(EditProfileDestination.Saved, state.destination)
            // Stays loading through navigation; the screen is torn down before the spinner clears.
            assertTrue(state.isSaving)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failed save sets saveError and stays`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply { updateResult = Result.failure(RuntimeException("boom")) }
        val vm = viewModel(repo)

        vm.state.test {
            awaitItem() // initial
            vm.onAction(EditProfileAction.NameChanged("Nuevo"))
            vm.onAction(EditProfileAction.SaveClick)
            val state = awaitState { it.saveError != null }
            assertTrue(state.saveError != null)
            assertNull(state.destination)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
