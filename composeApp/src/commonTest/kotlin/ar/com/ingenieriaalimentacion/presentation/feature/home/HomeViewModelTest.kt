package ar.com.ingenieriaalimentacion.presentation.feature.home

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.di.AppScope
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovement
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovementDirection
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.Profile
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseTarget
import ar.com.ingenieriaalimentacion.domain.usecase.food.ObserveFoodMovementsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.ObserveMeasurementsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyProfileUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.SetCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.ObserveSubjectUploadsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.ObserveSurveyResponseUploadsUseCase
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.FakeFoodMovementHistoryRepository
import ar.com.ingenieriaalimentacion.fake.FakeMeasurementHistoryRepository
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
import ar.com.ingenieriaalimentacion.fake.FakeSubjectHistoryRepository
import ar.com.ingenieriaalimentacion.fake.FakeSurveyResponseHistoryRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

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

    private fun profile(role: String) = Profile(
        id = "user-1",
        email = "user@example.com",
        name = "Ana",
        lastName = "García",
        role = role,
        establishments = null,
    )

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
        surveyResponseRepo: FakeSurveyResponseHistoryRepository = FakeSurveyResponseHistoryRepository(),
        foodMovementRepo: FakeFoodMovementHistoryRepository = FakeFoodMovementHistoryRepository(),
        profileRepo: FakeProfileRepository,
        connectivity: FakeConnectivityObserver = FakeConnectivityObserver(initial = true),
    ): HomeViewModel {
        val coordinator = SyncCoordinator(emptyList(), connectivity, AppScope())
        return HomeViewModel(
            observeSubjectUploads = ObserveSubjectUploadsUseCase(subjectRepo),
            observeMeasurements = ObserveMeasurementsUseCase(measurementRepo),
            observeSurveyResponseUploads = ObserveSurveyResponseUploadsUseCase(surveyResponseRepo),
            observeFoodMovements = ObserveFoodMovementsUseCase(foodMovementRepo),
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

    @Test
    fun `a survey response appears in recent activity`() = runTest(dispatcher) {
        val surveyResponseRepo = FakeSurveyResponseHistoryRepository()
        val profileRepo = FakeProfileRepository().apply {
            meResult = Result.success(profile("field_worker"))
            myEstablishmentsResult = Result.success(emptyList())
        }
        val vm = viewModel(surveyResponseRepo = surveyResponseRepo, profileRepo = profileRepo)

        vm.state.test {
            awaitState { it.roleLabel != null }

            surveyResponseRepo.localMerged.value = listOf(
                SurveyResponseSummary(
                    id = "sr1",
                    surveyId = "s1",
                    surveyName = "Encuesta nutricional",
                    responseTarget = SurveyResponseTarget.Establishment,
                    targetLabel = "Escuela 1",
                    eventAt = "2026-06-29T12:00:00Z",
                    syncState = SyncState.Pending,
                    isLocal = true,
                ),
            )
            val afterEmission = awaitState { it.recent.any { row -> row.id == "sr1" } }
            val row = afterEmission.recent.single { it.id == "sr1" }
            assertEquals(RecentActivityUi.Kind.SurveyResponse, row.kind)
            assertEquals("Escuela 1", row.subtitle)
            assertEquals("Pendiente", row.badgeText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a food movement appears in recent activity`() = runTest(dispatcher) {
        val foodMovementRepo = FakeFoodMovementHistoryRepository()
        val profileRepo = FakeProfileRepository().apply {
            meResult = Result.success(profile("field_worker"))
            myEstablishmentsResult = Result.success(emptyList())
        }
        val vm = viewModel(foodMovementRepo = foodMovementRepo, profileRepo = profileRepo)

        vm.state.test {
            awaitState { it.roleLabel != null }

            foodMovementRepo.localMerged.value = listOf(
                FoodMovement(
                    id = "fi1",
                    direction = FoodMovementDirection.Intake,
                    occurredAt = "2026-06-29T12:00:00Z",
                    occurredAtMillis = 1_782_388_800_000L,
                    establishmentName = "Escuela 1",
                    lines = emptyList(),
                ),
            )
            val afterEmission = awaitState { it.recent.any { row -> row.id == "Intake:fi1" } }
            val row = afterEmission.recent.single { it.id == "Intake:fi1" }
            assertEquals(RecentActivityUi.Kind.FoodIntake, row.kind)
            assertEquals("Ingreso de alimentos", row.title)
            assertEquals("Escuela 1", row.subtitle)
            assertEquals("0 alimentos", row.badgeText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `food overview click navigates and destination clears`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(emptyList())
        }
        val viewModel = viewModel(profileRepo = profileRepo)
        advanceUntilIdle()

        viewModel.onAction(HomeAction.FoodOverviewClick)

        assertEquals(
            HomeDestination.NavigateToFoodOverview,
            viewModel.state.value.destination,
        )
        viewModel.clearDestination()
        assertNull(viewModel.state.value.destination)
    }

    @Test
    fun `quick actions expose exactly the four home entry points`() {
        assertEquals(
            listOf("Medición", "Sujeto", "Encuesta", "Alimentos"),
            QUICK_ACTIONS.map { it.label },
        )
        assertEquals(HomeAction.FoodOverviewClick, QUICK_ACTIONS.last().action)
    }

    @Test
    fun `admin and analyst quick actions wait for an approved current establishment`() =
        runTest(dispatcher) {
            listOf("admin", "analyst").forEach { role ->
                val profileRepo = FakeProfileRepository().apply {
                    meResult = Result.success(profile(role))
                    myEstablishmentsResult = Result.success(listOf(establishment("a")))
                }
                val vm = viewModel(profileRepo = profileRepo)
                advanceUntilIdle()

                assertFalse(vm.state.value.quickActionsEnabled, role)
                assertEquals(
                    "Elegí un establecimiento aprobado para registrar datos.",
                    vm.state.value.quickActionsHint,
                )

                vm.onAction(HomeAction.EstablishmentSelected(establishment("a")))
                advanceUntilIdle()
                assertTrue(vm.state.value.quickActionsEnabled, role)
                assertNull(vm.state.value.quickActionsHint)
            }
        }

    @Test
    fun `admin without establishments is told to subscribe`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository().apply { meResult = Result.success(profile("admin")) }
        val vm = viewModel(profileRepo = profileRepo)
        advanceUntilIdle()

        assertFalse(vm.state.value.quickActionsEnabled)
        assertTrue(vm.state.value.quickActionsHint!!.startsWith("Suscribite"))
    }

    @Test
    fun `field worker quick actions stay enabled without a current establishment`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository().apply { meResult = Result.success(profile("field_worker")) }
        val vm = viewModel(profileRepo = profileRepo)
        advanceUntilIdle()

        assertNull(vm.state.value.currentEstablishment)
        assertTrue(vm.state.value.quickActionsEnabled)
    }

    @Test
    fun `reloadEstablishments drops a current establishment the user left`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(listOf(establishment("a"), establishment("b")))
            currentEstablishmentId = "a"
        }
        val vm = viewModel(profileRepo = profileRepo)
        advanceUntilIdle()
        assertEquals("a", vm.state.value.currentEstablishment?.id)

        profileRepo.myEstablishmentsResult = Result.success(listOf(establishment("b")))
        profileRepo.currentEstablishmentId = null
        vm.reloadEstablishments()
        advanceUntilIdle()

        assertNull(vm.state.value.currentEstablishment)
        assertEquals(listOf("b"), vm.state.value.establishmentOptions.map { it.id })
    }
}
