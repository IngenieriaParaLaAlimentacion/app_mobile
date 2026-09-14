package ar.com.ingenieriaalimentacion.presentation.feature.food.food_movement_history

import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovement

/**
 * Unlike the other three sections this state carries an [error].
 *
 * [error] backs the recoverable error state: it is set only when the repository could serve
 * **nothing at all** (the request failed AND the cache is empty), so a user with cached rows never
 * sees an error banner over usable content.
 *
 * [items] is rendered as-is. There is no cutoff and no derived "visible" subset: the server merges
 * the two directions, so every row the cache holds is already in its final position.
 */
data class FoodMovementHistoryState(
    val items: List<FoodMovement> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val error: FoodMovementHistoryError? = null,
) {
    val canLoadMore: Boolean get() = hasMore && !isLoading && !isLoadingMore && error == null
    val isEmpty: Boolean get() = !isLoading && error == null && items.isEmpty()

    /** Error *over* content → inline banner; error *instead of* content → full-screen retry. */
    val showsBlockingError: Boolean get() = error != null && items.isEmpty()
}

/**
 * Recoverable load failure. Deliberately a tiny sealed type rather than a raw `Throwable` in state:
 * the screen renders copy, not exceptions, and this is the seam a future shared
 * `HistoryLoadError` would slot into when the pattern is lifted to the other three sections.
 */
sealed interface FoodMovementHistoryError {
    /** Network/offline/5xx — the retry button is meaningful. */
    data object Retryable : FoodMovementHistoryError
}
