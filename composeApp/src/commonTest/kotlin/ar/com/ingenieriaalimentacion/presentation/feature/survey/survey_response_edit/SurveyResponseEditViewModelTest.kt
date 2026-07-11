package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_edit

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.di.AppScope
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyAnswerValue
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyDetail
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyOption
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyQuestion
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseDetail
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseType
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyScope
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.EditSurveyResponseUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.GetSurveyDetailUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.GetSurveyResponseDetailUseCase
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
import ar.com.ingenieriaalimentacion.fake.FakeSurveyRepository
import ar.com.ingenieriaalimentacion.fake.FakeSurveyResponseEditRepository
import ar.com.ingenieriaalimentacion.fake.FakeSurveyResponseHistoryRepository
import ar.com.ingenieriaalimentacion.presentation.feature.survey.common.SurveyAnswerFieldState
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
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

@OptIn(ExperimentalCoroutinesApi::class)
class SurveyResponseEditViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private val question = SurveyQuestion(
        id = "q1",
        text = "¿Cuál elegís?",
        responseType = SurveyResponseType.SingleChoice,
        isRequired = true,
        options = listOf(SurveyOption("opt-a", "A"), SurveyOption("opt-b", "B")),
    )

    private val surveyDetail = SurveyDetail(
        id = "sv-1",
        name = "Encuesta FIES",
        scope = SurveyScope.Generic,
        isActive = true,
        questions = listOf(question),
    )

    private val responseDetail = SurveyResponseDetail(
        id = "r1",
        surveyId = "sv-1",
        establishmentId = "est-1",
        submittedById = "user-1",
        answers = mapOf("q1" to SurveyAnswerValue.SingleChoice("opt-a")),
        eventAtMs = 1_000L,
    )

    private fun establishment(id: String) = EstablishmentWithStatus(
        id = id,
        name = "Escuela $id",
        formattedAddress = "Calle 1",
        latitude = 0.0,
        longitude = 0.0,
        placeId = "p$id",
        city = "Ciudad",
        province = "Provincia",
        status = EstablishmentStatus.Approved,
    )

    private fun viewModel(
        historyRepo: FakeSurveyResponseHistoryRepository = FakeSurveyResponseHistoryRepository().apply {
            detailResult = Result.success(responseDetail)
        },
        surveyRepo: FakeSurveyRepository = FakeSurveyRepository().apply {
            detailResult = Result.success(surveyDetail)
        },
        editRepo: FakeSurveyResponseEditRepository = FakeSurveyResponseEditRepository(),
        profileRepo: FakeProfileRepository = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(listOf(establishment("est-1")))
        },
    ): SurveyResponseEditViewModel {
        val coordinator = SyncCoordinator(listOf(editRepo), FakeConnectivityObserver(), AppScope())
        return SurveyResponseEditViewModel(
            route = Route.SurveyResponseEdit(responseId = "r1"),
            getSurveyResponseDetail = GetSurveyResponseDetailUseCase(historyRepo),
            getSurveyDetail = GetSurveyDetailUseCase(surveyRepo),
            getMyEstablishments = GetMyEstablishmentsUseCase(profileRepo),
            editSurveyResponse = EditSurveyResponseUseCase(editRepo),
            syncCoordinator = coordinator,
        )
    }

    private suspend fun ReceiveTurbine<SurveyResponseEditState>.awaitState(
        predicate: (SurveyResponseEditState) -> Boolean,
    ): SurveyResponseEditState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun `pre-fills the answer from the fetched response detail`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.state.test {
            val loaded = awaitState { !it.isLoading }
            val q1 = loaded.answers["q1"] as SurveyAnswerFieldState.SingleChoice
            assertEquals("opt-a", q1.selectedId)
            assertEquals("Encuesta FIES", loaded.surveyName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submit builds correctly-typed answers and calls enqueueEdit with a fresh event_at`() = runTest(dispatcher) {
        val editRepo = FakeSurveyResponseEditRepository()
        val vm = viewModel(editRepo = editRepo)

        vm.state.test {
            awaitState { !it.isLoading }

            vm.onAction(
                SurveyResponseEditAction.AnswerChanged(
                    "q1",
                    SurveyAnswerFieldState.SingleChoice(selectedId = "opt-b"),
                ),
            )
            awaitState { (it.answers["q1"] as? SurveyAnswerFieldState.SingleChoice)?.selectedId == "opt-b" }

            vm.onAction(SurveyResponseEditAction.Submit)
            val done = awaitState { it.destination == SurveyResponseEditDestination.Saved }
            assertEquals(SurveyResponseEditDestination.Saved, done.destination)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, editRepo.enqueued.size)
        val edit = editRepo.enqueued.first()
        assertEquals("r1", edit.responseId)
        assertEquals(SurveyAnswerValue.SingleChoice("opt-b"), edit.answers["q1"])
        // A fresh, current-clock event_at — distinct from the original response's 1_000L.
        assertEquals(true, edit.eventAtMs > responseDetail.eventAtMs)
    }
}
