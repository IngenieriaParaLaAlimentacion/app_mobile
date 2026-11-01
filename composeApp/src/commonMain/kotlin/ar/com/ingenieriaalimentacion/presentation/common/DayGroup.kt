package ar.com.ingenieriaalimentacion.presentation.common

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * A block of history rows that share one local calendar day, under the header that dates them.
 * Keeping the date on the header instead of repeating it inside every row lets a day of field
 * work read as a single block. Used by all three Historial tabs.
 */
data class DayGroup<T>(
    /** Stable LazyColumn key; carries the index so a repeated day still yields a unique key. */
    val key: String,
    val label: String,
    val items: List<T>,
)

/**
 * Splits an already-sorted history list into day blocks, dating each with [dayOf].
 *
 * Grouping is *consecutive*, not by-key: these lists arrive sorted by event time (newest first),
 * so a boundary is simply "the previous row fell on another day". A row that arrives out of order
 * then degrades to a repeated header instead of silently landing under the wrong date. Group on
 * the same timestamp the list is sorted by, or blocks and ordering will disagree.
 */
fun <T> List<T>.groupByDay(today: LocalDate, dayOf: (T) -> LocalDate?): List<DayGroup<T>> {
    val groups = ArrayList<DayGroup<T>>()
    var currentDay: LocalDate? = null
    var currentItems = mutableListOf<T>()

    fun flush() {
        if (currentItems.isEmpty()) return
        groups += DayGroup(
            key = "day-${currentDay ?: "unknown"}-${groups.size}",
            label = dayLabel(currentDay, today),
            items = currentItems,
        )
        currentItems = mutableListOf()
    }

    forEachIndexed { index, item ->
        val day = dayOf(item)
        if (index > 0 && day != currentDay) flush()
        currentDay = day
        currentItems += item
    }
    flush()
    return groups
}

/** The current local day, the reference every "Hoy"/"Ayer" label is resolved against. */
fun today(zone: TimeZone): LocalDate = kotlin.time.Clock.System.now().toLocalDateTime(zone).date

fun dayOf(epochMillis: Long, zone: TimeZone): LocalDate =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone).date

/** Null when the server sent a timestamp this client cannot parse — those rows group under
 *  "Sin fecha" rather than being forced into today. */
fun dayOf(isoInstant: String, zone: TimeZone): LocalDate? =
    runCatching { Instant.parse(isoInstant).toLocalDateTime(zone).date }.getOrNull()

internal fun dayLabel(date: LocalDate?, today: LocalDate): String = when (date) {
    null -> "Sin fecha"
    today -> "Hoy"
    today.minus(DatePeriod(days = 1)) -> "Ayer"
    // The year is spelled out only when it is not the current one — noise the rest of the time.
    // Month is JANUARY-first, so its ordinal indexes MONTHS directly.
    else -> "${date.day} de ${MONTHS[date.month.ordinal]}" +
        if (date.year != today.year) " de ${date.year}" else ""
}

private val MONTHS = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)
