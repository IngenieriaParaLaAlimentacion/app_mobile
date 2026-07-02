package ar.com.ingenieriaalimentacion.presentation.feature.measurement.uploads

import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.di.AppScope
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementHistoryPage
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.DeleteMeasurementUploadUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.GetMeasurementHistoryPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.ObserveMeasurementsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.RetryMeasurementUploadUseCase
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.FakeMeasurementHistoryRepository
import ar.com.ingenieriaalimentacion.fake.FakeMeasurementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MeasurementUploadsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun upload(localId: String, syncState: SyncState = SyncState.Pending) = MeasurementHistoryEntry(
        id = localId,
        subjectName = "Ana",
        subjectLastName = "García",
        subjectDni = 42,
        establishmentName = "Escuela 1",
        weightKg = 70.0,
        heightCm = 172.0,
        measuredAt = "2026-06-21T18:30:00Z",
        syncState = syncState,
        errorMessage = null,
        bmi = null,
        alerts = emptyList(),
        isLocal = true,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun viewModel(
        repo: FakeMeasurementRepository,
        historyRepo: FakeMeasurementHistoryRepository = FakeMeasurementHistoryRepository(),
    ): MeasurementUploadsViewModel {
        val coordinator = SyncCoordinator(listOf(repo), FakeConnectivityObserver(), AppScope())
        return MeasurementUploadsViewModel(
            observeUploads = ObserveMeasurementsUseCase(historyRepo),
            getPage = GetMeasurementHistoryPageUseCase(historyRepo),
            retryUpload = RetryMeasurementUploadUseCase(repo),
            deleteUpload = DeleteMeasurementUploadUseCase(repo),
            syncCoordinator = coordinator,
        )
    }

    @Test
    fun `initial state is loading then maps the first emission newest-first`() = runTest(dispatcher) {
        val repo = FakeMeasurementRepository()
        val historyRepo = FakeMeasurementHistoryRepository()
        historyRepo.merged.value = listOf(
            upload("newest", SyncState.Failed),
            upload("oldest", SyncState.Synced),
        )
        val vm = viewModel(repo, historyRepo)

        vm.state.test {
            assertTrue(awaitItem().isLoading) // initial
            val loaded = awaitItem()
            assertTrue(!loaded.isLoading)
            assertEquals(listOf("newest", "oldest"), loaded.uploads.map { it.localId })
            assertEquals("Error", loaded.uploads.first().badgeLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry delegates to repository`() = runTest(dispatcher) {
        val repo = FakeMeasurementRepository()
        val vm = viewModel(repo)

        vm.onAction(MeasurementUploadsAction.Retry("m1"))
        advanceUntilIdle()

        assertEquals(listOf("m1"), repo.retried)
    }

    @Test
    fun `delete delegates to repository`() = runTest(dispatcher) {
        val repo = FakeMeasurementRepository()
        val vm = viewModel(repo)

        vm.onAction(MeasurementUploadsAction.Delete("m2"))
        advanceUntilIdle()

        assertEquals(listOf("m2"), repo.deleted)
    }

    @Test
    fun `LoadMore appends without duplicating ids already present from the reactive floor`() = runTest(dispatcher) {
        val repo = FakeMeasurementRepository()
        val historyRepo = FakeMeasurementHistoryRepository()
        historyRepo.merged.value = listOf(upload("floor-1", SyncState.Synced))
        historyRepo.pageResultsByPage[1] = Result.success(
            MeasurementHistoryPage(items = listOf(upload("floor-1", SyncState.Synced)), page = 1, totalPages = 2),
        )
        historyRepo.pageResultsByPage[2] = Result.success(
            MeasurementHistoryPage(
                items = listOf(upload("floor-1", SyncState.Synced), upload("page2-1", SyncState.Synced)),
                page = 2,
                totalPages = 2,
            ),
        )
        val vm = viewModel(repo, historyRepo)
        // Keep `state` actively collected (WhileSubscribed) for the duration of the test.
        val collectorJob = launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.onAction(MeasurementUploadsAction.LoadMore)
        advanceUntilIdle()

        val ids = vm.state.value.uploads.map { it.localId }
        assertEquals(listOf("floor-1", "page2-1"), ids)
        collectorJob.cancel()
    }
}
