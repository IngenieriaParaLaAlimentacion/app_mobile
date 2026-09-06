package ar.com.ingenieriaalimentacion.presentation.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The epoch-millis path used by the Sujetos and Mediciones tabs. The ISO path (Encuestas) is
 * covered by [ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_history.SurveyResponseHistoryDaysTest].
 */
class DayGroupTest {

    private val today = LocalDate(2026, 9, 6)
    private val utc = TimeZone.UTC

    private fun millis(iso: String) = Instant.parse(iso).toEpochMilliseconds()

    @Test
    fun `consecutive rows of one day share a header, and a missing date falls to Sin fecha`() {
        val rows = listOf(
            "today-1" to millis("2026-09-06T18:00:00Z"),
            "today-2" to millis("2026-09-06T07:00:00Z"),
            "yesterday" to millis("2026-09-05T23:00:00Z"),
            "old" to millis("2025-12-24T10:00:00Z"),
            "unknown" to null,
        )

        val days = rows.groupByDay(today) { (_, at) -> at?.let { dayOf(it, utc) } }

        assertEquals(listOf("Hoy", "Ayer", "24 de diciembre de 2025", "Sin fecha"), days.map { it.label })
        assertEquals(listOf("today-1", "today-2"), days[0].items.map { it.first })
        assertEquals(days.map { it.key }.distinct().size, days.size)
    }

    @Test
    fun `the day is resolved in the given zone, not UTC`() {
        // 00:30 UTC on the 6th is still the 5th in Buenos Aires (UTC-3).
        val at = millis("2026-09-06T00:30:00Z")
        val zone = TimeZone.of("America/Argentina/Buenos_Aires")

        assertEquals("Hoy", listOf(at).groupByDay(today) { dayOf(it, utc) }.single().label)
        assertEquals("Ayer", listOf(at).groupByDay(today) { dayOf(it, zone) }.single().label)
    }
}
