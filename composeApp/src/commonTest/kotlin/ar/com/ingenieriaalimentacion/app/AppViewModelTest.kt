package ar.com.ingenieriaalimentacion.app

import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.di.AppScope
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.usecase.auth.ClearLocalDataUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.InitializeAuthUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.ObserveSessionStatusUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.ClearEstablishmentCacheUseCase
import ar.com.ingenieriaalimentacion.fake.FakeAuthRepository
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.FakeDatabaseCleaner
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

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

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
        authRepo: FakeAuthRepository,
        profileRepo: FakeProfileRepository = FakeProfileRepository(),
        cleaner: FakeDatabaseCleaner = FakeDatabaseCleaner(),
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
}
