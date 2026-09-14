package ar.com.ingenieriaalimentacion.presentation.feature.food.food_movement_history

import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovement

/**
 * Unlike the other three sections this state carries an [error] and a [cutoffMillis].
 *
 * [error] backs the recoverable error state: it is set only when the repository could serve
 * **nothing at all** (both directions failed AND the cache is empty), so a user with cached rows
 * never sees an error banner over usable content.
 *
 * [cutoffMillis] is the bounded-merge watermark: rows older than it are held back so the visible
 * list stays append-only while two independently-paginated sources catch up.
 */
data class FoodMovementHistoryState(
    val items: List<FoodMovement> = emptyList(),
    val cutoffMillis: Long? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val error: FoodMovementHistoryError? = null,
) {
    /** What the list actually renders: everything at or above the watermark. */
    val visibleItems: List<FoodMovement>
        get() = cutoffMillis?.let { cutoff -> items.filter { it.occurredAtMillis >= cutoff } } ?: items

    val canLoadMore: Boolean get() = hasMore && !isLoading && !isLoadingMore && error == null
    val isEmpty: Boolean get() = !isLoading && error == null && visibleItems.isEmpty()

    /** Error *over* content → inline banner; error *instead of* content → full-screen retry. */
    val showsBlockingError: Boolean get() = error != null && visibleItems.isEmpty()
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
