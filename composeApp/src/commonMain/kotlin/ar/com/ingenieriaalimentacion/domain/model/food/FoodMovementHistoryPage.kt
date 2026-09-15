package ar.com.ingenieriaalimentacion.domain.model.food

/**
 * One fetched page of the food-movement feed, ingresos and salidas already merged and ordered by the
 * server.
 *
 * [items] is a complete global prefix of the feed — there is no client-side merge and therefore no
 * cutoff to apply, no partially-complete tail and nothing for the screen to hold back.
 *
 * [hasMore] is false for a page served from cache while offline: the cache is a recency-capped LRU,
 * not a paginated mirror, so an offline page is terminal and the page counter must not advance off
 * it.
 */
data class FoodMovementHistoryPage(
    val items: List<FoodMovement>,
    val hasMore: Boolean,
)
