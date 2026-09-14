package ar.com.ingenieriaalimentacion.data.food

import ar.com.ingenieriaalimentacion.data.activity.local.ActivityItemType
import ar.com.ingenieriaalimentacion.data.activity.local.ActivityLocalStore
import ar.com.ingenieriaalimentacion.data.activity.local.CachedActivityRow
import ar.com.ingenieriaalimentacion.data.food.mapper.toDomain
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovement
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovementDirection
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovementHistoryPage
import ar.com.ingenieriaalimentacion.domain.repository.FoodMovementHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json

/**
 * Cache ∪ remote — **no queue half**. Food movements are the app's only write without an offline
 * queue, and salidas cannot be created on mobile at all, so there is no local row to merge or to
 * win an id collision (ADR-009 is vacuous here).
 *
 * **One request per page.** The backend accepts a repeated `type`, so ingresos and salidas are
 * asked for together and come back already merged, sorted and paginated as a single feed. Every
 * page is therefore a complete global prefix, with nothing to hold back from the screen.
 *
 * Responses are still split by direction on the way into the cache — see [persist].
 */
class FoodMovementHistoryRepositoryImpl(
    private val api: FoodMovementActivityApi,
    private val cache: ActivityLocalStore,
    private val json: Json,
) : FoodMovementHistoryRepository {

    override fun observeLocalMerged(): Flow<List<FoodMovement>> = combine(
        cache.observe(ActivityItemType.FOOD_INTAKE),
        cache.observe(ActivityItemType.FOOD_OUTFLOW),
    ) { intakes, outflows ->
        (intakes + outflows).decodeAndSort()
    }

    override suspend fun getPage(page: Int, pageSize: Int): Result<FoodMovementHistoryPage> {
        val remote = runCatching { api.list(page, pageSize) }
        return remote.fold(
            onSuccess = { response ->
                val movements = response.items.mapNotNull { it.toDomain() }
                persist(movements)
                Result.success(
                    FoodMovementHistoryPage(
                        // Server order is kept as-is: the page arrives ordered and `mapNotNull`
                        // preserves the relative order of the rows it keeps. Only `cachedPage`
                        // needs a sort, because it concatenates two separately-ordered cache keys.
                        items = movements,
                        // `total_pages`, not `items.size >= pageSize`: the envelope is authoritative
                        // and the heuristic costs a wasted round-trip on an exactly-full last page.
                        hasMore = page < response.totalPages,
                    ),
                )
            },
            onFailure = { error ->
                // Offline: serve the requested cached page instead of failing. Mirrors
                // SubjectHistoryRepositoryImpl / SurveyResponseHistoryRepositoryImpl, minus the
                // queue half (there is no food-movement queue).
                val cached = cachedPage(page, pageSize)
                if (cached.isNotEmpty()) {
                    // `hasMore = false` is this repository's equivalent of the house
                    // `totalPages = page`: an offline read is a TERMINAL page. The cache is a
                    // 200-row-per-type LRU, not a paginated mirror, so letting the counter advance
                    // off it would skip real server pages once connectivity returns.
                    Result.success(FoodMovementHistoryPage(items = cached, hasMore = false))
                } else {
                    Result.failure(error)
                }
            },
        )
    }

    /**
     * One response, two cache keys. `CachedActivityItem` has a single `type` column, so the merged
     * page is regrouped by direction before it is written — that keeps the 200-row cap per direction
     * and keeps [observeLocalMerged]'s two-flow `combine` working unchanged.
     *
     * A direction that contributed no rows to this page is skipped rather than written empty:
     * `upsertPage` always opens a transaction and runs the cap eviction, so an empty write would
     * cost a commit and a spurious flow emission for no change.
     */
    private suspend fun persist(movements: List<FoodMovement>) {
        val byDirection = movements.groupBy { it.direction }
        persist(ActivityItemType.FOOD_INTAKE, byDirection[FoodMovementDirection.Intake])
        persist(ActivityItemType.FOOD_OUTFLOW, byDirection[FoodMovementDirection.Outflow])
    }

    private suspend fun persist(type: ActivityItemType, movements: List<FoodMovement>?) {
        if (movements.isNullOrEmpty()) return
        cache.upsertPage(type, movements.map { it.toCachedRow() })
    }

    private suspend fun cachedPage(page: Int, pageSize: Int): List<FoodMovement> {
        val offset = (page - 1) * pageSize
        val rows = cache.getPage(ActivityItemType.FOOD_INTAKE, pageSize, offset) +
            cache.getPage(ActivityItemType.FOOD_OUTFLOW, pageSize, offset)
        return rows.decodeAndSort()
    }

    /** An undecodable payload is skipped, never thrown: the cache must not be able to blank the tab. */
    private fun List<CachedActivityRow>.decodeAndSort(): List<FoodMovement> =
        mapNotNull { row ->
            runCatching { json.decodeFromString(FoodMovement.serializer(), row.payloadJson) }.getOrNull()
        }.sortedNewestFirst()

    /**
     * The cache row id is namespaced by direction. `CachedActivityItem.id` is the **table-wide**
     * primary key, not `(type, id)` — so an intake and an outflow sharing an id would collide and
     * `INSERT OR REPLACE` would silently flip one row's `type`, corrupting both sections. Server
     * UUIDs from two independent tables make that vanishingly unlikely, but the prefix removes the
     * possibility for the price of four characters. `evictBeyondCap` filters by `type` first, so
     * the prefix is invisible to the cap. The *domain* id stays raw.
     */
    private fun FoodMovement.toCachedRow(): CachedActivityRow = CachedActivityRow(
        id = "${direction.cacheIdPrefix}$id",
        occurredAtMillis = occurredAtMillis,
        payloadJson = json.encodeToString(FoodMovement.serializer(), this),
    )
}

/** Globally date-descending, id-descending — the same order the backend emits, so the fetched page
 *  and the cache flow agree. */
private fun List<FoodMovement>.sortedNewestFirst(): List<FoodMovement> =
    sortedWith(compareByDescending<FoodMovement> { it.occurredAtMillis }.thenByDescending { it.id })

private val FoodMovementDirection.cacheIdPrefix: String
    get() = when (this) {
        FoodMovementDirection.Intake -> "in:"
        FoodMovementDirection.Outflow -> "out:"
    }
