package ar.com.ingenieriaalimentacion.presentation.feature.food.food_stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.network.connectivity.ConnectivityObserver
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockError
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockFailure
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockFilters
import ar.com.ingenieriaalimentacion.domain.model.profile.CurrentEstablishmentResult
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetFoodCategoriesUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetFoodStockPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.ResolveCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.SetCurrentEstablishmentUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Online-only paginated stock listing, scoped to the current establishment.
 *
 * Two departures from `FoodMovementHistoryViewModel`, both forced by the absence of a cache:
 *
 *  1. **[FoodStockState.items] is accumulated here.** The history screen's `items` come from a
 *     cache flow that re-sorts globally on every emission, so its `loadMore` never appends. This
 *     screen has no such floor: every page is concatenated onto the previous one, in server order.
 *     There is deliberately **no client-side dedup**: the backend breaks ties on `id` in the
 *     stock ORDER BY (backend#136), which is the correct place to fix it — a client-side
 *     `distinctBy` would hide *skipped* rows while only papering over *duplicated* ones.
 *  2. **A filter or sort change cancels the in-flight job and replaces the list.** The page counter
 *     and the filter tuple must move together, or a late page from the previous filter appends rows
 *     that do not match the current one.
 */
class FoodStockViewModel(
    private val resolveCurrentEstablishment: ResolveCurrentEstablishmentUseCase,
    private val setCurrentEstablishment: SetCurrentEstablishmentUseCase,
    private val getFoodStockPage: GetFoodStockPageUseCase,
    private val getFoodCategories: GetFoodCategoriesUseCase,
    connectivity: ConnectivityObserver,
) : ViewModel() {

    private val _state = MutableStateFlow(FoodStockState())
    val state = _state.asStateFlow()

    /** The single in-flight list job. Cancelled by every reset so a stale page cannot append. */
    private var loadJob: Job? = null

    /** `LifecycleResumeEffect` fires on first composition too; `init` already resolved. */
    private var firstResumeConsumed = false

    init {
        viewModelScope.launch {
            // The Flow, not the suspending one-shot: IosConnectivityObserver.isOnline() suspends
            // until the first nw_path_monitor emission, so a one-shot check at screen entry would
            // stall the first frame on iOS. (Same comment, same reason, as CreateFoodIntakeViewModel.)
            connectivity.isOnline.collect { online -> _state.update { it.copy(isOnline = online) } }
        }
        viewModelScope.launch {
            // Best-effort: a failed category call leaves the filter empty and disabled. It must
            // never gate the list, which is the screen's actual content.
            getFoodCategories().onSuccess { categories ->
                _state.update { it.copy(categories = categories) }
            }
        }
        viewModelScope.launch { resolve(refreshAlways = true) }
    }

    fun onAction(action: FoodStockAction) {
        when (action) {
            FoodStockAction.BackClick ->
                _state.update { it.copy(destination = FoodStockDestination.Back) }

            FoodStockAction.LoadMore -> loadMore()

            FoodStockAction.RetryLoad -> retry()

            is FoodStockAction.OnlyAvailableChanged ->
                applyFilters(update = { it.copy(onlyAvailable = action.onlyAvailable) })

            is FoodStockAction.CategorySelected -> applyFilters(
                closeSheet = { it.copy(showCategorySheet = false) },
                update = { it.copy(categoryId = action.categoryId) },
            )

            is FoodStockAction.SortSelected -> applyFilters(
                closeSheet = { it.copy(showSortSheet = false) },
                update = { it.copy(sortOrder = action.sortOrder) },
            )

            FoodStockAction.SortSheetOpen -> _state.update { it.copy(showSortSheet = true) }
            FoodStockAction.SortSheetDismiss -> _state.update { it.copy(showSortSheet = false) }
            FoodStockAction.CategorySheetOpen -> _state.update { it.copy(showCategorySheet = true) }
            FoodStockAction.CategorySheetDismiss -> _state.update { it.copy(showCategorySheet = false) }

            FoodStockAction.EstablishmentPickerOpen ->
                _state.update { it.copy(showEstablishmentPicker = true) }
            FoodStockAction.EstablishmentPickerDismiss ->
                _state.update { it.copy(showEstablishmentPicker = false) }
            is FoodStockAction.EstablishmentSelected -> selectEstablishment(action.establishment)
        }
    }

    fun clearDestination() = _state.update { it.copy(destination = null) }

    /**
     * Re-read on resume — research §9.4: `CurrentEstablishmentLocalStore` is a one-shot suspend
     * read with **no Flow**, so nothing pushes a change here. The only way to change it is to go
     * back to Home and use its picker, which means ON_RESUME is precisely the moment the answer can
     * have changed. Cheap by construction: when the id is unchanged this is one SQLDelight read and
     * no network call, so the user does not pay for a re-fetch every time they switch apps.
     */
    fun onResume() {
        if (!firstResumeConsumed) {
            firstResumeConsumed = true
            return
        }
        viewModelScope.launch { resolve(refreshAlways = false) }
    }

    private suspend fun resolve(refreshAlways: Boolean) {
        val previousId = _state.value.establishment?.id
        when (val result = resolveCurrentEstablishment()) {
            is CurrentEstablishmentResult.Selected -> {
                _state.update {
                    it.copy(
                        establishment = result.establishment,
                        // Kept for the picker offered by the 403/404 surfaces: those are reached
                        // from this branch, which never opens the sheet itself, so without this
                        // the only action on that screen opens an empty sheet.
                        establishmentOptions = result.options,
                        needsEstablishment = false,
                        staleSelection = false,
                        error = null,
                    )
                }
                if (refreshAlways || result.establishment.id != previousId) refreshPage1()
            }

            is CurrentEstablishmentResult.NoneSelected -> showPicker(result.options, stale = false)
            is CurrentEstablishmentResult.StaleSelection -> showPicker(result.options, stale = true)

            // NOT the picker: offline on a cold cache it would have nothing to show either.
            is CurrentEstablishmentResult.LoadFailed -> {
                loadJob?.cancel()
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = FoodStockScreenError.EstablishmentsUnavailable,
                    )
                }
            }
        }
    }

    private fun showPicker(options: List<EstablishmentWithStatus>, stale: Boolean) {
        loadJob?.cancel()
        _state.update {
            it.copy(
                establishment = null,
                establishmentOptions = options,
                needsEstablishment = true,
                staleSelection = stale,
                // Auto-open only when there is something to pick; otherwise the surface explains.
                showEstablishmentPicker = options.isNotEmpty(),
                items = emptyList(),
                isLoading = false,
                isLoadingMore = false,
                error = null,
            )
        }
    }

    private fun selectEstablishment(establishment: EstablishmentWithStatus) {
        _state.update {
            it.copy(
                establishment = establishment,
                showEstablishmentPicker = false,
                needsEstablishment = false,
                staleSelection = false,
                error = null,
            )
        }
        viewModelScope.launch { setCurrentEstablishment(establishment) }
        refreshPage1()
    }

    private fun retry() {
        val current = _state.value
        if (current.error == FoodStockScreenError.EstablishmentsUnavailable || current.establishment == null) {
            _state.update { it.copy(error = null, isLoading = true) }
            viewModelScope.launch { resolve(refreshAlways = true) }
        } else {
            refreshPage1()
        }
    }

    /**
     * Filter/sort change: reset to page 1 and **replace**, never append. `items` is cleared eagerly
     * so a slow network cannot leave the previous filter's rows on screen under the new chip.
     */
    private fun applyFilters(
        closeSheet: (FoodStockState) -> FoodStockState = { it },
        update: (FoodStockFilters) -> FoodStockFilters,
    ) {
        val next = update(_state.value.filters)
        // Re-tapping the selected sort, the selected category, or the active segment is a no-op:
        // clearing the list and re-requesting page 1 would spin for a result already on screen.
        if (next == _state.value.filters) {
            _state.update(closeSheet)
            return
        }
        _state.update { closeSheet(it).copy(filters = next) }
        refreshPage1()
    }

    private fun refreshPage1() {
        val establishmentId = _state.value.establishment?.id ?: return
        val filters = _state.value.filters
        loadJob?.cancel()
        _state.update {
            it.copy(items = emptyList(), page = 1, hasMore = true, isLoading = true,
                isLoadingMore = false, error = null)
        }
        loadJob = viewModelScope.launch {
            getFoodStockPage(establishmentId, filters, page = 1, pageSize = PAGE_SIZE).fold(
                onSuccess = { page ->
                    _state.update {
                        // Replace, not append: this IS page 1 of a fresh filter set.
                        it.copy(items = page.items, page = 1, hasMore = page.hasMore,
                            isLoading = false, error = null)
                    }
                },
                onFailure = { t ->
                    _state.update { it.copy(isLoading = false, error = t.toScreenError()) }
                },
            )
        }
    }

    private fun loadMore() {
        val current = _state.value
        if (!current.canLoadMore) return
        val establishmentId = current.establishment?.id ?: return
        val next = current.page + 1
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            getFoodStockPage(establishmentId, current.filters, page = next, pageSize = PAGE_SIZE).fold(
                onSuccess = { page ->
                    _state.update {
                        it.copy(
                            // THE accumulation. No dedup, no re-sort: the server's order is the
                            // order, and its id tiebreaker is what keeps it stable across pages.
                            items = it.items + page.items,
                            page = next,
                            hasMore = page.hasMore,
                            isLoadingMore = false,
                        )
                    }
                },
                // A failed *additional* page is not an error state — the user still has content.
                // `hasMore` stays true so the near-end trigger retries on the next scroll.
                onFailure = { _state.update { it.copy(isLoadingMore = false) } },
            )
        }
    }

    private fun Throwable.toScreenError(): FoodStockScreenError =
        when ((this as? FoodStockFailure)?.error) {
            FoodStockError.EstablishmentAccessDenied -> FoodStockScreenError.AccessDenied
            FoodStockError.EstablishmentNotFound -> FoodStockScreenError.EstablishmentNotFound
            FoodStockError.InvalidRequest, FoodStockError.Unauthorized -> FoodStockScreenError.Terminal
            FoodStockError.Network, null -> FoodStockScreenError.Retryable
        }

    private companion object { const val PAGE_SIZE = 20 }
}
