package ar.com.ingenieriaalimentacion.data.food.mapper

import ar.com.ingenieriaalimentacion.data.food.dto.FoodActivityLineResponse
import ar.com.ingenieriaalimentacion.data.food.dto.FoodMovementActivityItemResponse
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovement
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovementDirection
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovementLine
import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import kotlin.time.Instant

/**
 * Never branches on [FoodMovementActivityItemResponse.type] — it maps whichever sibling key the
 * filtered call populated, so an unexpected `type` string yields null and is skipped rather than
 * throwing.
 *
 * Returns null when the payload is missing, or when `occurred_at` is unparseable: a row the client
 * cannot date would sort to the epoch and land under the wrong day header.
 */
internal fun FoodMovementActivityItemResponse.toDomain(): FoodMovement? {
    val millis = runCatching { Instant.parse(occurredAt).toEpochMilliseconds() }.getOrNull()
        ?: return null
    val (payload, direction) = when {
        foodIntake != null -> foodIntake to FoodMovementDirection.Intake
        foodOutflow != null -> foodOutflow to FoodMovementDirection.Outflow
        else -> return null
    }
    return FoodMovement(
        id = payload.id,
        direction = direction,
        occurredAt = occurredAt,
        occurredAtMillis = millis,
        establishmentName = payload.establishment.name,
        comments = payload.comments?.takeIf { it.isNotBlank() },
        // `map`, never `distinctBy`/`associateBy`: the same food may legally appear twice in one
        // movement and both lines must render, as stored, in stored order.
        lines = payload.items.map { it.toDomain() },
    )
}

private fun FoodActivityLineResponse.toDomain(): FoodMovementLine = FoodMovementLine(
    foodId = food.id,
    foodName = food.name,
    quantity = quantity,
    unit = unit.toFoodUnitOrKilogramos(),
)

/** Same fallback policy as [FoodMapper]'s catalog mapping: an unrecognized unit degrades to
 *  kilogramos rather than dropping the line. Duplicated deliberately — `FoodMapper`'s copy is
 *  private and this one maps a different DTO. */
private fun String.toFoodUnitOrKilogramos(): FoodUnit = when (this) {
    "unidades" -> FoodUnit.Unidades
    "litros" -> FoodUnit.Litros
    else -> FoodUnit.Kilogramos
}
