package ar.com.ingenieriaalimentacion.presentation.feature.measurement.create_measurement

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.di.AppScope
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.CreateMeasurementUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.FakeMeasurementRepository
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MeasurementViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private val subject = SubjectSummary(id = "subj-1", dni = 42, name = "Ana", lastName = "García", sex = Sex.Female)

    private fun establishment(id: String, status: EstablishmentStatus) = EstablishmentWithStatus(
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
        measRepo: FakeMeasurementRepository = FakeMeasurementRepository(),
        profileRepo: FakeProfileRepository,
    ): MeasurementViewModel {
        val coordinator = SyncCoordinator(listOf(measRepo), FakeConnectivityObserver(), AppScope())
        return MeasurementViewModel(
            createMeasurement = CreateMeasurementUseCase(measRepo),
            getMyEstablishments = GetMyEstablishmentsUseCase(profileRepo),
            getCurrentEstablishment = GetCurrentEstablishmentUseCase(profileRepo),
            syncCoordinator = coordinator,
        )
    }

    private suspend fun ReceiveTurbine<MeasurementState>.awaitState(
        predicate: (MeasurementState) -> Boolean,
    ): MeasurementState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun `profile load filters to Approved and auto-selects when exactly one`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(
                listOf(
                    establishment("a", EstablishmentStatus.Approved),
                    establishment("b", EstablishmentStatus.Pending),
                ),
            )
        }
        val vm = viewModel(profileRepo = profileRepo)

        vm.state.test {
            val loaded = awaitState { !it.isLoadingProfile }
            assertEquals(listOf("a"), loaded.establishmentOptions.map { it.id })
            assertEquals("a", loaded.selectedEstablishment?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `two approved establishments are not auto-selected`() = runTest(dispatcher) {
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
            val loaded = awaitState { !it.isLoadingProfile }
            assertEquals(2, loaded.establishmentOptions.size)
            assertNull(loaded.selectedEstablishment)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `enqueue success resets peso and talla but keeps subject and establishment`() = runTest(dispatcher) {
        val measRepo = FakeMeasurementRepository()
        val profileRepo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(listOf(establishment("a", EstablishmentStatus.Approved)))
        }
        val vm = viewModel(measRepo = measRepo, profileRepo = profileRepo)

        vm.state.test {
            awaitState { !it.isLoadingProfile && it.selectedEstablishment != null }
            vm.setSubjectFromResult(subject)
            vm.onAction(MeasurementAction.WeightChanged("72,5"))
            vm.onAction(MeasurementAction.HeightChanged("170"))
            awaitState { it.weight.value == "72.5" && it.height.value == "170" && it.subject != null }

            vm.onAction(MeasurementAction.Submit)
            val done = awaitState { it.successMessage != null }
            assertEquals("Medición guardada", done.successMessage)
            assertEquals("", done.weight.value)
            assertEquals("", done.height.value)
            assertEquals("subj-1", done.subject?.id)
            assertEquals("a", done.selectedEstablishment?.id)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, measRepo.enqueued.size)
        assertEquals(72.5, measRepo.enqueued.first().weightKg)
        assertEquals(170.0, measRepo.enqueued.first().heightCm)
        assertEquals("subj-1", measRepo.enqueued.first().subject.id)
    }

    @Test
    fun `submit without establishment blocks enqueue and shows the establishment error`() = runTest(dispatcher) {
        val measRepo = FakeMeasurementRepository()
        val profileRepo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(emptyList()) // field worker with no approved establishments
        }
        val vm = viewModel(measRepo = measRepo, profileRepo = profileRepo)

        vm.state.test {
            awaitState { !it.isLoadingProfile }
            vm.setSubjectFromResult(subject)
            vm.onAction(MeasurementAction.WeightChanged("72,5"))
            vm.onAction(MeasurementAction.HeightChanged("170"))
            awaitState { it.weight.value == "72.5" && it.height.value == "170" }

            vm.onAction(MeasurementAction.Submit)
            val blocked = awaitState { it.establishmentError != null }
            assertEquals("Elegí el establecimiento.", blocked.establishmentError)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(measRepo.enqueued.isEmpty())
    }

    @Test
    fun `out-of-range peso blocks enqueue and surfaces the error on submit`() = runTest(dispatcher) {
        val measRepo = FakeMeasurementRepository()
        val profileRepo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(listOf(establishment("a", EstablishmentStatus.Approved)))
        }
        val vm = viewModel(measRepo = measRepo, profileRepo = profileRepo)

        vm.state.test {
            awaitState { !it.isLoadingProfile && it.selectedEstablishment != null }
            vm.setSubjectFromResult(subject)
            vm.onAction(MeasurementAction.WeightChanged("0,4")) // below 0.5 lower bound
            vm.onAction(MeasurementAction.HeightChanged("170"))
            awaitState { it.weight.value == "0.4" }

            vm.onAction(MeasurementAction.Submit)
            val blocked = awaitState { it.weight.displayError != null }
            assertEquals("Peso entre 0,5 y 300 kg.", blocked.weight.displayError)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(measRepo.enqueued.isEmpty())
    }

    @Test
    fun `a current establishment pre-fills selection even with two approved options`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(
                listOf(
                    establishment("a", EstablishmentStatus.Approved),
                    establishment("b", EstablishmentStatus.Approved),
                ),
            )
            currentEstablishmentId = "b"
        }
        val vm = viewModel(profileRepo = profileRepo)

        vm.state.test {
            val loaded = awaitState { !it.isLoadingProfile }
            assertEquals(2, loaded.establishmentOptions.size)
            assertEquals("b", loaded.selectedEstablishment?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a stale current establishment id falls back to today's behavior`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(
                listOf(
                    establishment("a", EstablishmentStatus.Approved),
                    establishment("b", EstablishmentStatus.Approved),
                ),
            )
            currentEstablishmentId = "revoked"
        }
        val vm = viewModel(profileRepo = profileRepo)

        vm.state.test {
            val loaded = awaitState { !it.isLoadingProfile }
            assertNull(loaded.selectedEstablishment)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `overriding the establishment in Measurement does not persist as the current establishment`() =
        runTest(dispatcher) {
            val profileRepo = FakeProfileRepository().apply {
                myEstablishmentsResult = Result.success(
                    listOf(
                        establishment("a", EstablishmentStatus.Approved),
                        establishment("b", EstablishmentStatus.Approved),
                    ),
                )
                currentEstablishmentId = "a"
            }
            val vm = viewModel(profileRepo = profileRepo)

            vm.state.test {
                awaitState { !it.isLoadingProfile }
                vm.onAction(MeasurementAction.EstablishmentSelected(establishment("b", EstablishmentStatus.Approved)))
                val overridden = awaitState { it.selectedEstablishment?.id == "b" }
                assertEquals("b", overridden.selectedEstablishment?.id)
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(0, profileRepo.setCurrentEstablishmentIdCount)
        }

    @Test
    fun `subject result is reflected in state`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(emptyList())
        }
        val vm = viewModel(profileRepo = profileRepo)

        vm.state.test {
            awaitState { !it.isLoadingProfile }
            vm.setSubjectFromResult(subject)
            val withSubject = awaitState { it.subject != null }
            assertEquals("Ana", withSubject.subject?.name)
            assertEquals("García", withSubject.subject?.lastName)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
