package ar.com.ingenieriaalimentacion.presentation.feature.food.food_stock

import ar.com.ingenieriaalimentacion.domain.model.food.FoodCategory
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockFilters
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockItem
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus

/**
 * Same `items / isLoading / isLoadingMore / page / hasMore / error` shape as
 * `FoodMovementHistoryState`, with one structural difference that matters: **[items] is owned by
 * this state, not by a cache flow.** The history screen renders whatever the cache emits and never
 * appends in `loadMore`; this feature is not offline-first, so there is no cache and the ViewModel
 * accumulates pages itself. See [FoodStockViewModel] for why that makes the backend's `ORDER BY`
 * tiebreaker a hard dependency.
 */
data class FoodStockState(
    val establishment: EstablishmentWithStatus? = null,
    val establishmentOptions: List<EstablishmentWithStatus> = emptyList(),
    val showEstablishmentPicker: Boolean = false,
    /** True after a `NoneSelected`/`StaleSelection` resolve: the list is not merely empty, it is
     *  unscoped, so the screen shows the "elegí un establecimiento" surface instead of a list. */
    val needsEstablishment: Boolean = false,
    val staleSelection: Boolean = false,

    val items: List<FoodStockItem> = emptyList(),
    val categories: List<FoodCategory> = emptyList(),
    val filters: FoodStockFilters = FoodStockFilters(),

    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val error: FoodStockScreenError? = null,
    val isOnline: Boolean = true,

    val showSortSheet: Boolean = false,
    val showCategorySheet: Boolean = false,
    val destination: FoodStockDestination? = null,
) {
    val canLoadMore: Boolean
        get() = hasMore && !isLoading && !isLoadingMore && error == null && establishment != null

    val isEmpty: Boolean
        get() = !isLoading && error == null && establishment != null && items.isEmpty()

    /** Error *over* content → keep the list, retry on the next scroll; error *instead of* content →
     *  full-surface retry. Same rule as the history section. */
    val showsBlockingError: Boolean get() = error != null && items.isEmpty()

    val selectedCategoryName: String?
        get() = filters.categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name }
}

/**
 * Recoverable/terminal load failures as the *screen* sees them. A tiny sealed type rather than a
 * raw `Throwable`, for the same reason `FoodMovementHistoryError` is: the screen renders copy.
 */
sealed interface FoodStockScreenError {
    /** Network / offline / 5xx on the stock call — the retry button is meaningful. */
    data object Retryable : FoodStockScreenError

    /** 403 — the relation is gone or was never approved. The action is "elegí otro establecimiento",
     *  not "reintentá". The backend sends no `relation_status` on this path, so the app cannot tell
     *  "nunca te sumaste" from "te revocaron" and the copy must cover both. */
    data object AccessDenied : FoodStockScreenError

    /** 404 — terminal; the stored establishment id points at nothing. */
    data object EstablishmentNotFound : FoodStockScreenError

    /** `getMyEstablishments()` itself failed (CurrentEstablishmentResult.LoadFailed). Retryable, and
     *  explicitly NOT the picker — offline on a cold cache the picker would be empty too. */
    data object EstablishmentsUnavailable : FoodStockScreenError

    /** 400/422/401 — nothing the user can do here. */
    data object Terminal : FoodStockScreenError
}
