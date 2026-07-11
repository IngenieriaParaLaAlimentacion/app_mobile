package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_form

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.di.AppScope
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyAnswerValue
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyDetail
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyOption
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyQuestion
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseType
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyScope
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.GetSurveyDetailUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.SubmitSurveyResponseUseCase
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
import ar.com.ingenieriaalimentacion.fake.FakeSurveyRepository
import ar.com.ingenieriaalimentacion.fake.FakeSurveyResponseRepository
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SurveyResponseFormViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private val questions = listOf(
        SurveyQuestion(
            id = "q1",
            text = "¿Cuál elegís?",
            responseType = SurveyResponseType.SingleChoice,
            isRequired = true,
            options = listOf(SurveyOption("opt-a", "A"), SurveyOption("opt-b", "B")),
        ),
        SurveyQuestion(
            id = "q2",
            text = "Comentario",
            responseType = SurveyResponseType.Text,
            isRequired = true,
            options = emptyList(),
        ),
        SurveyQuestion(
            id = "q3",
            text = "Cantidad",
            responseType = SurveyResponseType.Numeric,
            isRequired = false,
            options = emptyList(),
        ),
    )

    private val detail = SurveyDetail(
        id = "sv-1",
        name = "Encuesta FIES",
        scope = SurveyScope.Generic,
        isActive = true,
        questions = questions,
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
        surveyRepo: FakeSurveyRepository = FakeSurveyRepository().apply { detailResult = Result.success(detail) },
        responseRepo: FakeSurveyResponseRepository = FakeSurveyResponseRepository(),
        profileRepo: FakeProfileRepository = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(listOf(establishment("a")))
        },
    ): SurveyResponseFormViewModel {
        val coordinator = SyncCoordinator(listOf(responseRepo), FakeConnectivityObserver(), AppScope())
        return SurveyResponseFormViewModel(
            route = Route.SurveyResponseForm(surveyId = "sv-1"),
            getSurveyDetail = GetSurveyDetailUseCase(surveyRepo),
            getMyEstablishments = GetMyEstablishmentsUseCase(profileRepo),
            getCurrentEstablishment = GetCurrentEstablishmentUseCase(profileRepo),
            submitSurveyResponse = SubmitSurveyResponseUseCase(responseRepo),
            syncCoordinator = coordinator,
        )
    }

    private suspend fun ReceiveTurbine<SurveyResponseFormState>.awaitState(
        predicate: (SurveyResponseFormState) -> Boolean,
    ): SurveyResponseFormState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun `required-question validation blocks submit`() = runTest(dispatcher) {
        val responseRepo = FakeSurveyResponseRepository()
        val vm = viewModel(responseRepo = responseRepo)

        vm.state.test {
            awaitState { !it.isLoading && it.selectedEstablishment != null }

            vm.onAction(SurveyResponseFormAction.Submit)
            val blocked = awaitState {
                val q1 = it.answers["q1"] as? SurveyAnswerFieldState.SingleChoice
                val q2 = it.answers["q2"] as? SurveyAnswerFieldState.Text
                q1?.error != null && q2?.field?.displayError != null
            }
            assertTrue((blocked.answers["q1"] as SurveyAnswerFieldState.SingleChoice).error != null)
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(responseRepo.enqueued.isEmpty())
    }

    @Test
    fun `a valid form calls enqueue with correctly-typed answers`() = runTest(dispatcher) {
        val responseRepo = FakeSurveyResponseRepository()
        val vm = viewModel(responseRepo = responseRepo)

        vm.state.test {
            awaitState { !it.isLoading && it.selectedEstablishment != null }

            vm.onAction(
                SurveyResponseFormAction.AnswerChanged(
                    "q1",
                    SurveyAnswerFieldState.SingleChoice(selectedId = "opt-a"),
                ),
            )
            vm.onAction(
                SurveyResponseFormAction.AnswerChanged(
                    "q2",
                    SurveyAnswerFieldState.Text(field = TextFieldState(value = "hola")),
                ),
            )
            vm.onAction(
                SurveyResponseFormAction.AnswerChanged(
                    "q3",
                    SurveyAnswerFieldState.Numeric(field = TextFieldState(value = "3.5")),
                ),
            )
            awaitState { (it.answers["q3"] as? SurveyAnswerFieldState.Numeric)?.field?.value == "3.5" }

            vm.onAction(SurveyResponseFormAction.Submit)
            val done = awaitState { it.successMessage != null }
            assertEquals("Respuesta guardada", done.successMessage)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, responseRepo.enqueued.size)
        val enqueued = responseRepo.enqueued.first()
        assertEquals("sv-1", enqueued.surveyId)
        assertEquals("a", enqueued.establishmentId)
        assertEquals(SurveyAnswerValue.SingleChoice("opt-a"), enqueued.answers["q1"])
        assertEquals(SurveyAnswerValue.Text("hola"), enqueued.answers["q2"])
        assertEquals(SurveyAnswerValue.Numeric(3.5), enqueued.answers["q3"])
    }
}
