package ar.com.ingenieriaalimentacion.data.food.mapper

import ar.com.ingenieriaalimentacion.data.food.dto.EstablishmentFoodMovementDetailDto
import ar.com.ingenieriaalimentacion.data.food.dto.EstablishmentFoodMovementSummaryDto
import ar.com.ingenieriaalimentacion.data.food.dto.PaginatedEstablishmentFoodMovementsResponse
import ar.com.ingenieriaalimentacion.data.food.dto.ResponsibleRefDto
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementDetail
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementLine
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementPage
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementSummary
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementDirection
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovementSource
import ar.com.ingenieriaalimentacion.domain.model.food.FoodIntakeOrigin
import ar.com.ingenieriaalimentacion.domain.model.food.ResponsibleRef
import kotlin.time.Instant

internal fun String.toMovementDirection(): EstablishmentFoodMovementDirection = when (this) {
    "in" -> EstablishmentFoodMovementDirection.In
    "out" -> EstablishmentFoodMovementDirection.Out
    else -> error("unknown food movement direction: $this")
}

internal fun ResponsibleRefDto.toDomain() = ResponsibleRef(id, name, lastName)

internal fun EstablishmentFoodMovementSummaryDto.toDomain() = EstablishmentFoodMovementSummary(
    id = id,
    direction = direction.toMovementDirection(),
    occurredAtMillis = Instant.parse(occurredAt).toEpochMilliseconds(),
    responsible = responsible.toDomain(),
    foodCount = foodCount,
    originType = if (direction == "in") FoodIntakeOrigin.fromWire(originType) else null,
    isRecurring = isRecurring.takeIf { direction == "in" },
)

internal fun PaginatedEstablishmentFoodMovementsResponse.toDomain() =
    EstablishmentFoodMovementPage(items.map { it.toDomain() }, page < totalPages)

internal fun EstablishmentFoodMovementDetailDto.toDomain() = EstablishmentFoodMovementDetail(
    id = id,
    direction = direction.toMovementDirection(),
    occurredAtMillis = Instant.parse(occurredAt).toEpochMilliseconds(),
    source = if (source == "photo") FoodMovementSource.Photo else FoodMovementSource.Manual,
    comments = comments,
    originType = if (direction == "in") FoodIntakeOrigin.fromWire(originType) else null,
    isRecurring = isRecurring.takeIf { direction == "in" },
    responsible = responsible.toDomain(),
    items = items.map { line ->
        EstablishmentFoodMovementLine(
            foodId = line.food.id,
            foodName = line.food.name,
            unit = line.unit.toFoodUnit(),
            quantity = line.quantity,
        )
    },
)
