package ar.com.ingenieriaalimentacion.presentation.feature.subject.uploads

import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.di.AppScope
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryPage
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.usecase.subject.DeleteSubjectUploadUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.GetSubjectHistoryPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.ObserveSubjectUploadsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.RetrySubjectUploadUseCase
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.FakeSubjectHistoryRepository
import ar.com.ingenieriaalimentacion.fake.FakeSubjectRepository
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
class SubjectUploadsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun entry(localId: String, syncState: SyncState = SyncState.Pending) = SubjectHistoryEntry(
        id = localId,
        dni = 42,
        sex = Sex.Female,
        name = "Ana",
        lastName = "García",
        syncState = syncState,
        canonicalId = null,
        errorMessage = null,
        isLocal = true,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun viewModel(
        repo: FakeSubjectRepository,
        historyRepo: FakeSubjectHistoryRepository = FakeSubjectHistoryRepository(),
    ): SubjectUploadsViewModel {
        val coordinator = SyncCoordinator(listOf(repo), FakeConnectivityObserver(), AppScope())
        return SubjectUploadsViewModel(
            observeUploads = ObserveSubjectUploadsUseCase(historyRepo),
            getPage = GetSubjectHistoryPageUseCase(historyRepo),
            retryUpload = RetrySubjectUploadUseCase(repo),
            deleteUpload = DeleteSubjectUploadUseCase(repo),
            syncCoordinator = coordinator,
        )
    }

    @Test
    fun `initial state is loading then maps the first emission newest-first`() = runTest(dispatcher) {
        val repo = FakeSubjectRepository()
        val historyRepo = FakeSubjectHistoryRepository()
        historyRepo.merged.value = listOf(
            entry("newest", SyncState.Failed).copy(updatedAt = 2L),
            entry("oldest", SyncState.Synced).copy(updatedAt = 1L),
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
        val repo = FakeSubjectRepository()
        val vm = viewModel(repo)

        vm.onAction(SubjectUploadsAction.Retry("s1"))
        advanceUntilIdle()

        assertEquals(listOf("s1"), repo.retried)
    }

    @Test
    fun `delete delegates to repository`() = runTest(dispatcher) {
        val repo = FakeSubjectRepository()
        val vm = viewModel(repo)

        vm.onAction(SubjectUploadsAction.Delete("s2"))
        advanceUntilIdle()

        assertEquals(listOf("s2"), repo.deleted)
    }

    @Test
    fun `LoadMore appends without duplicating ids already present from the reactive floor`() = runTest(dispatcher) {
        val repo = FakeSubjectRepository()
        val historyRepo = FakeSubjectHistoryRepository()
        historyRepo.merged.value = listOf(entry("floor-1", SyncState.Synced))
        historyRepo.pageResultsByPage[1] = Result.success(
            SubjectHistoryPage(items = listOf(entry("floor-1", SyncState.Synced)), page = 1, totalPages = 2),
        )
        historyRepo.pageResultsByPage[2] = Result.success(
            SubjectHistoryPage(
                items = listOf(entry("floor-1", SyncState.Synced), entry("page2-1", SyncState.Synced)),
                page = 2,
                totalPages = 2,
            ),
        )
        val vm = viewModel(repo, historyRepo)
        // Keep `state` actively collected (WhileSubscribed) for the duration of the test.
        val collectorJob = launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.onAction(SubjectUploadsAction.LoadMore)
        advanceUntilIdle()

        val ids = vm.state.value.uploads.map { it.localId }
        assertEquals(listOf("floor-1", "page2-1"), ids)
        collectorJob.cancel()
    }
}
