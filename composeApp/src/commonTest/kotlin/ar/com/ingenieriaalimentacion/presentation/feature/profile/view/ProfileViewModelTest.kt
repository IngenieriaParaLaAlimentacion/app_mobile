package ar.com.ingenieriaalimentacion.presentation.feature.profile.view

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.Profile
import ar.com.ingenieriaalimentacion.domain.usecase.auth.SignOutUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyProfileUseCase
import ar.com.ingenieriaalimentacion.fake.FakeAuthRepository
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val nonManager = Profile(
        id = "u1", email = "a@b.com", name = "Ana", lastName = "García",
        role = "professional", establishments = null,
    )

    private val managerProfile = Profile(
        id = "u2", email = "m@b.com", name = "Beto", lastName = "López",
        role = "establishment_manager",
        establishments = listOf(
            EstablishmentWithStatus(
                "e1", "Norte", "Calle 1", 0.0, 0.0, "p1", "Ciudad", "Provincia",
                EstablishmentStatus.Pending,
            ),
        ),
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
        profileRepo: FakeProfileRepository,
        authRepo: FakeAuthRepository = FakeAuthRepository(),
    ) = ProfileViewModel(
        getMyProfile = GetMyProfileUseCase(profileRepo),
        signOut = SignOutUseCase(authRepo),
    )

    private suspend fun ReceiveTurbine<ProfileState>.awaitState(
        predicate: (ProfileState) -> Boolean,
    ): ProfileState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun `loads profile on init`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply { meResult = Result.success(nonManager) }
        val vm = viewModel(repo)

        vm.state.test {
            val loaded = awaitState { it.profile != null }
            assertEquals(nonManager, loaded.profile)
            assertFalse(loaded.isLoading)
            assertFalse(loaded.profile?.isManager ?: true)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `manager profile retains establishments`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply { meResult = Result.success(managerProfile) }
        val vm = viewModel(repo)

        vm.state.test {
            val profile = requireNotNull(awaitState { it.profile != null }.profile)
            assertTrue(profile.isManager)
            assertEquals(1, profile.establishments?.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load failure sets loadError`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply { meResult = Result.failure(RuntimeException("boom")) }
        val vm = viewModel(repo)

        vm.state.test {
            val state = awaitState { it.loadError }
            assertTrue(state.loadError)
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `EditClick emits EditProfile destination with profile fields`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply { meResult = Result.success(nonManager) }
        val vm = viewModel(repo)

        vm.state.test {
            awaitState { it.profile != null }
            vm.onAction(ProfileAction.EditClick)
            val state = awaitState { it.destination != null }
            val destination = state.destination
            assertTrue(destination is ProfileDestination.EditProfile)
            assertEquals("Ana", destination.name)
            assertEquals("García", destination.lastName)
            assertEquals("a@b.com", destination.email)
            assertEquals("professional", destination.role)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `MyEstablishmentsClick emits MyEstablishments`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply { meResult = Result.success(managerProfile) }
        val vm = viewModel(repo)

        vm.state.test {
            awaitState { it.profile != null }
            vm.onAction(ProfileAction.MyEstablishmentsClick)
            val state = awaitState { it.destination != null }
            assertEquals(ProfileDestination.MyEstablishments, state.destination)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SignOutClick opens the confirm dialog without signing out`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply { meResult = Result.success(nonManager) }
        val authRepo = FakeAuthRepository()
        val vm = viewModel(repo, authRepo)

        vm.state.test {
            awaitState { it.profile != null }
            vm.onAction(ProfileAction.SignOutClick)
            val state = awaitState { it.showSignOutConfirm }
            assertTrue(state.showSignOutConfirm)
            assertEquals(null, state.destination)
            assertEquals(0, authRepo.signOutCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ConfirmSignOut signs out and emits LoggedOut`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply { meResult = Result.success(nonManager) }
        val authRepo = FakeAuthRepository()
        val vm = viewModel(repo, authRepo)

        vm.state.test {
            awaitState { it.profile != null }
            vm.onAction(ProfileAction.SignOutClick)
            awaitState { it.showSignOutConfirm }
            vm.onAction(ProfileAction.ConfirmSignOut)
            val state = awaitState { it.destination != null }
            assertEquals(ProfileDestination.LoggedOut, state.destination)
            assertFalse(state.showSignOutConfirm)
            assertEquals(1, authRepo.signOutCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `DismissSignOutConfirm closes the dialog without signing out`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply { meResult = Result.success(nonManager) }
        val authRepo = FakeAuthRepository()
        val vm = viewModel(repo, authRepo)

        vm.state.test {
            awaitState { it.profile != null }
            vm.onAction(ProfileAction.SignOutClick)
            awaitState { it.showSignOutConfirm }
            vm.onAction(ProfileAction.DismissSignOutConfirm)
            val state = awaitState { !it.showSignOutConfirm }
            assertFalse(state.showSignOutConfirm)
            assertEquals(null, state.destination)
            assertEquals(0, authRepo.signOutCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh re-fetches the profile`() = runTest(dispatcher) {
        val repo = FakeProfileRepository().apply { meResult = Result.success(nonManager) }
        val vm = viewModel(repo)

        advanceUntilIdle() // initial load
        assertEquals(1, repo.getMeCount)

        vm.refresh()
        advanceUntilIdle()
        assertEquals(2, repo.getMeCount)
    }
}
