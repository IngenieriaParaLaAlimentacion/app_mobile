package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_history

import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseTarget
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class SurveyResponseHistoryDaysTest {

    private val today = LocalDate(2026, 9, 6)
    private val utc = TimeZone.UTC

    @Test
    fun `groups consecutive responses of the same day under one header`() {
        val days = listOf(
            summary("a", "2026-09-06T18:00:00Z"),
            summary("b", "2026-09-06T09:30:00Z"),
            summary("c", "2026-09-05T20:00:00Z"),
        ).toDayGroups(today, utc)

        assertEquals(listOf("Hoy", "Ayer"), days.map { it.label })
        assertEquals(listOf("a", "b"), days[0].items.map { it.id })
        assertEquals(listOf("c"), days[1].items.map { it.id })
    }

    @Test
    fun `labels older days with the spelled-out date and only spells the year when it differs`() {
        val days = listOf(
            summary("a", "2026-09-01T10:00:00Z"),
            summary("b", "2025-12-24T10:00:00Z"),
        ).toDayGroups(today, utc)

        assertEquals(listOf("1 de septiembre", "24 de diciembre de 2025"), days.map { it.label })
    }

    @Test
    fun `an unparseable event date groups under Sin fecha instead of today`() {
        val days = listOf(summary("a", "not-a-date")).toDayGroups(today, utc)

        assertEquals(listOf("Sin fecha"), days.map { it.label })
    }

    @Test
    fun `an out-of-order row repeats the header rather than joining the wrong day`() {
        val days = listOf(
            summary("a", "2026-09-06T18:00:00Z"),
            summary("b", "2026-09-05T18:00:00Z"),
            summary("c", "2026-09-06T08:00:00Z"),
        ).toDayGroups(today, utc)

        assertEquals(listOf("Hoy", "Ayer", "Hoy"), days.map { it.label })
        assertEquals(days.map { it.key }.distinct().size, days.size)
    }

    @Test
    fun `the local time zone decides the day, not UTC`() {
        // 00:30 UTC on the 6th is still the 5th in Buenos Aires (UTC-3).
        val days = listOf(summary("a", "2026-09-06T00:30:00Z"))
            .toDayGroups(today, TimeZone.of("America/Argentina/Buenos_Aires"))

        assertEquals(listOf("Ayer"), days.map { it.label })
    }

    @Test
    fun `an empty list produces no headers`() {
        assertEquals(emptyList(), emptyList<SurveyResponseSummary>().toDayGroups(today, utc))
    }

    private fun summary(id: String, eventAt: String) = SurveyResponseSummary(
        id = id,
        surveyId = "s1",
        surveyName = "Encuesta",
        responseTarget = SurveyResponseTarget.Anonymous,
        targetLabel = null,
        eventAt = eventAt,
        syncState = SyncState.Synced,
        isLocal = false,
    )
}
