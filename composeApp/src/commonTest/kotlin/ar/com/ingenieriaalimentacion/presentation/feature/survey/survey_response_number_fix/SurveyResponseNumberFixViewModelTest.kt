package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_number_fix

import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.di.AppScope
import ar.com.ingenieriaalimentacion.domain.usecase.survey.UpdateSurveyResponseNumberUseCase
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.FakeSurveyResponseRepository
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SurveyResponseNumberFixViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun teardown() = Dispatchers.resetMain()

    @Test
    fun `valid number updates row and navigates back`() = runTest(dispatcher) {
        val repository = FakeSurveyResponseRepository()
        val viewModel = SurveyResponseNumberFixViewModel(
            Route.SurveyResponseNumberFix("local-1", "Encuesta"),
            UpdateSurveyResponseNumberUseCase(repository),
            SyncCoordinator(listOf(repository), FakeConnectivityObserver(), AppScope()),
        )

        viewModel.onAction(SurveyResponseNumberFixAction.NumberChanged("42"))
        viewModel.onAction(SurveyResponseNumberFixAction.Submit)
        advanceUntilIdle()

        assertEquals(listOf("local-1" to 42), repository.updatedNumbers)
        assertEquals(SurveyResponseNumberFixDestination.Back, viewModel.state.value.destination)
    }

    @Test
    fun `out of range number is rejected locally`() = runTest(dispatcher) {
        val repository = FakeSurveyResponseRepository()
        val viewModel = SurveyResponseNumberFixViewModel(
            Route.SurveyResponseNumberFix("local-1", "Encuesta"),
            UpdateSurveyResponseNumberUseCase(repository),
            SyncCoordinator(listOf(repository), FakeConnectivityObserver(), AppScope()),
        )

        viewModel.onAction(SurveyResponseNumberFixAction.NumberChanged("1000000"))
        viewModel.onAction(SurveyResponseNumberFixAction.Submit)

        assertTrue(repository.updatedNumbers.isEmpty())
        assertEquals("Ingresá un número entre 1 y 999999.", viewModel.state.value.number.displayError)
    }
}
