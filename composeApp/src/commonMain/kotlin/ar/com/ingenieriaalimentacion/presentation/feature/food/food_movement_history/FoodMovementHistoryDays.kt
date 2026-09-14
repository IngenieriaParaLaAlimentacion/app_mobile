package ar.com.ingenieriaalimentacion.presentation.feature.food.food_movement_history

import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovement
import ar.com.ingenieriaalimentacion.presentation.common.DayGroup
import ar.com.ingenieriaalimentacion.presentation.common.dayOf
import ar.com.ingenieriaalimentacion.presentation.common.groupByDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Day blocks for the Alimentos tab, dated by `occurredAtMillis` — the same value the repository
 * sorts on and the same one the pagination watermark compares against, so blocks, ordering and the
 * visible cut can never disagree. (`groupByDay` groups *consecutive* runs, so grouping on a
 * different timestamp than the sort key silently produces duplicate headers.)
 *
 * Uses the `Long` overload of [dayOf], not the `String` one: `occurredAtMillis` was already parsed
 * once in the mapper, and re-parsing the ISO string would reintroduce a "Sin fecha" path that
 * cannot occur here (an unparseable timestamp is dropped at the mapper).
 */
internal fun List<FoodMovement>.toDayGroups(
    today: LocalDate,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): List<DayGroup<FoodMovement>> = groupByDay(today) { dayOf(it.occurredAtMillis, zone) }
