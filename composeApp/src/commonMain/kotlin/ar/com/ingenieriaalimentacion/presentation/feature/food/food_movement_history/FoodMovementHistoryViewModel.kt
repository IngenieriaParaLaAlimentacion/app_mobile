package ar.com.ingenieriaalimentacion.presentation.feature.food.food_movement_history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetFoodMovementHistoryPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.ObserveFoodMovementsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Same reactive floor as the other three sections — `items` come from the cache flow, not from the
 * page call; the page call only warms the cache and advances `page`/`hasMore`/`cutoffMillis`.
 *
 * Two departures from the survey template, both deliberate:
 *  1. **No `SyncCoordinator`.** Food movements have no offline queue, so there is nothing to sync.
 *  2. **`refreshPage1()` has an `onFailure`** that sets a recoverable [FoodMovementHistoryError].
 *     The template swallows failures silently, which is defensible when the cache always has
 *     content but leaves a first-run offline user staring at an empty state that reads as "no
 *     registraste nada" instead of "no pudimos cargar". The repository only fails when it could
 *     serve nothing at all, so this never fires over usable content.
 *
 * `items` is never appended to in [loadMore] — it is owned exclusively by the cache flow, which
 * already contains every fetched row (the repository wrote them) and re-sorts globally on every
 * emission. That removes the template's id-dedup entirely.
 */
class FoodMovementHistoryViewModel(
    private val observeFoodMovements: ObserveFoodMovementsUseCase,
    private val getHistoryPage: GetFoodMovementHistoryPageUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(FoodMovementHistoryState())
    val state = _state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), _state.value)

    init {
        viewModelScope.launch {
            observeFoodMovements().collect { items ->
                _state.update { it.copy(items = items, isLoading = false) }
            }
        }
        viewModelScope.launch { refreshPage1() }
    }

    fun onAction(action: FoodMovementHistoryAction) {
        when (action) {
            FoodMovementHistoryAction.LoadMore -> loadMore()
            FoodMovementHistoryAction.RetryLoad -> viewModelScope.launch {
                _state.update { it.copy(error = null, isLoading = it.items.isEmpty()) }
                refreshPage1()
            }
        }
    }

    private suspend fun refreshPage1() {
        getHistoryPage(page = 1, pageSize = PAGE_SIZE).fold(
            onSuccess = { page ->
                _state.update {
                    it.copy(
                        page = 1,
                        hasMore = page.hasMore,
                        cutoffMillis = page.cutoffMillis,
                        isLoading = false,
                        error = null,
                    )
                }
            },
            onFailure = {
                // Only reachable when BOTH directions failed and nothing is cached.
                _state.update {
                    it.copy(isLoading = false, error = FoodMovementHistoryError.Retryable)
                }
            },
        )
    }

    private fun loadMore() {
        val current = _state.value
        if (!current.canLoadMore) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            getHistoryPage(page = current.page + 1, pageSize = PAGE_SIZE).fold(
                onSuccess = { page ->
                    _state.update {
                        it.copy(
                            page = current.page + 1,
                            hasMore = page.hasMore,
                            // Monotonically non-increasing: a later page can only ever *reveal*
                            // rows, never hide ones already on screen.
                            cutoffMillis = minOfNullable(it.cutoffMillis, page.cutoffMillis),
                            isLoadingMore = false,
                        )
                    }
                },
                // A failed *additional* page is not an error state — the user still has content.
                // `hasMore` stays true so the near-end trigger can retry on the next scroll.
                onFailure = { _state.update { it.copy(isLoadingMore = false) } },
            )
        }
    }

    /** null ⇒ "no cut", which is the weakest constraint, so it wins over any value. */
    private fun minOfNullable(a: Long?, b: Long?): Long? =
        if (a == null || b == null) null else minOf(a, b)

    private companion object { const val PAGE_SIZE = 20 }
}
