package ar.com.ingenieriaalimentacion.data.food

import ar.com.ingenieriaalimentacion.data.activity.local.ActivityItemType
import ar.com.ingenieriaalimentacion.data.activity.local.ActivityLocalStore
import ar.com.ingenieriaalimentacion.data.activity.local.CachedActivityRow
import ar.com.ingenieriaalimentacion.data.food.dto.PaginatedFoodMovementActivityResponse
import ar.com.ingenieriaalimentacion.data.food.mapper.toDomain
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovement
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovementDirection
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovementHistoryPage
import ar.com.ingenieriaalimentacion.domain.repository.FoodMovementHistoryRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json

/**
 * Cache ∪ remote — **no queue half**. Food movements are the app's only write without an offline
 * queue, and salidas cannot be created on mobile at all, so there is no local row to merge or to
 * win an id collision (ADR-009 is vacuous here).
 *
 * Two paginated sources feed one section. Rather than interleave them with a shared cursor (which
 * the backend cannot express — there is no combined `type=food_intake,food_outflow` filter and no
 * opaque cursor), page N of *both* types is fetched in parallel and the results are merged. The
 * merge is only guaranteed complete down to the **watermark**: the newest of the two sources'
 * oldest-fetched timestamps, considering only sources that still have more pages. Everything at or
 * above the watermark is provably the true global prefix of the merged feed; anything below it may
 * still have older-source rows missing, so the ViewModel hides it until the next page arrives.
 * That is what makes the visible list strictly append-only — CA "sin duplicar ni reordenar".
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

    override suspend fun getPage(page: Int, pageSize: Int): Result<FoodMovementHistoryPage> =
        coroutineScope {
            // Parallel, not sequential: the two calls are independent and the user is waiting.
            val intakes = async { runCatching { api.listIntakes(page, pageSize) } }
            val outflows = async { runCatching { api.listOutflows(page, pageSize) } }
            fold(page, pageSize, intakes.await(), outflows.await())
        }

    private suspend fun fold(
        page: Int,
        pageSize: Int,
        intakes: Result<PaginatedFoodMovementActivityResponse>,
        outflows: Result<PaginatedFoodMovementActivityResponse>,
    ): Result<FoodMovementHistoryPage> {
        // Partial success is a success: one direction being unavailable must not blank the tab.
        // Only a total failure with nothing cached is surfaced as an error (CA 3's retryable state).
        if (intakes.isFailure && outflows.isFailure) {
            val cached = cachedPage(page, pageSize)
            val error = intakes.exceptionOrNull() ?: outflows.exceptionOrNull()!!
            return if (cached.isNotEmpty()) {
                Result.success(
                    FoodMovementHistoryPage(
                        items = cached,
                        // Unknown watermark offline: show everything cached rather than truncate.
                        cutoffMillis = null,
                        hasMore = cached.size >= pageSize,
                        isStale = true,
                    ),
                )
            } else {
                Result.failure(error)
            }
        }

        val intakeItems = intakes.persist(ActivityItemType.FOOD_INTAKE)
        val outflowItems = outflows.persist(ActivityItemType.FOOD_OUTFLOW)

        return Result.success(
            FoodMovementHistoryPage(
                items = (intakeItems + outflowItems).sortedNewestFirst(),
                // Newest of the two floors: below it, one source has already run out of rows.
                cutoffMillis = listOfNotNull(
                    intakes.mergeBound(intakeItems),
                    outflows.mergeBound(outflowItems),
                ).maxOrNull(),
                // `total_pages`, not `items.size >= pageSize`: the watermark needs exact per-source
                // exhaustion, and the heuristic would pin the cutoff for a wasted round-trip.
                hasMore = intakes.hasMore(page) || outflows.hasMore(page),
                isStale = intakes.isFailure || outflows.isFailure,
            ),
        )
    }

    private suspend fun Result<PaginatedFoodMovementActivityResponse>.persist(
        type: ActivityItemType,
    ): List<FoodMovement> {
        val movements = getOrNull()?.items?.mapNotNull { it.toDomain() } ?: return emptyList()
        cache.upsertPage(type, movements.map { it.toCachedRow() })
        return movements
    }

    /**
     * This source's contribution to the merge watermark: the oldest row it returned, or null when
     * it cannot constrain the merge — it is exhausted (`page >= total_pages`) or it failed, so
     * nothing more will arrive from it this round.
     */
    private fun Result<PaginatedFoodMovementActivityResponse>.mergeBound(
        items: List<FoodMovement>,
    ): Long? {
        val exhausted = getOrNull()?.let { it.page >= it.totalPages } ?: true
        return if (exhausted) null else items.minOfOrNull { it.occurredAtMillis }
    }

    private fun Result<PaginatedFoodMovementActivityResponse>.hasMore(page: Int): Boolean =
        getOrNull()?.let { page < it.totalPages } ?: false

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

/** Globally date-descending, id-descending — the same order the backend emits per source, so the
 *  merged list and each source's own stream agree. */
private fun List<FoodMovement>.sortedNewestFirst(): List<FoodMovement> =
    sortedWith(compareByDescending<FoodMovement> { it.occurredAtMillis }.thenByDescending { it.id })

private val FoodMovementDirection.cacheIdPrefix: String
    get() = when (this) {
        FoodMovementDirection.Intake -> "in:"
        FoodMovementDirection.Outflow -> "out:"
    }
