package ar.com.ingenieriaalimentacion.data.food.mapper

import ar.com.ingenieriaalimentacion.data.food.dto.FoodIntakeRequest
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

internal fun PaginatedFoodsResponse.toDomain(): FoodSearchResult =
    FoodSearchResult(items = items.map { it.toDomain() }, total = total)

/** [receivedAtIso] is stamped by the repository so the ISO formatting lives in one place. */
internal fun NewFoodIntake.toRequest(receivedAtIso: String): FoodIntakeRequest = FoodIntakeRequest(
    id = id,
    establishmentId = establishmentId,
    receivedAt = receivedAtIso,
    comments = comments?.takeIf { it.isNotBlank() },
    items = items.map { FoodLineRequest(foodId = it.foodId, quantity = it.quantity) },
)

private fun String.toFoodUnit(): FoodUnit = when (this) {
    "unidades" -> FoodUnit.Unidades
    "litros" -> FoodUnit.Litros
    // Kilogramos covers the vast majority of the catalog, so an unrecognized value degrades to a
    // decimal-friendly field rather than wrongly forcing whole numbers.
    else -> FoodUnit.Kilogramos
}
