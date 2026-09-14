package ar.com.ingenieriaalimentacion.domain.model.food

/**
 * One fetched page of the merged food-movement feed.
 *
 * [cutoffMillis] is the bounded-merge watermark: rows older than it may still have missing
 * counterparts from the other direction, so the screen hides them until the next page lands. Null
 * means "nothing to hide" — both directions are exhausted, or the page came from cache where no
 * watermark can be computed.
 *
 * [isStale] marks a page assembled from cache or from only one of the two directions, so the screen
 * can show a soft notice without treating it as a failure.
 */
data class FoodMovementHistoryPage(
    val items: List<FoodMovement>,
    val cutoffMillis: Long?,
    val hasMore: Boolean,
    val isStale: Boolean = false,
)
