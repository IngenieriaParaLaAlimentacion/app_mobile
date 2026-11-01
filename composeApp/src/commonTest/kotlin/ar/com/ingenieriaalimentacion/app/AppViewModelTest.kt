package ar.com.ingenieriaalimentacion.app

import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.data.notification.PushOpenBridge
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.di.AppScope
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession
import ar.com.ingenieriaalimentacion.domain.usecase.auth.ClearLocalDataUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.InitializeAuthUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.ObserveSessionStatusUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.ClearEstablishmentCacheUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.notification.RegisterPushDeviceUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.notification.SelectEstablishmentFromPushUseCase
import ar.com.ingenieriaalimentacion.fake.FakeAuthRepository
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.FakeDatabaseCleaner
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
import ar.com.ingenieriaalimentacion.fake.FakePushDeviceRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        PushOpenBridge.pushes.value?.let(PushOpenBridge::consume)
        Dispatchers.resetMain()
    }

    private fun viewModel(
        authRepo: FakeAuthRepository,
        profileRepo: FakeProfileRepository = FakeProfileRepository(),
        cleaner: FakeDatabaseCleaner = FakeDatabaseCleaner(),
        pushRepo: FakePushDeviceRepository = FakePushDeviceRepository(),
    ) = AppViewModel(
        initializeAuth = InitializeAuthUseCase(authRepo),
        observeSessionStatus = ObserveSessionStatusUseCase(authRepo),
        syncCoordinator = SyncCoordinator(
            engines = emptyList(),
            connectivity = FakeConnectivityObserver(),
            scope = AppScope(),
        ),
        clearEstablishmentCache = ClearEstablishmentCacheUseCase(profileRepo),
        clearLocalData = ClearLocalDataUseCase(cleaner),
        registerPushDevice = RegisterPushDeviceUseCase(authRepo, pushRepo),
        selectEstablishmentFromPush = SelectEstablishmentFromPushUseCase(profileRepo),
    )

    @Test
    fun `explicit sign-out wipes all local data and not just the establishment cache`() = runTest(dispatcher) {
        val authRepo = FakeAuthRepository()
        val profileRepo = FakeProfileRepository()
        val cleaner = FakeDatabaseCleaner()
        viewModel(authRepo, profileRepo, cleaner)
        advanceUntilIdle()

        authRepo.emit(SessionStatus.NotAuthenticated(isSignOut = true))
        advanceUntilIdle()

        assertEquals(1, cleaner.clearAllCount)
        assertEquals(0, profileRepo.clearCacheCount)
    }

    @Test
    fun `forced logout clears only the establishment cache and keeps local uploads`() = runTest(dispatcher) {
        val authRepo = FakeAuthRepository()
        val profileRepo = FakeProfileRepository()
        val cleaner = FakeDatabaseCleaner()
        viewModel(authRepo, profileRepo, cleaner)
        advanceUntilIdle()

        authRepo.emit(SessionStatus.NotAuthenticated(isSignOut = false))
        advanceUntilIdle()

        assertEquals(0, cleaner.clearAllCount)
        assertEquals(1, profileRepo.clearCacheCount)
    }

    @Test
    fun `restored authentication registers once across authenticated refreshes`() = runTest(dispatcher) {
        val authRepo = FakeAuthRepository().apply {
            emit(SessionStatus.Authenticated(UserSession("access-1", "refresh-1")))
        }
        val pushRepo = FakePushDeviceRepository()
        viewModel(authRepo = authRepo, pushRepo = pushRepo)
        advanceUntilIdle()

        authRepo.emit(SessionStatus.Authenticated(UserSession("access-2", "refresh-2")))
        advanceUntilIdle()

        assertEquals(listOf("register:"), pushRepo.events)
    }

    @Test
    fun `pending tap waits for auth then selects establishment before navigation`() = runTest(dispatcher) {
        val authRepo = FakeAuthRepository().apply {
            emit(SessionStatus.NotAuthenticated())
        }
        val profileRepo = FakeProfileRepository()
        val appViewModel = viewModel(authRepo = authRepo, profileRepo = profileRepo)

        appViewModel.pushDestinations.test {
            PushOpenBridge.handle(
                mapOf(
                    "type" to "stale_establishment",
                    "establishment_id" to "establishment-1",
                    "stale_days" to "7",
                ),
            )
            advanceUntilIdle()
            expectNoEvents()

            authRepo.emit(SessionStatus.Authenticated(UserSession("access", "refresh")))
            advanceUntilIdle()

            assertEquals(AppPushDestination.FoodStock, awaitItem())
            assertEquals("establishment-1", profileRepo.currentEstablishmentId)
            assertEquals(1, profileRepo.setCurrentEstablishmentIdCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `malformed push emits no navigation`() = runTest(dispatcher) {
        val authRepo = FakeAuthRepository().apply {
            emit(SessionStatus.Authenticated(UserSession("access", "refresh")))
        }
        val appViewModel = viewModel(authRepo)

        appViewModel.pushDestinations.test {
            PushOpenBridge.handle(
                mapOf(
                    "type" to "stale_establishment",
                    "establishment_id" to "establishment-1",
                    "stale_days" to "8",
                ),
            )
            advanceUntilIdle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
