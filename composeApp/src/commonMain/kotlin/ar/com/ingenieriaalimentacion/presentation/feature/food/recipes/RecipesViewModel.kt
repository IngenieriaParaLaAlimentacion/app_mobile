package ar.com.ingenieriaalimentacion.presentation.feature.food.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.core.validation.Rules
import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.core.validation.onChange
import ar.com.ingenieriaalimentacion.core.validation.onFocus
import ar.com.ingenieriaalimentacion.data.network.connectivity.ConnectivityObserver
import ar.com.ingenieriaalimentacion.domain.model.profile.CurrentEstablishmentResult
import ar.com.ingenieriaalimentacion.domain.model.recipe.*
import ar.com.ingenieriaalimentacion.domain.repository.RecipeRepository
import ar.com.ingenieriaalimentacion.domain.usecase.profile.ResolveCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.util.IdGenerator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecipesViewModel(
    private val repository: RecipeRepository,
    private val resolveCurrentEstablishment: ResolveCurrentEstablishmentUseCase,
    private val connectivity: ConnectivityObserver,
    private val ids: IdGenerator,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val countValidator =
        Rules.of(
            Rules.required("Ingresá cuántos chicos van a comer."),
            Rules.digitsInRange(1..10000, "Ingresá una cantidad entre 1 y 10.000."),
        )
    private val restoredMutation = restoreMutation()
    private val _state =
        MutableStateFlow(
            RecipesState(
                pendingMutation = restoredMutation,
                screenError = if (restoredMutation != null) RecipeError.Network else null,
                children = TextFieldState(value = savedState["recipe_children"] ?: ""),
                mealType =
                    MealType.entries.firstOrNull {
                        it.wireValue == savedState.get<String>("recipe_meal")
                    } ?: MealType.Lunch,
            )
        )
    val state = _state.asStateFlow()
    private var historyJob: Job? = null
    private var historyVersion = 0

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> _state.update { it.copy(isOnline = online) } }
        }
        loadEstablishment()
    }

    fun onAction(action: RecipesAction) {
        when (action) {
            RecipesAction.BackClick ->
                if (!_state.value.isSubmitting) {
                    if (_state.value.selectedProposalId != null)
                        _state.update { it.copy(selectedProposalId = null, screenError = null) }
                    else if (_state.value.detail != null)
                        _state.update {
                            it.copy(detail = null, reusePreview = null, screenError = null)
                        }
                    else navigate(RecipesDestination.Back)
                }
            RecipesAction.RetryEstablishment -> if (!_state.value.isBusy) loadEstablishment()
            is RecipesAction.ChildrenChanged ->
                if (_state.value.canEdit) {
                    savedState["recipe_children"] = action.value
                    _state.update {
                        it.copy(
                            children = it.children.onChange(action.value, countValidator),
                            screenError = null,
                            successMessage = null,
                        )
                    }
                }
            is RecipesAction.ChildrenFocusChanged ->
                _state.update {
                    it.copy(children = it.children.onFocus(action.focused, countValidator))
                }
            is RecipesAction.MealSelected ->
                if (_state.value.canEdit) {
                    savedState["recipe_meal"] = action.meal.wireValue
                    _state.update { it.copy(mealType = action.meal) }
                }
            RecipesAction.EditRequest -> if (_state.value.canEdit) {
                _state.update { it.copy(isEditingRequest = true) }
            }
            is RecipesAction.ShowProposal -> if (!_state.value.isBusy &&
                _state.value.request?.proposals?.any { it.id == action.proposalId } == true
            ) {
                _state.update { it.copy(selectedProposalId = action.proposalId) }
            }
            RecipesAction.Generate -> generate()
            is RecipesAction.TabSelected ->
                if (!_state.value.isBusy) {
                    _state.update {
                        it.copy(
                            tab = action.tab,
                            selectedProposalId = null,
                            detail = null,
                            reusePreview = null,
                            screenError = null,
                        )
                    }
                    if (action.tab == RecipesTab.History && _state.value.historyPage == 0)
                        loadHistory(force = true)
                }
            RecipesAction.ReloadHistory -> loadHistory()
            RecipesAction.LoadMoreHistory ->
                if (_state.value.historyHasMore) loadHistory(more = true)
            is RecipesAction.ShowRecipe -> showRecipe(action.requestId, action.proposalId)
            is RecipesAction.Choose -> requestChoice(action.proposalId)
            RecipesAction.DismissConfirmation ->
                if (!_state.value.isSubmitting) _state.update { it.copy(confirmation = null) }
            RecipesAction.Confirm -> confirm()
            RecipesAction.RetryConfirmation -> _state.value.pendingMutation?.let { mutate(it) }
            is RecipesAction.ReuseChildrenChanged ->
                if (_state.value.canEdit)
                    _state.update {
                        it.copy(
                            reuseChildren = it.reuseChildren.onChange(action.value, countValidator),
                            reusePreview = null,
                            screenError = null,
                        )
                    }
            is RecipesAction.ReuseChildrenFocusChanged ->
                _state.update {
                    it.copy(
                        reuseChildren = it.reuseChildren.onFocus(action.focused, countValidator)
                    )
                }
            RecipesAction.PreviewReuse -> previewReuse()
            RecipesAction.ConfirmReuse -> requestReuse()
        }
    }

    fun clearDestination() = _state.update { it.copy(destination = null) }

    private fun navigate(destination: RecipesDestination) = _state.update {
        it.copy(destination = destination)
    }

    private fun loadEstablishment() {
        _state.update { it.copy(isLoading = true, scopeError = null) }
        viewModelScope.launch {
            when (val result = resolveCurrentEstablishment()) {
                is CurrentEstablishmentResult.Selected ->
                    _state.update {
                        it.copy(establishment = result.establishment, isLoading = false)
                    }
                is CurrentEstablishmentResult.NoneSelected,
                is CurrentEstablishmentResult.StaleSelection ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            establishment = null,
                            scopeError =
                                "Elegí un establecimiento aprobado en Inicio para pedir recetas.",
                        )
                    }
                is CurrentEstablishmentResult.LoadFailed ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            scopeError =
                                "No pudimos cargar el establecimiento. Revisá la conexión y reintentá.",
                        )
                    }
            }
        }
    }

    private fun generate() {
        val current = _state.value
        if (!current.canGenerate) return
        val establishment = current.establishment ?: return
        val count = current.children.value.toIntOrNull() ?: return
        _state.update {
            it.copy(isGenerating = true, request = null, selectedProposalId = null, screenError = null, successMessage = null)
        }
        viewModelScope.launch {
            repository
                .generate(establishment.id, count, current.mealType)
                .fold(
                    onSuccess = { result ->
                        _state.update { it.copy(isGenerating = false, request = result, isEditingRequest = false) }
                    },
                    onFailure = { failure ->
                        _state.update {
                            it.copy(isGenerating = false, screenError = failure.recipeError())
                        }
                    },
                )
        }
    }

    private fun loadHistory(more: Boolean = false, force: Boolean = false) {
        val current = _state.value
        val establishment = current.establishment ?: return
        if (!current.isOnline || current.isHistoryLoading && !force || current.isSubmitting) return
        val page = if (more) current.historyPage + 1 else 1
        val version = ++historyVersion
        historyJob?.cancel()
        _state.update { it.copy(isHistoryLoading = true, historyError = null) }
        historyJob = viewModelScope.launch {
            repository
                .history(establishment.id, page)
                .fold(
                    onSuccess = { result ->
                        if (version == historyVersion)
                            _state.update {
                                it.copy(
                                    history =
                                        if (more)
                                            (it.history + result.items).distinctBy { r -> r.id }
                                        else result.items,
                                    historyPage = page,
                                    historyHasMore = result.hasMore,
                                    isHistoryLoading = false,
                                )
                            }
                    },
                    onFailure = { failure ->
                        if (version == historyVersion)
                            _state.update {
                                it.copy(
                                    isHistoryLoading = false,
                                    historyError = failure.recipeError(),
                                )
                            }
                    },
                )
        }
    }

    private fun showRecipe(requestId: String, proposalId: String) {
        if (!_state.value.canEdit) return
        val recipe =
            _state.value.history
                .firstOrNull { it.id == requestId }
                ?.proposals
                ?.firstOrNull { it.id == proposalId } ?: return
        _state.update {
            it.copy(
                detail = RecipeDetail(requestId, recipe),
                reuseChildren = TextFieldState(value = recipe.portions.toString()),
                reusePreview = null,
                screenError = null,
                successMessage = null,
            )
        }
    }

    private fun requestChoice(proposalId: String) {
        val current = _state.value
        val request = current.request ?: return
        val establishment = current.establishment ?: return
        if (
            !current.canEdit ||
                !current.isOnline ||
                request.selection != null ||
                request.id in current.blockedRequestIds
        )
            return
        val recipe = request.proposals.firstOrNull { it.id == proposalId } ?: return
        _state.update {
            it.copy(
                confirmation =
                    RecipeConfirmation(
                        RecipeMutation(establishment.id, request.id, recipe.id),
                        recipe.name,
                        recipe.portions,
                    )
            )
        }
    }

    private fun previewReuse() {
        val current = _state.value
        if (!current.canPreviewReuse) return
        val detail = current.detail ?: return
        val count = current.reuseChildren.value.toIntOrNull() ?: return
        _state.update {
            it.copy(
                isPreviewing = true,
                reusePreview = null,
                screenError = null,
                successMessage = null,
            )
        }
        viewModelScope.launch {
            repository
                .previewReuse(detail.requestId, detail.proposal.id, count)
                .fold(
                    onSuccess = { preview ->
                        _state.update { it.copy(isPreviewing = false, reusePreview = preview) }
                    },
                    onFailure = { failure ->
                        _state.update {
                            it.copy(isPreviewing = false, screenError = failure.recipeError())
                        }
                    },
                )
        }
    }

    private fun requestReuse() {
        val current = _state.value
        val detail = current.detail ?: return
        val preview = current.reusePreview ?: return
        val establishment = current.establishment ?: return
        if (!current.canEdit || !current.isOnline) return
        val mutation =
            RecipeMutation(
                establishment.id,
                detail.requestId,
                detail.proposal.id,
                ids.newId(),
                preview.portions,
            )
        _state.update {
            it.copy(confirmation = RecipeConfirmation(mutation, preview.name, preview.portions))
        }
    }

    private fun confirm() {
        val confirmation = _state.value.confirmation ?: return
        mutate(confirmation.mutation)
    }

    private fun mutate(mutation: RecipeMutation) {
        val current = _state.value
        if (
            current.isBusy ||
                !current.isOnline ||
                current.establishment?.id != mutation.establishmentId
        )
            return
        if (current.pendingMutation != null && current.pendingMutation != mutation) return
        saveMutation(mutation)
        _state.update {
            it.copy(
                isSubmitting = true,
                pendingMutation = mutation,
                screenError = null,
                successMessage = null,
            )
        }
        viewModelScope.launch {
            val result: Result<RecipeSelection> =
                if (mutation.reuseId == null) {
                    repository.choose(mutation.requestId, mutation.proposalId)
                } else {
                    repository
                        .reuse(
                            mutation.requestId,
                            mutation.proposalId,
                            mutation.reuseId,
                            mutation.childrenCount!!,
                        )
                        .mapCatching { reused ->
                            val selection = checkNotNull(reused.selection)
                            _state.update {
                                it.copy(
                                    detail =
                                        RecipeDetail(
                                            reused.id,
                                            reused.proposals.first { p ->
                                                p.id == selection.proposalId
                                            },
                                        ),
                                    reusePreview = null,
                                )
                            }
                            selection
                        }
                }
            result.fold(
                onSuccess = { selection ->
                    clearMutation()
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            pendingMutation = null,
                            confirmation = null,
                            request =
                                it.request?.let { request ->
                                    if (
                                        request.id == mutation.requestId && mutation.reuseId == null
                                    )
                                        request.copy(selection = selection)
                                    else request
                                },
                            successMessage =
                                "Preparación registrada. Se descontaron los ingredientes del stock.",
                            mutationVersion = it.mutationVersion + 1,
                        )
                    }
                    loadHistory(force = true)
                },
                onFailure = { failure ->
                    val error = failure.recipeError()
                    val uncertain = error == RecipeError.Network
                    if (!uncertain) clearMutation()
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            confirmation = null,
                            pendingMutation = if (uncertain) mutation else null,
                            screenError = error,
                            blockedRequestIds =
                                if (error == RecipeError.AlreadyChosen)
                                    it.blockedRequestIds + mutation.requestId
                                else it.blockedRequestIds,
                        )
                    }
                },
            )
        }
    }

    private fun saveMutation(mutation: RecipeMutation) {
        savedState["recipe_pending_establishment"] = mutation.establishmentId
        savedState["recipe_pending_request"] = mutation.requestId
        savedState["recipe_pending_proposal"] = mutation.proposalId
        savedState["recipe_pending_reuse"] = mutation.reuseId
        savedState["recipe_pending_count"] = mutation.childrenCount
    }

    private fun restoreMutation(): RecipeMutation? {
        val establishment = savedState.get<String>("recipe_pending_establishment") ?: return null
        val request = savedState.get<String>("recipe_pending_request") ?: return null
        val proposal = savedState.get<String>("recipe_pending_proposal") ?: return null
        return RecipeMutation(
            establishment,
            request,
            proposal,
            savedState["recipe_pending_reuse"],
            savedState["recipe_pending_count"],
        )
    }

    private fun clearMutation() {
        listOf(
                "recipe_pending_establishment",
                "recipe_pending_request",
                "recipe_pending_proposal",
                "recipe_pending_reuse",
                "recipe_pending_count",
            )
            .forEach { savedState.remove<Any?>(it) }
    }
}

private fun Throwable.recipeError() = (this as? RecipeFailure)?.error ?: RecipeError.Network
