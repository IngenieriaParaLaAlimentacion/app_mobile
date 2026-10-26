package ar.com.ingenieriaalimentacion.presentation.feature.food.recipes

import androidx.lifecycle.SavedStateHandle
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.recipe.*
import ar.com.ingenieriaalimentacion.domain.repository.RecipeRepository
import ar.com.ingenieriaalimentacion.domain.usecase.profile.ResolveCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.FakeIdGenerator
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
import kotlin.test.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class RecipesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val establishment =
        EstablishmentWithStatus(
            "e-1",
            "Comedor",
            "Calle 1",
            0.0,
            0.0,
            "place",
            "Ciudad",
            "Provincia",
            EstablishmentStatus.Approved,
        )
    private val proposal =
        RecipeProposal(
            "p-1",
            "Arroz",
            30,
            emptyList(),
            listOf("Hervir."),
            mapOf("hierro" to 50.0),
            emptyMap(),
            emptyList(),
            listOf(RecipeShortfall("stock_insufficient", "energy", 200.0, "kcal")),
            emptyList(),
            500.0,
            300.0,
            null,
        )
    private val request =
        RecipeRequest("r-1", "e-1", 30, "lunch", "2026-10-17T10:00:00Z", listOf(proposal), null)

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest fun teardown() = Dispatchers.resetMain()

    private class FakeRecipes(val request: RecipeRequest) : RecipeRepository {
        val generations = mutableListOf<Triple<String, Int, MealType>>()
        val choices = mutableListOf<Pair<String, String>>()
        val reuses = mutableListOf<String>()
        val histories = mutableListOf<Int>()
        var previews = 0
        var generateError: RecipeError? = null
        var choiceError: RecipeError? = null
        var reuseError: RecipeError? = null
        var secondPageError = false
        var historyGate: CompletableDeferred<Unit>? = null

        override suspend fun generate(
            establishmentId: String,
            childrenCount: Int,
            mealType: MealType,
        ): Result<RecipeRequest> {
            generations += Triple(establishmentId, childrenCount, mealType)
            return generateError?.let { Result.failure(RecipeFailure(it)) }
                ?: Result.success(request)
        }

        override suspend fun history(
            establishmentId: String,
            page: Int,
        ): Result<RecipeHistoryPage> {
            histories += page
            historyGate?.await()
            if (page == 2 && secondPageError)
                return Result.failure(RecipeFailure(RecipeError.Network))
            return Result.success(
                RecipeHistoryPage(
                    listOf(request.copy(selection = RecipeSelection("p-1", "out-1"))),
                    page == 1,
                )
            )
        }

        override suspend fun choose(
            requestId: String,
            proposalId: String,
        ): Result<RecipeSelection> {
            choices += requestId to proposalId
            return choiceError?.let { Result.failure(RecipeFailure(it)) }
                ?: Result.success(RecipeSelection(proposalId, "out-1"))
        }

        override suspend fun previewReuse(
            requestId: String,
            proposalId: String,
            childrenCount: Int,
        ): Result<RecipeProposal> {
            previews++
            return Result.success(request.proposals.first().copy(portions = childrenCount))
        }

        override suspend fun reuse(
            requestId: String,
            proposalId: String,
            id: String,
            childrenCount: Int,
        ): Result<RecipeRequest> {
            reuses += id
            return reuseError?.let { Result.failure(RecipeFailure(it)) }
                ?: Result.success(
                    request.copy(
                        id = id,
                        childrenCount = childrenCount,
                        selection = RecipeSelection("p-1", "out-2"),
                    )
                )
        }
    }

    private fun viewModel(
        repo: FakeRecipes,
        online: Boolean = true,
        currentId: String? = "e-1",
        saved: SavedStateHandle = SavedStateHandle(),
    ) =
        RecipesViewModel(
            repo,
            ResolveCurrentEstablishmentUseCase(
                FakeProfileRepository().apply {
                    myEstablishmentsResult = Result.success(listOf(establishment))
                    currentEstablishmentId = currentId
                }
            ),
            FakeConnectivityObserver(online),
            FakeIdGenerator(),
            saved,
        )

    private fun RecipesViewModel.fill() {
        onAction(RecipesAction.ChildrenChanged("30"))
        onAction(RecipesAction.MealSelected(MealType.Lunch))
    }

    @Test
    fun `generation sends fixed scope and preserves shortfalls`() =
        runTest(dispatcher) {
            val repo = FakeRecipes(request)
            val vm = viewModel(repo)
            advanceUntilIdle()
            vm.fill()
            vm.onAction(RecipesAction.Generate)
            advanceUntilIdle()
            assertEquals(listOf(Triple("e-1", 30, MealType.Lunch)), repo.generations)
            assertEquals(
                proposal.unmetConstraints,
                vm.state.value.request?.proposals?.first()?.unmetConstraints,
            )
            assertTrue(repo.choices.isEmpty())
        }

    @Test
    fun `viewing a proposal and going back never mutates stock or loses results`() = runTest(dispatcher) {
        val repo = FakeRecipes(request)
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.fill()
        vm.onAction(RecipesAction.Generate)
        advanceUntilIdle()
        assertFalse(vm.state.value.isEditingRequest)
        vm.onAction(RecipesAction.ShowProposal("missing"))
        assertNull(vm.state.value.selectedProposalId)
        vm.onAction(RecipesAction.ShowProposal("p-1"))
        assertEquals("p-1", vm.state.value.selectedProposalId)
        assertNull(vm.state.value.confirmation)
        assertTrue(repo.choices.isEmpty())
        vm.onAction(RecipesAction.BackClick)
        assertNull(vm.state.value.selectedProposalId)
        assertNull(vm.state.value.destination)
        assertEquals(request, vm.state.value.request)
        vm.onAction(RecipesAction.EditRequest)
        vm.onAction(RecipesAction.ChildrenChanged("45"))
        assertTrue(vm.state.value.isEditingRequest)
        assertEquals(30, vm.state.value.request?.childrenCount)
        assertTrue(repo.choices.isEmpty())
    }

    @Test
    fun `no approved selection or no connection prevents generation`() =
        runTest(dispatcher) {
            for ((online, currentId) in listOf(false to "e-1", true to null)) {
                val repo = FakeRecipes(request)
                val vm = viewModel(repo, online, currentId)
                advanceUntilIdle()
                vm.fill()
                vm.onAction(RecipesAction.Generate)
                advanceUntilIdle()
                assertTrue(repo.generations.isEmpty())
            }
        }

    @Test
    fun `invalid count never reaches repository`() =
        runTest(dispatcher) {
            val repo = FakeRecipes(request)
            val vm = viewModel(repo)
            advanceUntilIdle()
            for (count in listOf("", "0", "-1", "10001", "abc", "2.5")) {
                vm.onAction(RecipesAction.ChildrenChanged(count))
                vm.onAction(RecipesAction.Generate)
            }
            advanceUntilIdle()
            assertTrue(repo.generations.isEmpty())
        }

    @Test
    fun `model failure keeps input for explicit retry`() =
        runTest(dispatcher) {
            val repo = FakeRecipes(request).apply { generateError = RecipeError.GenerationFailed }
            val vm = viewModel(repo)
            advanceUntilIdle()
            vm.fill()
            vm.onAction(RecipesAction.Generate)
            advanceUntilIdle()
            assertEquals("30", vm.state.value.children.value)
            assertEquals(RecipeError.GenerationFailed, vm.state.value.screenError)
            repo.generateError = null
            vm.onAction(RecipesAction.Generate)
            advanceUntilIdle()
            assertEquals(2, repo.generations.size)
        }

    @Test
    fun `choose requires confirmation and double taps spend once`() =
        runTest(dispatcher) {
            val repo = FakeRecipes(request)
            val vm = viewModel(repo)
            advanceUntilIdle()
            vm.fill()
            vm.onAction(RecipesAction.Generate)
            advanceUntilIdle()
            vm.onAction(RecipesAction.Choose("p-1"))
            assertTrue(repo.choices.isEmpty())
            assertNotNull(vm.state.value.confirmation)
            vm.onAction(RecipesAction.Confirm)
            vm.onAction(RecipesAction.Confirm)
            advanceUntilIdle()
            assertEquals(1, repo.choices.size)
            assertNotNull(vm.state.value.request?.selection)
            assertEquals(1, vm.state.value.mutationVersion)
        }

    @Test
    fun `stock conflict never announces success`() =
        runTest(dispatcher) {
            val repo =
                FakeRecipes(request).apply {
                    choiceError = RecipeError.InsufficientStock("rice", "2", "1")
                }
            val vm = viewModel(repo)
            advanceUntilIdle()
            vm.fill()
            vm.onAction(RecipesAction.Generate)
            advanceUntilIdle()
            vm.onAction(RecipesAction.Choose("p-1"))
            vm.onAction(RecipesAction.Confirm)
            advanceUntilIdle()
            assertNull(vm.state.value.request?.selection)
            assertNull(vm.state.value.successMessage)
            assertEquals(0, vm.state.value.mutationVersion)
            assertNull(vm.state.value.pendingMutation)
        }

    @Test
    fun `uncertain choice retries same ids and locks other actions`() =
        runTest(dispatcher) {
            val repo = FakeRecipes(request).apply { choiceError = RecipeError.Network }
            val vm = viewModel(repo)
            advanceUntilIdle()
            vm.fill()
            vm.onAction(RecipesAction.Generate)
            advanceUntilIdle()
            vm.onAction(RecipesAction.Choose("p-1"))
            vm.onAction(RecipesAction.Confirm)
            advanceUntilIdle()
            assertNotNull(vm.state.value.pendingMutation)
            vm.onAction(RecipesAction.Generate)
            assertEquals(1, repo.generations.size)
            repo.choiceError = null
            vm.onAction(RecipesAction.RetryConfirmation)
            advanceUntilIdle()
            assertEquals(listOf("r-1" to "p-1", "r-1" to "p-1"), repo.choices)
            assertNull(vm.state.value.pendingMutation)
        }

    @Test
    fun `reuse previews on server then preserves id across recreation and retry`() =
        runTest(dispatcher) {
            val repo = FakeRecipes(request)
            val saved = SavedStateHandle()
            val vm = viewModel(repo, saved = saved)
            advanceUntilIdle()
            vm.onAction(RecipesAction.TabSelected(RecipesTab.History))
            advanceUntilIdle()
            vm.onAction(RecipesAction.ShowRecipe("r-1", "p-1"))
            vm.onAction(RecipesAction.ReuseChildrenChanged("15"))
            vm.onAction(RecipesAction.PreviewReuse)
            advanceUntilIdle()
            assertEquals(15, vm.state.value.reusePreview?.portions)
            assertTrue(repo.reuses.isEmpty())
            vm.onAction(RecipesAction.ConfirmReuse)
            repo.reuseError = RecipeError.Network
            vm.onAction(RecipesAction.Confirm)
            advanceUntilIdle()
            val originalId = repo.reuses.single()
            val restored = viewModel(repo, saved = saved)
            advanceUntilIdle()
            assertEquals(originalId, restored.state.value.pendingMutation?.reuseId)
            repo.reuseError = null
            restored.onAction(RecipesAction.RetryConfirmation)
            advanceUntilIdle()
            assertEquals(listOf(originalId, originalId), repo.reuses)
            assertNull(saved.get<String>("recipe_pending_request"))
            assertTrue(repo.generations.isEmpty())
        }

    @Test
    fun `changing reuse count invalidates preview`() =
        runTest(dispatcher) {
            val repo = FakeRecipes(request)
            val vm = viewModel(repo)
            advanceUntilIdle()
            vm.onAction(RecipesAction.TabSelected(RecipesTab.History))
            advanceUntilIdle()
            vm.onAction(RecipesAction.ShowRecipe("r-1", "p-1"))
            vm.onAction(RecipesAction.PreviewReuse)
            advanceUntilIdle()
            assertNotNull(vm.state.value.reusePreview)
            vm.onAction(RecipesAction.ReuseChildrenChanged("16"))
            vm.onAction(RecipesAction.ConfirmReuse)
            assertNull(vm.state.value.reusePreview)
            assertNull(vm.state.value.confirmation)
        }

    @Test
    fun `pagination failure retains history and retries the same page`() =
        runTest(dispatcher) {
            val repo = FakeRecipes(request).apply { secondPageError = true }
            val vm = viewModel(repo)
            advanceUntilIdle()
            vm.onAction(RecipesAction.TabSelected(RecipesTab.History))
            advanceUntilIdle()
            vm.onAction(RecipesAction.LoadMoreHistory)
            advanceUntilIdle()
            assertEquals(1, vm.state.value.history.size)
            assertEquals(1, vm.state.value.historyPage)
            repo.secondPageError = false
            vm.onAction(RecipesAction.LoadMoreHistory)
            advanceUntilIdle()
            assertEquals(listOf(1, 2, 2), repo.histories)
        }

    @Test
    fun `choice cancels stale history and reloads after success`() =
        runTest(dispatcher) {
            val repo = FakeRecipes(request)
            val vm = viewModel(repo)
            advanceUntilIdle()
            vm.fill()
            vm.onAction(RecipesAction.Generate)
            advanceUntilIdle()
            repo.historyGate = CompletableDeferred()
            vm.onAction(RecipesAction.TabSelected(RecipesTab.History))
            runCurrent()
            assertTrue(vm.state.value.isHistoryLoading)
            vm.onAction(RecipesAction.TabSelected(RecipesTab.Generate))
            vm.onAction(RecipesAction.Choose("p-1"))
            repo.historyGate = null
            vm.onAction(RecipesAction.Confirm)
            advanceUntilIdle()
            assertFalse(vm.state.value.isHistoryLoading)
            assertEquals(1, vm.state.value.history.size)
            assertEquals(listOf(1, 1), repo.histories)
        }
}
