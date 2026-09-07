package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_form

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.di.AppScope
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.survey.ResponseTargetRef
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyAnswerValue
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyDetail
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyOption
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyQuestion
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseType
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseTarget
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
        responseTarget = SurveyResponseTarget.Establishment,
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
        route: Route.SurveyResponseForm = Route.SurveyResponseForm(surveyId = "sv-1"),
    ): SurveyResponseFormViewModel {
        val coordinator = SyncCoordinator(listOf(responseRepo), FakeConnectivityObserver(), AppScope())
        return SurveyResponseFormViewModel(
            route = route,
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
            awaitState { !it.isLoading && it.target != null }

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
            awaitState { !it.isLoading && it.target != null }

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
            // An establishment target is consumed by the submit, so the form pops instead of
            // clearing itself for another interview.
            val done = awaitState { it.destination != null }
            assertEquals(SurveyResponseFormDestination.Done, done.destination)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, responseRepo.enqueued.size)
        val enqueued = responseRepo.enqueued.first()
        assertEquals("sv-1", enqueued.surveyId)
        assertEquals(ResponseTargetRef.Establishment("a", "Escuela a"), enqueued.target)
        assertEquals(SurveyAnswerValue.SingleChoice("opt-a"), enqueued.answers["q1"])
        assertEquals(SurveyAnswerValue.Text("hola"), enqueued.answers["q2"])
        assertEquals(SurveyAnswerValue.Numeric(3.5), enqueued.answers["q3"])
        assertNull(enqueued.responseNumber)
    }

    @Test
    fun `required survey blocks a blank response number`() = runTest(dispatcher) {
        val responseRepo = FakeSurveyResponseRepository()
        val vm = viewModel(
            surveyRepo = FakeSurveyRepository().apply {
                detailResult = Result.success(detail.copy(requiresResponseNumber = true))
            },
            responseRepo = responseRepo,
        )

        vm.state.test {
            awaitState { !it.isLoading && it.target != null }
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
            vm.onAction(SurveyResponseFormAction.Submit)

            val blocked = awaitState { it.responseNumber.displayError != null }
            assertEquals("Ingresá el número de respuesta.", blocked.responseNumber.displayError)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(responseRepo.enqueued.isEmpty())
    }

    @Test
    fun `a subject survey opened without a target id cannot submit`() = runTest(dispatcher) {
        val responseRepo = FakeSurveyResponseRepository()
        val vm = viewModel(
            surveyRepo = FakeSurveyRepository().apply {
                detailResult = Result.success(detail.copy(responseTarget = SurveyResponseTarget.Subject))
            },
            responseRepo = responseRepo,
            // No targetId: the picker was skipped, so there is nobody to answer for.
            route = Route.SurveyResponseForm(surveyId = "sv-1"),
        )

        vm.state.test {
            val loaded = awaitState { !it.isLoading }
            assertNull(loaded.target)
            assertFalse(loaded.canSubmit)

            vm.onAction(SurveyResponseFormAction.Submit)
            val blocked = awaitState { it.targetError != null }
            assertEquals("Elegí para quién es esta respuesta.", blocked.targetError)
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(responseRepo.enqueued.isEmpty())
    }

    @Test
    fun `a subject survey takes its target from the route arguments`() = runTest(dispatcher) {
        val vm = viewModel(
            surveyRepo = FakeSurveyRepository().apply {
                detailResult = Result.success(detail.copy(responseTarget = SurveyResponseTarget.Subject))
            },
            route = Route.SurveyResponseForm("sv-1", targetId = "subj-1", targetLabel = "Ana García"),
        )

        vm.state.test {
            val loaded = awaitState { !it.isLoading }
            assertEquals(ResponseTargetRef.Subject("subj-1", "Ana García"), loaded.target)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an anonymous survey resolves its target with no picker and no establishment lookup`() =
        runTest(dispatcher) {
            val responseRepo = FakeSurveyResponseRepository()
            val vm = viewModel(
                surveyRepo = FakeSurveyRepository().apply {
                    detailResult = Result.success(detail.copy(responseTarget = SurveyResponseTarget.Anonymous))
                },
                responseRepo = responseRepo,
                profileRepo = FakeProfileRepository().apply {
                    myEstablishmentsResult = Result.success(emptyList())
                },
            )

            vm.state.test {
                val loaded = awaitState { !it.isLoading }
                assertEquals(ResponseTargetRef.Anonymous, loaded.target)

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
                awaitState { (it.answers["q2"] as? SurveyAnswerFieldState.Text)?.field?.value == "hola" }

                vm.onAction(SurveyResponseFormAction.Submit)
                // Anonymous has no uniqueness rule, so the form stays put for the next interview.
                val done = awaitState { it.successMessage != null }
                assertEquals("Respuesta guardada", done.successMessage)
                assertNull(done.destination)
                assertEquals("", done.responseNumber.value)
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(ResponseTargetRef.Anonymous, responseRepo.enqueued.single().target)
        }

    @Test
    fun `anonymous submission increments a supplied response number`() = runTest(dispatcher) {
        val vm = viewModel(
            surveyRepo = FakeSurveyRepository().apply {
                detailResult = Result.success(
                    detail.copy(responseTarget = SurveyResponseTarget.Anonymous),
                )
            },
        )

        vm.state.test {
            awaitState { !it.isLoading }
            vm.onAction(SurveyResponseFormAction.ResponseNumberChanged("41"))
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
            vm.onAction(SurveyResponseFormAction.Submit)

            assertEquals("42", awaitState { it.successMessage != null }.responseNumber.value)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
