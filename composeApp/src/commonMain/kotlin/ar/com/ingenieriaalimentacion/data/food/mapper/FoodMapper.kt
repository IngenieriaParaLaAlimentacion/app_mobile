package ar.com.ingenieriaalimentacion.data.food.mapper

import ar.com.ingenieriaalimentacion.data.food.dto.FoodIntakeRequest
import ar.com.ingenieriaalimentacion.data.food.dto.FoodDetailDto
import ar.com.ingenieriaalimentacion.data.food.dto.FoodLineRequest
import ar.com.ingenieriaalimentacion.data.food.dto.FoodListItemDto
import ar.com.ingenieriaalimentacion.data.food.dto.PaginatedFoodsResponse
import ar.com.ingenieriaalimentacion.domain.model.food.Food
import ar.com.ingenieriaalimentacion.domain.model.food.FoodSearchResult
import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import ar.com.ingenieriaalimentacion.domain.model.food.NewFoodIntake

internal fun FoodListItemDto.toDomain(): Food = Food(
    id = id,
    code = code,
    name = name,
    unit = baseUnit.toFoodUnit(),
    categoryName = category.name,
)

internal fun FoodDetailDto.toDomain(): Food = Food(
    id = id,
    code = code,
    name = name,
    unit = when (baseUnit) {
        "unidades" -> FoodUnit.Unidades
        "kilogramos" -> FoodUnit.Kilogramos
        "litros" -> FoodUnit.Litros
        else -> error("Unknown receipt food base unit: $baseUnit")
    },
    categoryName = category.name,
)

internal fun PaginatedFoodsResponse.toDomain(): FoodSearchResult =
    FoodSearchResult(items = items.map { it.toDomain() }, total = total)

/** [occurredAtIso] is stamped by the repository so the ISO formatting lives in one place. */
internal fun NewFoodIntake.toRequest(occurredAtIso: String): FoodIntakeRequest = FoodIntakeRequest(
    id = id,
    establishmentId = establishmentId,
    occurredAt = occurredAtIso,
    comments = comments?.takeIf { it.isNotBlank() },
    originType = originType.wireValue,
    isRecurring = isRecurring,
    items = items.map { FoodLineRequest(foodId = it.foodId, quantity = it.quantity) },
)

/** `internal`, not `private`: [FoodStockMapper] must degrade unknown units by the same rule, and
 *  duplicating a degradation policy is how two code paths quietly start disagreeing. */
internal fun String.toFoodUnit(): FoodUnit = when (this) {
    "unidades" -> FoodUnit.Unidades
    "litros" -> FoodUnit.Litros
    // Kilogramos covers the vast majority of the catalog, so an unrecognized value degrades to a
    // decimal-friendly field rather than wrongly forcing whole numbers.
    else -> FoodUnit.Kilogramos
}
