package ar.com.ingenieriaalimentacion.presentation.feature.home

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.di.AppScope
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.ObserveMeasurementsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyProfileUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.SetCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.ObserveSubjectUploadsUseCase
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.FakeMeasurementHistoryRepository
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
import ar.com.ingenieriaalimentacion.fake.FakeSubjectHistoryRepository
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
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun establishment(id: String, status: EstablishmentStatus = EstablishmentStatus.Approved) =
        EstablishmentWithStatus(
            id = id,
            name = "Escuela $id",
            formattedAddress = "Calle 1",
            latitude = 0.0,
            longitude = 0.0,
            placeId = "p$id",
            city = "Ciudad",
            province = "Provincia",
            status = status,
        )

    private fun viewModel(
        subjectRepo: FakeSubjectHistoryRepository = FakeSubjectHistoryRepository(),
        measurementRepo: FakeMeasurementHistoryRepository = FakeMeasurementHistoryRepository(),
        profileRepo: FakeProfileRepository,
        connectivity: FakeConnectivityObserver = FakeConnectivityObserver(initial = true),
    ): HomeViewModel {
        val coordinator = SyncCoordinator(emptyList(), connectivity, AppScope())
        return HomeViewModel(
            observeSubjectUploads = ObserveSubjectUploadsUseCase(subjectRepo),
            observeMeasurements = ObserveMeasurementsUseCase(measurementRepo),
            getMyProfile = GetMyProfileUseCase(profileRepo),
            getMyEstablishments = GetMyEstablishmentsUseCase(profileRepo),
            getCurrentEstablishment = GetCurrentEstablishmentUseCase(profileRepo),
            setCurrentEstablishment = SetCurrentEstablishmentUseCase(profileRepo),
            syncCoordinator = coordinator,
            connectivity = connectivity,
            now = { 0L },
        )
    }

    private suspend fun ReceiveTurbine<HomeState>.awaitState(
        predicate: (HomeState) -> Boolean,
    ): HomeState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun `loads approved establishments and resolves the stored current establishment`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(
                listOf(
                    establishment("a", EstablishmentStatus.Approved),
                    establishment("b", EstablishmentStatus.Pending),
                ),
            )
            currentEstablishmentId = "a"
        }
        val vm = viewModel(profileRepo = profileRepo)

        vm.state.test {
            val loaded = awaitState { it.establishmentOptions.isNotEmpty() }
            assertEquals(listOf("a"), loaded.establishmentOptions.map { it.id })
            assertEquals("a", loaded.currentEstablishment?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a stored current establishment no longer approved resolves to null`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(listOf(establishment("a", EstablishmentStatus.Approved)))
            currentEstablishmentId = "revoked"
        }
        val vm = viewModel(profileRepo = profileRepo)

        vm.state.test {
            val loaded = awaitState { it.establishmentOptions.isNotEmpty() }
            assertNull(loaded.currentEstablishment)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `EstablishmentSelected updates state and persists exactly once`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(
                listOf(
                    establishment("a", EstablishmentStatus.Approved),
                    establishment("b", EstablishmentStatus.Approved),
                ),
            )
        }
        val vm = viewModel(profileRepo = profileRepo)

        vm.state.test {
            awaitState { it.establishmentOptions.isNotEmpty() }
            vm.onAction(HomeAction.EstablishmentPickerOpen)
            awaitState { it.showEstablishmentPicker }

            vm.onAction(HomeAction.EstablishmentSelected(establishment("b", EstablishmentStatus.Approved)))
            val selected = awaitState { it.currentEstablishment?.id == "b" }
            assertEquals(false, selected.showEstablishmentPicker)
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()

        assertEquals("b", profileRepo.currentEstablishmentId)
        assertEquals(1, profileRepo.setCurrentEstablishmentIdCount)
    }

    @Test
    fun `a queue emission after selection does not reset establishment fields`() = runTest(dispatcher) {
        val subjectRepo = FakeSubjectHistoryRepository()
        val profileRepo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(listOf(establishment("a", EstablishmentStatus.Approved)))
        }
        val vm = viewModel(subjectRepo = subjectRepo, profileRepo = profileRepo)

        vm.state.test {
            awaitState { it.establishmentOptions.isNotEmpty() }
            vm.onAction(HomeAction.EstablishmentSelected(establishment("a", EstablishmentStatus.Approved)))
            awaitState { it.currentEstablishment?.id == "a" }

            // A fresh queue emission re-derives state; establishment fields must survive.
            subjectRepo.merged.value = listOf(
                SubjectHistoryEntry(
                    id = "s1", dni = 1, sex = Sex.Female, name = "Ana", lastName = "García",
                    syncState = SyncState.Pending, canonicalId = null, errorMessage = null,
                    isLocal = true, createdAt = 0L, updatedAt = 0L,
                ),
            )
            val afterEmission = awaitState { it.todaySubjects == 1 }
            assertEquals(listOf("a"), afterEmission.establishmentOptions.map { it.id })
            assertEquals(false, afterEmission.showEstablishmentPicker)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
