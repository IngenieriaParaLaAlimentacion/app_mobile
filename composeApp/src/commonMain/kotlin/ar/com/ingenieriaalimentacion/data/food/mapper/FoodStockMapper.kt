package ar.com.ingenieriaalimentacion.data.food.mapper

import ar.com.ingenieriaalimentacion.data.food.dto.IdentifierRefDto
import ar.com.ingenieriaalimentacion.data.food.dto.FoodStockItemDto
import ar.com.ingenieriaalimentacion.data.food.dto.PaginatedFoodStockResponse
import ar.com.ingenieriaalimentacion.domain.model.food.FoodCategory
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockItem
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockPage
import kotlin.time.Instant

/**
 * Wire → domain. `unit` reuses [toFoodUnit], the single never-throwing degradation rule already
 * applied to the catalog, so an unrecognized server enum can never blank a row.
 *
 * An unparseable `updated_at` becomes null rather than "now": the row is still real stock and must
 * render; only its timestamp line is dropped.
 */
internal fun FoodStockItemDto.toDomain(): FoodStockItem = FoodStockItem(
    foodId = food.id,
    foodName = food.name,
    categoryId = category.id,
    categoryName = category.name,
    unit = unit.toFoodUnit(),
    quantity = quantity,
    updatedAtMillis = updatedAt?.let {
        runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull()
    },
)

/**
 * `total_pages`, not `items.size >= pageSize`: the envelope is authoritative and the heuristic costs
 * a wasted round-trip on an exactly-full last page. Note `total_pages` is **0** (not 1) for an empty
 * establishment, so `page < totalPages` correctly yields `hasMore = false` on page 1.
 */
internal fun PaginatedFoodStockResponse.toDomain(): FoodStockPage = FoodStockPage(
    items = items.map { it.toDomain() },
    hasMore = page < totalPages,
)

internal fun IdentifierRefDto.toDomain(): FoodCategory = FoodCategory(id = id, name = name)
