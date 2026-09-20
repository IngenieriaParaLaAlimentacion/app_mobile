package ar.com.ingenieriaalimentacion.presentation.feature.food.establishment_food_movement_history

import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementSummary
import ar.com.ingenieriaalimentacion.presentation.common.DayGroup
import ar.com.ingenieriaalimentacion.presentation.common.dayOf
import ar.com.ingenieriaalimentacion.presentation.common.groupByDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Dated by `occurredAtMillis`, the same value the API sorts the pages on: `groupByDay` groups
 * *consecutive* runs, so grouping on any other timestamp silently produces duplicate headers.
 */
internal fun List<EstablishmentFoodMovementSummary>.toDayGroups(
    today: LocalDate,
    zone: TimeZone,
): List<DayGroup<EstablishmentFoodMovementSummary>> =
    groupByDay(today) { dayOf(it.occurredAtMillis, zone) }
