package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_history

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.di.AppScope
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.model.survey.ResponseConflict
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseTarget
import ar.com.ingenieriaalimentacion.domain.usecase.survey.DiscardSurveyResponseUploadUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.GetSurveyResponseHistoryPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.ObserveSurveyResponseUploadsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.RetrySurveyResponseUploadUseCase
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.FakeSurveyResponseHistoryRepository
import ar.com.ingenieriaalimentacion.fake.FakeSurveyResponseRepository
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SurveyResponseHistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun summary(
        id: String,
        syncState: SyncState = SyncState.Failed,
        conflict: ResponseConflict? = null,
        errorMessage: String? = null,
    ) = SurveyResponseSummary(
        id = id,
        surveyId = "sv-1",
        surveyName = "Encuesta FIES",
        responseTarget = SurveyResponseTarget.Establishment,
        targetLabel = "Comedor Norte",
        eventAt = "2026-01-01T00:00:00Z",
        syncState = syncState,
        isLocal = true,
        conflict = conflict,
        errorMessage = errorMessage,
    )

    private fun viewModel(
        historyRepo: FakeSurveyResponseHistoryRepository,
        responseRepo: FakeSurveyResponseRepository = FakeSurveyResponseRepository(),
    ): SurveyResponseHistoryViewModel {
        val coordinator = SyncCoordinator(listOf(responseRepo), FakeConnectivityObserver(), AppScope())
        return SurveyResponseHistoryViewModel(
            observeUploads = ObserveSurveyResponseUploadsUseCase(historyRepo),
            getHistoryPage = GetSurveyResponseHistoryPageUseCase(historyRepo),
            retrySurveyResponseUpload = RetrySurveyResponseUploadUseCase(responseRepo),
            discardSurveyResponseUpload = DiscardSurveyResponseUploadUseCase(responseRepo),
            syncCoordinator = coordinator,
        )
    }

    private suspend fun ReceiveTurbine<SurveyResponseHistoryState>.awaitState(
        predicate: (SurveyResponseHistoryState) -> Boolean,
    ): SurveyResponseHistoryState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun `discard removes the row`() = runTest(dispatcher) {
        val historyRepo = FakeSurveyResponseHistoryRepository()
        val responseRepo = FakeSurveyResponseRepository()
        historyRepo.localMerged.value = listOf(summary("r1"), summary("r2"))
        val vm = viewModel(historyRepo, responseRepo)

        vm.state.test {
            awaitState { it.items.size == 2 }

            vm.onAction(SurveyResponseHistoryAction.DiscardClick("r1"))
            // The reactive collector is the only thing that removes the row, so mirror what the
            // real queue would emit after the delete lands.
            historyRepo.localMerged.value = listOf(summary("r2"))

            val after = awaitState { it.items.size == 1 }
            assertEquals(listOf("r2"), after.items.map { row -> row.id })
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()

        assertEquals(listOf("r1"), responseRepo.deleted)
    }

    @Test
    fun `a conflicted row carries its conflict payload and is never retried`() = runTest(dispatcher) {
        val historyRepo = FakeSurveyResponseHistoryRepository()
        val responseRepo = FakeSurveyResponseRepository()
        historyRepo.localMerged.value = listOf(
            summary("r1", conflict = ResponseConflict("Ana", "2026-08-12T14:03:00Z")),
        )
        val vm = viewModel(historyRepo, responseRepo)

        vm.state.test {
            val loaded = awaitState { it.items.isNotEmpty() }
            val row = loaded.items.single()
            assertEquals(ResponseConflict("Ana", "2026-08-12T14:03:00Z"), row.conflict)
            // The row is FAILED underneath, but the UI branches on `conflict` first and offers
            // only Descartar — nothing here re-queues it.
            assertEquals(SyncState.Failed, row.syncState)
            assertNull(row.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(responseRepo.retried.isEmpty())
    }

    @Test
    fun `an ordinary failure keeps its error copy for the row to render`() = runTest(dispatcher) {
        val historyRepo = FakeSurveyResponseHistoryRepository()
        val responseRepo = FakeSurveyResponseRepository()
        historyRepo.localMerged.value = listOf(summary("r1", errorMessage = "Sin conexión. Reintentando…"))
        val vm = viewModel(historyRepo, responseRepo)

        vm.state.test {
            val loaded = awaitState { it.items.isNotEmpty() }
            assertEquals("Sin conexión. Reintentando…", loaded.items.single().errorMessage)

            vm.onAction(SurveyResponseHistoryAction.RetryUploadClick("r1"))
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()

        assertEquals(listOf("r1"), responseRepo.retried)
    }
}
