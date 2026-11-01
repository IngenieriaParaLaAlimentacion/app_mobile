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
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.MeasurementAlertEvaluator
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.PrepareMeasurementUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.FakeMeasurementRepository
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

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

    private val subject = SubjectSummary(id = "subj-1", dni = 42, name = "Ana", lastName = "García", sex = Sex.Female, birthDate = kotlinx.datetime.LocalDate(1990, 1, 1))

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

    private var clock = Instant.parse("2026-10-16T23:59:00Z")

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
            prepareMeasurement = PrepareMeasurementUseCase(MeasurementAlertEvaluator()) { clock },
            now = { clock },
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
            vm.onAction(MeasurementAction.HeightChanged("175"))
            awaitState { it.weight.value == "72.5" && it.height.value == "175" && it.subject != null }

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
        assertEquals(175.0, measRepo.enqueued.first().heightCm)
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

    private fun approvedProfile() = FakeProfileRepository().apply {
        myEstablishmentsResult = Result.success(listOf(establishment("a", EstablishmentStatus.Approved)))
    }

    private fun MeasurementViewModel.fill(weight: String = "90", selectedSubject: SubjectSummary = subject) {
        setSubjectFromResult(selectedSubject)
        onAction(MeasurementAction.WeightChanged(weight))
        onAction(MeasurementAction.HeightChanged("170"))
    }

    @Test
    fun `offline alerts wait for explicit confirmation and duplicate taps insert once`() = runTest(dispatcher) {
        for (weight in listOf("40", "90")) {
            val repo = FakeMeasurementRepository()
            val vm = viewModel(repo, approvedProfile())
            vm.state.test {
                awaitState { !it.isLoadingProfile }
                vm.fill(weight)
                vm.onAction(MeasurementAction.Submit)
                vm.onAction(MeasurementAction.Submit)
                val preview = awaitState { it.confirmation != null }
                val token = assertNotNull(preview.confirmation).token
                assertFalse(preview.isSubmitting)
                assertFalse(preview.canSubmit)
                assertTrue(repo.enqueued.isEmpty())
                assertEquals(0, repo.syncCalls)
                vm.onAction(MeasurementAction.ConfirmMeasurement(token))
                vm.onAction(MeasurementAction.ConfirmMeasurement(token))
                vm.onAction(MeasurementAction.Submit)
                // Synchronous guards also reject programmatic edits/navigation during insertion.
                vm.onAction(MeasurementAction.WeightChanged("10"))
                vm.onAction(MeasurementAction.HeightChanged("20"))
                vm.onAction(MeasurementAction.ChangeSubjectClick)
                vm.onAction(MeasurementAction.BackClick)
                val saved = awaitState { it.successMessage != null }
                assertNull(saved.destination)
                assertEquals(1, repo.enqueued.size)
                assertEquals(weight.toDouble(), repo.enqueued.single().weightKg)
                assertEquals(170.0, repo.enqueued.single().heightCm)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `dismiss keeps exact input and stale callbacks cannot affect newer modal`() = runTest(dispatcher) {
        val repo = FakeMeasurementRepository()
        val vm = viewModel(repo, approvedProfile())
        vm.state.test {
            awaitState { !it.isLoadingProfile }
            vm.fill("120,7500")
            vm.onAction(MeasurementAction.Submit)
            val old = assertNotNull(awaitState { it.confirmation != null }.confirmation)
            vm.onAction(MeasurementAction.DismissConfirmation(old.token))
            val dismissed = awaitState { it.confirmation == null && it.weight.value == "120.7500" }
            assertEquals("170", dismissed.height.value)
            assertTrue(repo.enqueued.isEmpty())
            vm.onAction(MeasurementAction.Submit)
            val fresh = assertNotNull(awaitState { it.confirmation != null }.confirmation)
            assertTrue(fresh.token > old.token)
            vm.onAction(MeasurementAction.DismissConfirmation(old.token))
            vm.onAction(MeasurementAction.ConfirmMeasurement(old.token))
            runCurrent()
            assertEquals(fresh, vm.state.value.confirmation)
            assertTrue(repo.enqueued.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `each form identity change invalidates an unconfirmed draft`() = runTest(dispatcher) {
        val changes = listOf<MeasurementAction>(
            MeasurementAction.WeightChanged("91"), MeasurementAction.HeightChanged("171"),
            MeasurementAction.SubjectSelected(subject.copy(id = "other")),
            MeasurementAction.EstablishmentSelected(establishment("b", EstablishmentStatus.Approved)),
            MeasurementAction.ChangeSubjectClick,
            MeasurementAction.MeasurementDateSelected(LocalDate(2024, 5, 2)),
            MeasurementAction.MeasurementTimeSelected(LocalTime(10, 45)),
        )
        for (change in changes) {
            val repo = FakeMeasurementRepository()
            val vm = viewModel(repo, approvedProfile())
            vm.state.test {
                awaitState { !it.isLoadingProfile }
                vm.fill()
                vm.onAction(MeasurementAction.Submit)
                val old = assertNotNull(awaitState { it.confirmation != null }.confirmation)
                vm.onAction(change)
                awaitState { it.confirmation == null }
                vm.onAction(MeasurementAction.ConfirmMeasurement(old.token))
                runCurrent()
                assertTrue(repo.enqueued.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `profile reload selecting a different establishment invalidates the draft`() = runTest(dispatcher) {
        val profile = approvedProfile()
        val repo = FakeMeasurementRepository()
        val vm = viewModel(repo, profile)
        vm.state.test {
            awaitState { !it.isLoadingProfile }
            vm.fill()
            vm.onAction(MeasurementAction.Submit)
            val old = assertNotNull(awaitState { it.confirmation != null }.confirmation)
            profile.myEstablishmentsResult = Result.success(listOf(establishment("b", EstablishmentStatus.Approved)))
            vm.onAction(MeasurementAction.RetryProfile)
            awaitState { !it.isLoadingProfile && it.selectedEstablishment?.id == "b" }
            vm.onAction(MeasurementAction.ConfirmMeasurement(old.token))
            runCurrent()
            assertNull(vm.state.value.confirmation)
            assertTrue(repo.enqueued.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failure retry reuses confirmed values and instant across age boundary`() = runTest(dispatcher) {
        val repo = FakeMeasurementRepository().apply { enqueueResult = Result.failure(IllegalStateException("disk")) }
        val vm = viewModel(repo, approvedProfile())
        val birth = kotlinx.datetime.LocalDate(2007, 9, 17)
        vm.state.test {
            awaitState { !it.isLoadingProfile }
            vm.fill("120.7500", subject.copy(birthDate = birth))
            vm.onAction(MeasurementAction.ObservationChanged("Se verificó la medida"))
            vm.onAction(MeasurementAction.Submit)
            val draft = assertNotNull(awaitState { it.confirmation != null }.confirmation)
            val captured = clock
            clock = Instant.parse("2026-10-18T01:30:00Z")
            vm.onAction(MeasurementAction.ConfirmMeasurement(draft.token))
            val failed = awaitState { it.submitError != null }
            assertEquals("120.7500", failed.weight.value)
            assertEquals("Se verificó la medida", failed.observation.value)
            assertTrue(repo.persisted.isEmpty())
            assertEquals(0, repo.syncCalls)
            repo.enqueueResult = Result.success(Unit)
            vm.onAction(MeasurementAction.Submit)
            val retry = assertNotNull(awaitState { it.confirmation != null }.confirmation)
            assertEquals(draft, retry)
            vm.onAction(MeasurementAction.ConfirmMeasurement(retry.token))
            awaitState { it.successMessage != null }
            assertEquals(2, repo.enqueued.size)
            assertEquals(repo.enqueued.first(), repo.enqueued.last())
            assertEquals(captured, repo.persisted.single().measuredAt)
            assertEquals(120.75, repo.persisted.single().weightKg)
            assertEquals("Se verificó la medida", repo.persisted.single().observation)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `missing demographics uses conservative confirmation`() = runTest(dispatcher) {
        val repo = FakeMeasurementRepository()
        val vm = viewModel(repo, approvedProfile())
        vm.state.test {
            awaitState { !it.isLoadingProfile }
            vm.fill("72.5", subject.copy(birthDate = null))
            vm.onAction(MeasurementAction.Submit)
            val preview = assertNotNull(awaitState { it.confirmation != null }.confirmation)
            assertFalse(preview.description.contains("generarían una alerta"))
            assertTrue(repo.enqueued.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `invalid bounds empty values and missing subject never open confirmation`() = runTest(dispatcher) {
        for ((weight, height) in listOf("0.5" to "170", "301" to "170", "90" to "20", "90" to "251", "" to "")) {
            val repo = FakeMeasurementRepository()
            val vm = viewModel(repo, approvedProfile())
            vm.state.test {
                awaitState { !it.isLoadingProfile }
                vm.fill(weight)
                vm.onAction(MeasurementAction.HeightChanged(height))
                vm.onAction(MeasurementAction.Submit)
                runCurrent()
                assertNull(vm.state.value.confirmation)
                assertTrue(repo.enqueued.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }
        val repo = FakeMeasurementRepository()
        val vm = viewModel(repo, approvedProfile())
        vm.state.test {
            awaitState { !it.isLoadingProfile }
            vm.onAction(MeasurementAction.WeightChanged("90"))
            vm.onAction(MeasurementAction.HeightChanged("170"))
            vm.onAction(MeasurementAction.Submit)
            runCurrent()
            assertNull(vm.state.value.confirmation)
            assertTrue(repo.enqueued.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `back dismisses the modal without navigation or saving`() = runTest(dispatcher) {
        val repo = FakeMeasurementRepository()
        val vm = viewModel(repo, approvedProfile())
        vm.state.test {
            awaitState { !it.isLoadingProfile }
            vm.fill()
            vm.onAction(MeasurementAction.Submit)
            awaitState { it.confirmation != null }
            vm.onAction(MeasurementAction.BackClick)
            val corrected = awaitState { it.confirmation == null }
            assertNull(corrected.destination)
            assertEquals("90", corrected.weight.value)
            assertEquals("170", corrected.height.value)
            assertTrue(repo.enqueued.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `normal submit is guarded before save coroutine runs`() = runTest(dispatcher) {
        val repo = FakeMeasurementRepository()
        val vm = viewModel(repo, approvedProfile())
        vm.state.test {
            awaitState { !it.isLoadingProfile }
            vm.fill("70")
            vm.onAction(MeasurementAction.Submit)
            vm.onAction(MeasurementAction.Submit)
            val done = awaitState { it.successMessage != null }
            assertNull(done.confirmation)
            assertEquals(1, repo.persisted.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
    @Test
    fun `optional observation saves on normal measurements and clears after success`() = runTest(dispatcher) {
        val repo = FakeMeasurementRepository()
        val vm = viewModel(repo, approvedProfile())
        vm.state.test {
            awaitState { !it.isLoadingProfile }
            vm.fill("70")
            vm.onAction(MeasurementAction.ObservationChanged("  Sin calzado.\nMedición repetida.  "))
            vm.onAction(MeasurementAction.Submit)
            val saved = awaitState { it.successMessage != null }
            assertEquals("Sin calzado.\nMedición repetida.", repo.persisted.single().observation)
            assertEquals("", saved.observation.value)
            assertNull(saved.confirmation)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `editing observation invalidates confirmation and stale confirm cannot save`() = runTest(dispatcher) {
        val repo = FakeMeasurementRepository()
        val vm = viewModel(repo, approvedProfile())
        vm.state.test {
            awaitState { !it.isLoadingProfile }
            vm.fill()
            vm.onAction(MeasurementAction.ObservationChanged("Nota inicial"))
            vm.onAction(MeasurementAction.Submit)
            val original = assertNotNull(awaitState { it.confirmation != null }.confirmation)
            vm.onAction(MeasurementAction.ObservationChanged("Nota corregida"))
            awaitState { it.confirmation == null }
            vm.onAction(MeasurementAction.ConfirmMeasurement(original.token))
            runCurrent()
            assertTrue(repo.enqueued.isEmpty())
            vm.onAction(MeasurementAction.Submit)
            val updated = assertNotNull(awaitState { it.confirmation != null }.confirmation)
            assertTrue(updated.token != original.token)
            vm.onAction(MeasurementAction.DismissConfirmation(updated.token))
            val dismissed = awaitState { it.confirmation == null }
            assertEquals("Nota corregida", dismissed.observation.value)
            vm.onAction(MeasurementAction.Submit)
            val confirmation = assertNotNull(awaitState { it.confirmation != null }.confirmation)
            vm.onAction(MeasurementAction.ConfirmMeasurement(confirmation.token))
            awaitState { it.successMessage != null }
            assertEquals("Nota corregida", repo.persisted.single().observation)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observation validation follows blur and blocks oversized submit`() = runTest(dispatcher) {
        val repo = FakeMeasurementRepository()
        val vm = viewModel(repo, approvedProfile())
        vm.state.test {
            awaitState { !it.isLoadingProfile }
            vm.fill("70")
            vm.onAction(MeasurementAction.ObservationFocusChanged(true))
            vm.onAction(MeasurementAction.ObservationChanged("a".repeat(501)))
            val editing = awaitState { it.observation.value.length == 501 }
            assertNull(editing.observation.displayError)
            vm.onAction(MeasurementAction.ObservationFocusChanged(false))
            assertEquals("Máximo 500 caracteres.", awaitState { it.observation.displayError != null }.observation.displayError)
            vm.onAction(MeasurementAction.Submit)
            runCurrent()
            assertTrue(repo.enqueued.isEmpty())
            assertNull(vm.state.value.confirmation)
            vm.onAction(MeasurementAction.ObservationChanged("a".repeat(500)))
            vm.onAction(MeasurementAction.Submit)
            awaitState { it.successMessage != null }
            assertEquals(500, repo.persisted.single().observation?.length)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `defaults to Argentina current minute and resets from fresh clock after save`() = runTest(dispatcher) {
        clock = Instant.parse("2026-10-17T00:30:45Z")
        val repo = FakeMeasurementRepository()
        val vm = viewModel(repo, approvedProfile())
        vm.state.test {
            awaitState { !it.isLoadingProfile }
            assertEquals(LocalDateTime(2026, 10, 16, 21, 30), vm.state.value.measuredDateTime)
            clock = Instant.parse("2026-10-18T04:10:59Z")
            vm.fill("70")
            vm.onAction(MeasurementAction.Submit)
            awaitState { it.successMessage != null }
            assertEquals(Instant.parse("2026-10-17T00:30:00Z"), repo.persisted.single().measuredAt)
            assertEquals(LocalDateTime(2026, 10, 18, 1, 10), vm.state.value.measuredDateTime)
            assertNull(vm.state.value.measurementDateError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `past date and time become exact UTC instant while preserving other component`() = runTest(dispatcher) {
        val repo = FakeMeasurementRepository()
        val vm = viewModel(repo, approvedProfile())
        vm.state.test {
            awaitState { !it.isLoadingProfile }
            val originalTime = vm.state.value.measuredDateTime!!.time
            vm.onAction(MeasurementAction.MeasurementDateSelected(LocalDate(2024, 5, 2)))
            awaitState { it.measuredDateTime?.date == LocalDate(2024, 5, 2) }
            assertEquals(originalTime, vm.state.value.measuredDateTime!!.time)
            vm.onAction(MeasurementAction.MeasurementTimeSelected(LocalTime(10, 45)))
            awaitState { it.measuredDateTime?.time == LocalTime(10, 45) }
            assertEquals(LocalDate(2024, 5, 2), vm.state.value.measuredDateTime!!.date)
            vm.fill("70")
            vm.onAction(MeasurementAction.Submit)
            awaitState { it.successMessage != null }
            assertEquals(Instant.parse("2024-05-02T13:45:00Z"), repo.persisted.single().measuredAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `future day including forged future year cannot save and later time today can`() = runTest(dispatcher) {
        clock = Instant.parse("2026-10-17T00:30:00Z")
        val repo = FakeMeasurementRepository()
        val vm = viewModel(repo, approvedProfile())
        vm.state.test {
            awaitState { !it.isLoadingProfile }
            vm.fill("70")
            for (future in listOf(LocalDate(2026, 10, 17), LocalDate(2027, 1, 1))) {
                vm.onAction(MeasurementAction.MeasurementDateSelected(future))
                awaitState { it.measuredDateTime?.date == future && it.measurementDateError != null }
                assertFalse(vm.state.value.canSubmit)
                vm.onAction(MeasurementAction.MeasurementTimeSelected(LocalTime(23, 59)))
                vm.onAction(MeasurementAction.Submit)
                runCurrent()
                assertNotNull(vm.state.value.measurementDateError)
                assertTrue(repo.enqueued.isEmpty())
                assertNull(vm.state.value.confirmation)
                vm.onAction(MeasurementAction.MeasurementDatePickerOpen)
                awaitState { it.showMeasurementDatePicker }
                assertEquals(LocalDate(2026, 10, 16), vm.state.value.latestAllowedMeasurementDate)
                vm.onAction(MeasurementAction.MeasurementDatePickerDismiss)
            }
            vm.onAction(MeasurementAction.MeasurementDateSelected(LocalDate(2026, 10, 16)))
            awaitState { it.measurementDateError == null && !it.showMeasurementDatePicker }
            vm.onAction(MeasurementAction.Submit)
            awaitState { it.successMessage != null }
            assertEquals(Instant.parse("2026-10-17T02:59:00Z"), repo.persisted.single().measuredAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `calendar bound refreshes with Argentina day and submit revalidates after clock rollback`() = runTest(dispatcher) {
        clock = Instant.parse("2026-10-17T02:59:00Z")
        val repo = FakeMeasurementRepository()
        val vm = viewModel(repo, approvedProfile())
        vm.state.test {
            awaitState { !it.isLoadingProfile }
            clock = Instant.parse("2026-10-17T03:01:00Z")
            vm.onAction(MeasurementAction.MeasurementDatePickerOpen)
            awaitState { it.showMeasurementDatePicker }
            assertEquals(LocalDate(2026, 10, 17), vm.state.value.latestAllowedMeasurementDate)
            vm.onAction(MeasurementAction.MeasurementDateSelected(LocalDate(2026, 10, 17)))
            awaitState { !it.showMeasurementDatePicker }
            vm.fill("70")
            clock = Instant.parse("2026-10-17T02:59:00Z")
            vm.onAction(MeasurementAction.Submit)
            awaitState { it.measurementDateError != null }
            assertTrue(repo.enqueued.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `picker cancellation and back preserve selection and block submit while open`() = runTest(dispatcher) {
        val repo = FakeMeasurementRepository()
        val vm = viewModel(repo, approvedProfile())
        vm.state.test {
            awaitState { !it.isLoadingProfile }
            vm.fill("70")
            val selected = vm.state.value.measuredDateTime
            for ((open, dismiss) in listOf(
                MeasurementAction.MeasurementDatePickerOpen to MeasurementAction.MeasurementDatePickerDismiss,
                MeasurementAction.MeasurementTimePickerOpen to MeasurementAction.MeasurementTimePickerDismiss,
            )) {
                vm.onAction(open)
                awaitState { it.showMeasurementDatePicker || it.showMeasurementTimePicker }
                assertFalse(vm.state.value.canSubmit)
                vm.onAction(MeasurementAction.Submit)
                runCurrent()
                assertTrue(repo.enqueued.isEmpty())
                vm.onAction(dismiss)
                awaitState { !it.showMeasurementDatePicker && !it.showMeasurementTimePicker }
                assertEquals(selected, vm.state.value.measuredDateTime)
                vm.onAction(open)
                awaitState { it.showMeasurementDatePicker || it.showMeasurementTimePicker }
                vm.onAction(MeasurementAction.BackClick)
                awaitState { !it.showMeasurementDatePicker && !it.showMeasurementTimePicker }
                assertNull(vm.state.value.destination)
                assertEquals(selected, vm.state.value.measuredDateTime)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failed enqueue retry keeps selected timestamp but date or time edit invalidates draft`() = runTest(dispatcher) {
        for (edit in listOf<MeasurementAction>(
            MeasurementAction.MeasurementDateSelected(LocalDate(2024, 5, 3)),
            MeasurementAction.MeasurementTimeSelected(LocalTime(11, 30)),
        )) {
            val repo = FakeMeasurementRepository().apply { enqueueResult = Result.failure(IllegalStateException("disk")) }
            val vm = viewModel(repo, approvedProfile())
            vm.state.test {
                awaitState { !it.isLoadingProfile }
                vm.onAction(MeasurementAction.MeasurementDateSelected(LocalDate(2024, 5, 2)))
                vm.onAction(MeasurementAction.MeasurementTimeSelected(LocalTime(10, 45)))
                vm.fill()
                vm.onAction(MeasurementAction.Submit)
                val original = assertNotNull(awaitState { it.confirmation != null }.confirmation)
                vm.onAction(MeasurementAction.ConfirmMeasurement(original.token))
                awaitState { it.submitError != null }
                clock = Instant.parse("2026-10-19T12:00:00Z")
                vm.onAction(MeasurementAction.MeasurementDatePickerOpen)
                awaitState { it.showMeasurementDatePicker }
                vm.onAction(MeasurementAction.MeasurementDatePickerDismiss)
                vm.onAction(MeasurementAction.Submit)
                val retry = assertNotNull(awaitState { it.confirmation != null }.confirmation)
                assertEquals(original, retry)
                assertEquals(Instant.parse("2024-05-02T13:45:00Z"), retry.prepared.measurement.measuredAt)
                vm.onAction(edit)
                awaitState { it.confirmation == null }
                vm.onAction(MeasurementAction.ConfirmMeasurement(original.token))
                runCurrent()
                assertEquals(1, repo.enqueued.size)
                repo.enqueueResult = Result.success(Unit)
                vm.onAction(MeasurementAction.Submit)
                val changed = assertNotNull(awaitState { it.confirmation != null }.confirmation)
                assertTrue(changed.token != original.token)
                assertTrue(changed.prepared.measurement.measuredAt != original.prepared.measurement.measuredAt)
                vm.onAction(MeasurementAction.ConfirmMeasurement(changed.token))
                awaitState { it.successMessage != null }
                assertEquals(changed.prepared.measurement.measuredAt, repo.persisted.single().measuredAt)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `historical nonexistent Argentina clock time is rejected and can be corrected`() = runTest(dispatcher) {
        val repo = FakeMeasurementRepository()
        val vm = viewModel(repo, approvedProfile())
        vm.state.test {
            awaitState { !it.isLoadingProfile }
            vm.onAction(MeasurementAction.MeasurementDateSelected(LocalDate(2008, 10, 19)))
            vm.onAction(MeasurementAction.MeasurementTimeSelected(LocalTime(0, 30)))
            vm.fill("70")
            vm.onAction(MeasurementAction.Submit)
            val rejected = awaitState { it.submitError != null }
            assertEquals("La hora elegida no existe en esa fecha en Argentina. Elegí otra hora.", rejected.submitError)
            assertTrue(repo.enqueued.isEmpty())
            vm.onAction(MeasurementAction.MeasurementTimeSelected(LocalTime(1, 30)))
            vm.onAction(MeasurementAction.Submit)
            awaitState { it.successMessage != null }
            assertEquals(Instant.parse("2008-10-19T03:30:00Z"), repo.persisted.single().measuredAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

}
