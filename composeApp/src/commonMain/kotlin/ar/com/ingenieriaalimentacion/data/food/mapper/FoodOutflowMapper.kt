package ar.com.ingenieriaalimentacion.data.food.mapper

import ar.com.ingenieriaalimentacion.data.food.dto.FoodLineRequest
import ar.com.ingenieriaalimentacion.data.food.dto.FoodOutflowRequest
import ar.com.ingenieriaalimentacion.domain.model.food.NewFoodOutflow

internal fun NewFoodOutflow.toRequest(occurredAtIso: String): FoodOutflowRequest =
    FoodOutflowRequest(
        id = id,
        establishmentId = establishmentId,
        occurredAt = occurredAtIso,
        category = category.wireValue,
        comments = comments?.trim()?.takeIf(String::isNotBlank),
        items = items.map { FoodLineRequest(foodId = it.foodId, quantity = it.quantity) },
    )
