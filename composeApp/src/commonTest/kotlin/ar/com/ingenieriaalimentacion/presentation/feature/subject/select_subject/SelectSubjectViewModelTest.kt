package ar.com.ingenieriaalimentacion.presentation.feature.subject.select_subject

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectPage
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary
import ar.com.ingenieriaalimentacion.domain.usecase.subject.GetSubjectsUseCase
import ar.com.ingenieriaalimentacion.fake.FakeSubjectQueryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SelectSubjectViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val ana = SubjectSummary("1", 30111222, "Ana", "Pérez", Sex.Female)
    private val luis = SubjectSummary("2", 28999888, "Luis", "Gómez", Sex.Male)
    private val marta = SubjectSummary("3", 27000111, "Marta", "Díaz", Sex.Female)

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repo: FakeSubjectQueryRepository) = SelectSubjectViewModel(
        getSubjects = GetSubjectsUseCase(repo),
    )

    private suspend fun ReceiveTurbine<SelectSubjectState>.awaitState(
        predicate: (SelectSubjectState) -> Boolean,
    ): SelectSubjectState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun `initial blank search loads the first page`() = runTest(dispatcher) {
        val repo = FakeSubjectQueryRepository().apply {
            pages = mapOf(1 to SubjectPage(items = listOf(ana, luis), page = 1, totalPages = 2))
        }
        val vm = viewModel(repo)

        vm.state.test {
            val loaded = awaitState { !it.isLoading }
            assertEquals(listOf(ana, luis), loaded.results)
            assertEquals(2, loaded.totalPages)
            assertTrue(loaded.canLoadMore)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(null, repo.searches.first())
    }

    @Test
    fun `debounce collapses keystrokes into one search`() = runTest(dispatcher) {
        val repo = FakeSubjectQueryRepository().apply {
            pages = mapOf(1 to SubjectPage(items = listOf(ana), page = 1, totalPages = 1))
        }
        val vm = viewModel(repo)

        advanceUntilIdle() // initial blank load settles
        val before = repo.callCount

        vm.onAction(SelectSubjectAction.QueryChanged("a"))
        vm.onAction(SelectSubjectAction.QueryChanged("an"))
        vm.onAction(SelectSubjectAction.QueryChanged("ana"))
        advanceUntilIdle()

        assertEquals(before + 1, repo.callCount)
        assertEquals("ana", repo.lastSearch)
    }

    @Test
    fun `load more appends the next page and respects total pages`() = runTest(dispatcher) {
        val repo = FakeSubjectQueryRepository().apply {
            pages = mapOf(
                1 to SubjectPage(items = listOf(ana, luis), page = 1, totalPages = 2),
                2 to SubjectPage(items = listOf(marta), page = 2, totalPages = 2),
            )
        }
        val vm = viewModel(repo)

        vm.state.test {
            awaitState { !it.isLoading && it.results.size == 2 }

            vm.onAction(SelectSubjectAction.LoadMore)
            val appended = awaitState { it.results.size == 3 }
            assertEquals(listOf(ana, luis, marta), appended.results)
            assertEquals(2, appended.page)
            assertFalse(appended.canLoadMore) // page == totalPages

            // No further page to fetch — LoadMore is a no-op.
            val before = repo.callCount
            vm.onAction(SelectSubjectAction.LoadMore)
            advanceUntilIdle()
            assertEquals(before, repo.callCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `subject clicked emits confirm with subject json`() = runTest(dispatcher) {
        val repo = FakeSubjectQueryRepository().apply {
            pages = mapOf(1 to SubjectPage(items = listOf(ana), page = 1, totalPages = 1))
        }
        val vm = viewModel(repo)

        vm.state.test {
            awaitState { !it.isLoading }

            vm.onAction(SelectSubjectAction.SubjectClicked(ana))
            val state = awaitState { it.destination != null }
            val destination = state.destination
            assertTrue(destination is SelectSubjectDestination.Confirm)
            assertEquals(ana, Json.decodeFromString<SubjectSummary>(destination.subjectJson))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load failure sets loadError`() = runTest(dispatcher) {
        val repo = FakeSubjectQueryRepository().apply { error = RuntimeException("boom") }
        val vm = viewModel(repo)

        vm.state.test {
            val state = awaitState { it.loadError }
            assertTrue(state.loadError)
            assertFalse(state.isLoading)
            assertFalse(state.canLoadMore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `creating from a dni search prefills the new subject form`() = runTest(dispatcher) {
        val repo = FakeSubjectQueryRepository().apply {
            pages = mapOf(1 to SubjectPage(items = emptyList(), page = 1, totalPages = 1))
        }
        val vm = viewModel(repo)

        vm.state.test {
            awaitState { !it.isLoading }
            vm.onAction(SelectSubjectAction.QueryChanged("12345678"))
            advanceUntilIdle()

            vm.onAction(SelectSubjectAction.CreateSubjectClick)
            val destination = awaitState { it.destination != null }.destination
            assertTrue(destination is SelectSubjectDestination.CreateSubject)
            assertEquals("12345678", destination.prefillDni)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `creating from a name search leaves the dni blank`() = runTest(dispatcher) {
        val repo = FakeSubjectQueryRepository().apply {
            pages = mapOf(1 to SubjectPage(items = emptyList(), page = 1, totalPages = 1))
        }
        val vm = viewModel(repo)

        vm.state.test {
            awaitState { !it.isLoading }
            vm.onAction(SelectSubjectAction.QueryChanged("perez"))
            advanceUntilIdle()

            vm.onAction(SelectSubjectAction.CreateSubjectClick)
            val destination = awaitState { it.destination != null }.destination
            assertTrue(destination is SelectSubjectDestination.CreateSubject)
            // Only a digits-only query is a DNI; a name must not be typed into the DNI field.
            assertEquals("", destination.prefillDni)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
