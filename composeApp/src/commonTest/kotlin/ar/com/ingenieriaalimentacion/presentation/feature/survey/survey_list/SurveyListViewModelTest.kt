package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_list

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.domain.model.survey.Survey
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyScope
import ar.com.ingenieriaalimentacion.domain.repository.PagedSurveys
import ar.com.ingenieriaalimentacion.domain.usecase.survey.GetAvailableSurveysUseCase
import ar.com.ingenieriaalimentacion.fake.FakeSurveyRepository
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SurveyListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val survey1 = Survey(id = "s1", name = "Survey One", scope = SurveyScope.Generic, isActive = true, questionCount = 3)

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
}
