package ar.com.ingenieriaalimentacion.app

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.data.auth.SessionExit
import ar.com.ingenieriaalimentacion.data.auth.SessionLifecycle
import ar.com.ingenieriaalimentacion.data.auth.storage.MemorySessionManager
import ar.com.ingenieriaalimentacion.data.notification.PushOpenBridge
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.di.AppScope
import ar.com.ingenieriaalimentacion.domain.model.auth.AuthUser
import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository
import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository
import ar.com.ingenieriaalimentacion.domain.repository.StoredSession
import ar.com.ingenieriaalimentacion.domain.usecase.auth.InitializeAuthUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.notification.RegisterPushDeviceUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.notification.SelectEstablishmentFromPushUseCase
import ar.com.ingenieriaalimentacion.fake.FakeAuthRepository
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.FakeDatabaseCleaner
import ar.com.ingenieriaalimentacion.fake.FakeIdGenerator
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
import ar.com.ingenieriaalimentacion.fake.FakePushDeviceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = AppScope()
    private val profileRepo = FakeProfileRepository()
    private val pushRepo = FakePushDeviceRepository()
    private var viewModel: AppViewModel? = null

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        viewModel?.viewModelScope?.cancel()
        scope.cancel()
        PushOpenBridge.pushes.value?.let(PushOpenBridge::consume)
        PushOpenBridge.bind(null)
        Dispatchers.resetMain()
    }

    private fun lifecycle(initial: StoredSession = StoredSession.Empty) = SessionLifecycle(
        store = MemorySessionManager(initial),
        cache = FakeDatabaseCleaner(),
        ids = FakeIdGenerator(),
    )

    private fun viewModel(
        lifecycle: SessionLifecycle,
        profile: ProfileRepository = profileRepo,
    ): AppViewModel {
        val auth = object : AuthRepository by FakeAuthRepository() {
            override val sessionStatus = lifecycle.status
            override suspend fun bootstrap() = lifecycle.bootstrap()
        }
        return AppViewModel(
            initializeAuth = InitializeAuthUseCase(auth),
            lifecycle = lifecycle,
            syncCoordinator = SyncCoordinator(
                engines = emptyList(),
                connectivity = FakeConnectivityObserver(),
                scope = scope,
                lifecycle = lifecycle,
            ),
            registerPushDevice = RegisterPushDeviceUseCase(auth, pushRepo),
            selectEstablishmentFromPush = SelectEstablishmentFromPushUseCase(profile),
        ).also { viewModel = it }
    }

    @Test
    fun `restored authentication registers once across refreshes and again after a new sign-in`() =
        runTest(dispatcher) {
            val lifecycle = lifecycle(StoredSession.Active(userSession("a"), "initial"))
            viewModel(lifecycle)
            advanceUntilIdle()

            val firstLease = requireNotNull(lifecycle.leaseOrNull())
            lifecycle.completeRefresh(firstLease, "refresh-a", UserSession("new-access", "new-refresh"))
            advanceUntilIdle()
            assertEquals(listOf("register:"), pushRepo.events)

            lifecycle.end(firstLease, SessionExit.SignedOut)
            lifecycle.signIn("b")
            advanceUntilIdle()
            assertEquals(listOf("register:", "register:"), pushRepo.events)
        }

    @Test
    fun `a cold-start tap opens an approved establishment after session restore`() = runTest(dispatcher) {
        profileRepo.myEstablishmentsResult = Result.success(listOf(establishment(EstablishmentStatus.Approved)))
        push()
        val lifecycle = lifecycle(StoredSession.Active(userSession("a"), "restored"))
        val app = viewModel(lifecycle)

        app.pushDestinations.test {
            assertEquals(AppPushDestination.FoodStock("restored"), awaitItem())
            assertEquals("establishment-1", profileRepo.currentEstablishmentId)
            assertEquals(1, profileRepo.setCurrentEstablishmentIdCount)
        }
    }

    @Test
    fun `a signed-out tap requires approval for the newly signed-in account`() = runTest(dispatcher) {
        profileRepo.myEstablishmentsResult = Result.success(listOf(establishment(EstablishmentStatus.Pending)))
        val lifecycle = lifecycle()
        val app = viewModel(lifecycle)
        advanceUntilIdle()

        app.pushDestinations.test {
            push()
            lifecycle.signIn("b")
            advanceUntilIdle()
            expectNoEvents()
            assertNull(profileRepo.currentEstablishmentId)
            assertNull(PushOpenBridge.pushes.value)
        }
    }

    @Test
    fun `a pending tap is discarded across block and sign-in`() = runTest(dispatcher) {
        val lifecycle = lifecycle(StoredSession.Active(userSession("a"), "initial"))
        val selected = CompletableDeferred<Unit>()
        val profile = object : ProfileRepository by profileRepo {
            override suspend fun setCurrentEstablishmentId(establishmentId: String?) {
                selected.complete(Unit)
                awaitCancellation()
            }
        }
        val app = viewModel(lifecycle, profile)
        advanceUntilIdle()

        app.pushDestinations.test {
            push()
            advanceUntilIdle()
            assertTrue(selected.isCompleted)
            lifecycle.end(requireNotNull(lifecycle.leaseOrNull()), SessionExit.Blocked)
            lifecycle.signIn("b")
            advanceUntilIdle()

            expectNoEvents()
            assertNull(PushOpenBridge.pushes.value)
            assertEquals(0, profileRepo.setCurrentEstablishmentIdCount)
        }
    }

    @Test
    fun `malformed push emits no navigation`() = runTest(dispatcher) {
        val app = viewModel(lifecycle(StoredSession.Active(userSession("a"), "initial")))
        advanceUntilIdle()

        app.pushDestinations.test {
            push(staleDays = "8")
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    private suspend fun SessionLifecycle.signIn(user: String) {
        completeSignIn(beginSignIn(), userSession(user))
    }

    private fun userSession(user: String) = UserSession(
        accessToken = "access-$user",
        refreshToken = "refresh-$user",
        user = AuthUser(user, "$user@example.com", "Given", "Family", "field_worker"),
    )

    private fun establishment(status: EstablishmentStatus) = EstablishmentWithStatus(
        id = "establishment-1", name = "Kitchen", formattedAddress = "", latitude = 0.0,
        longitude = 0.0, placeId = "", city = "", province = "", status = status,
    )

    private fun push(staleDays: String = "7") {
        PushOpenBridge.handle(
            mapOf(
                "type" to "stale_establishment",
                "establishment_id" to "establishment-1",
                "stale_days" to staleDays,
            ),
        )
    }
}
