package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_list

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary
import ar.com.ingenieriaalimentacion.domain.model.survey.Survey
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseTarget
import ar.com.ingenieriaalimentacion.domain.repository.PagedSurveys
import ar.com.ingenieriaalimentacion.domain.usecase.survey.GetAvailableSurveysUseCase
import ar.com.ingenieriaalimentacion.fake.FakeSurveyRepository
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SurveyListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val survey1 = Survey(id = "s1", name = "Survey One", responseTarget = SurveyResponseTarget.Establishment, isActive = true, questionCount = 3)

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repo: FakeSurveyRepository) = SurveyListViewModel(
        getAvailableSurveys = GetAvailableSurveysUseCase(repo),
    )

    private suspend fun ReceiveTurbine<SurveyListState>.awaitState(
        predicate: (SurveyListState) -> Boolean,
    ): SurveyListState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun `cache-served result sets fromCache true with no load error`() = runTest(dispatcher) {
        val repo = FakeSurveyRepository().apply {
            availableResult = Result.success(PagedSurveys(items = listOf(survey1), page = 1, totalPages = 1, fromCache = true))
        }
        val vm = viewModel(repo)

        vm.state.test {
            val loaded = awaitState { !it.isLoading }
            assertTrue(loaded.fromCache)
            assertFalse(loaded.loadError)
            assertEquals(listOf(survey1), loaded.surveys)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `subsequent live success on retry clears fromCache`() = runTest(dispatcher) {
        val repo = FakeSurveyRepository().apply {
            availableResult = Result.success(PagedSurveys(items = listOf(survey1), page = 1, totalPages = 1, fromCache = true))
        }
        val vm = viewModel(repo)

        vm.state.test {
            awaitState { it.fromCache }

            repo.availableResult = Result.success(PagedSurveys(items = listOf(survey1), page = 1, totalPages = 1, fromCache = false))
            vm.onAction(SurveyListAction.RetryClick)

            val retried = awaitState { !it.fromCache && !it.isLoading }
            assertFalse(retried.fromCache)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun survey(target: SurveyResponseTarget) =
        survey1.copy(id = "s-${target.name}", responseTarget = target)

    private suspend fun ReceiveTurbine<SurveyListState>.awaitLoaded() {
        awaitState { !it.isLoading }
    }

    @Test
    fun `a subject survey routes to the subject picker and remembers which survey`() =
        runTest(dispatcher) {
            val target = survey(SurveyResponseTarget.Subject)
            val repo = FakeSurveyRepository().apply {
                availableResult = Result.success(PagedSurveys(listOf(target), page = 1, totalPages = 1))
            }
            val vm = viewModel(repo)

            vm.state.test {
                awaitLoaded()
                vm.onAction(SurveyListAction.SurveyClick(target))

                val routed = awaitState { it.destination != null }
                assertEquals(SurveyListDestination.SelectSubject, routed.destination)
                assertEquals(target.id, routed.pendingSurveyId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an establishment survey routes to the establishment picker`() = runTest(dispatcher) {
        val target = survey(SurveyResponseTarget.Establishment)
        val repo = FakeSurveyRepository().apply {
            availableResult = Result.success(PagedSurveys(listOf(target), page = 1, totalPages = 1))
        }
        val vm = viewModel(repo)

        vm.state.test {
            awaitLoaded()
            vm.onAction(SurveyListAction.SurveyClick(target))

            val routed = awaitState { it.destination != null }
            assertEquals(SurveyListDestination.SelectEstablishment, routed.destination)
            assertEquals(target.id, routed.pendingSurveyId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an anonymous survey goes straight to the form with no target`() = runTest(dispatcher) {
        val target = survey(SurveyResponseTarget.Anonymous)
        val repo = FakeSurveyRepository().apply {
            availableResult = Result.success(PagedSurveys(listOf(target), page = 1, totalPages = 1))
        }
        val vm = viewModel(repo)

        vm.state.test {
            awaitLoaded()
            vm.onAction(SurveyListAction.SurveyClick(target))

            val routed = awaitState { it.destination != null }
            assertEquals(SurveyListDestination.SurveyResponseForm(target.id), routed.destination)
            assertNull(routed.pendingSurveyId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a picked subject opens the form for the pending survey`() = runTest(dispatcher) {
        val target = survey(SurveyResponseTarget.Subject)
        val repo = FakeSurveyRepository().apply {
            availableResult = Result.success(PagedSurveys(listOf(target), page = 1, totalPages = 1))
        }
        val vm = viewModel(repo)

        vm.state.test {
            awaitLoaded()
            vm.onAction(SurveyListAction.SurveyClick(target))
            awaitState { it.destination == SurveyListDestination.SelectSubject }
            vm.clearDestination()

            vm.onAction(
                SurveyListAction.SubjectPicked(
                    SubjectSummary(id = "subj-1", dni = 42, name = "Ana", lastName = "García", sex = Sex.Female, birthDate = kotlinx.datetime.LocalDate(1990, 1, 1)),
                ),
            )

            val routed = awaitState { it.destination != null }
            assertEquals(
                SurveyListDestination.SurveyResponseForm(target.id, "subj-1", "Ana García"),
                routed.destination,
            )
            assertNull(routed.pendingSurveyId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a picker result with no pending survey is ignored`() = runTest(dispatcher) {
        val repo = FakeSurveyRepository().apply {
            availableResult = Result.success(PagedSurveys(listOf(survey1), page = 1, totalPages = 1))
        }
        val vm = viewModel(repo)

        vm.state.test {
            val loaded = awaitState { !it.isLoading }
            assertNull(loaded.pendingSurveyId)

            // A stale savedStateHandle value left over from another consumer must not open a form.
            vm.onAction(SurveyListAction.EstablishmentPicked(Establishment("est-1", "Norte", "Calle 1")))

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
