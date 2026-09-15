package ar.com.ingenieriaalimentacion.presentation.feature.food.food_movement_history

import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovement
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovementDirection
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class FoodMovementHistoryDaysTest {

    private val today = LocalDate(2026, 9, 6)
    private val utc = TimeZone.UTC

    @Test
    fun `groups consecutive movements of the same day under one header`() {
        val days = listOf(
            movement("a", "2026-09-06T18:00:00Z"),
            movement("b", "2026-09-06T09:30:00Z"),
            movement("c", "2026-09-05T20:00:00Z"),
        ).toDayGroups(today, utc)

        assertEquals(listOf("Hoy", "Ayer"), days.map { it.label })
        assertEquals(listOf("a", "b"), days[0].items.map { it.id })
        assertEquals(listOf("c"), days[1].items.map { it.id })
    }

    @Test
    fun `ingresos and salidas of the same day share one header`() {
        val days = listOf(
            movement("a", "2026-09-06T18:00:00Z", FoodMovementDirection.Intake),
            movement("b", "2026-09-06T09:30:00Z", FoodMovementDirection.Outflow),
        ).toDayGroups(today, utc)

        assertEquals(listOf("Hoy"), days.map { it.label })
        assertEquals(2, days.single().items.size)
    }

    @Test
    fun `labels older days with the spelled-out date and only spells the year when it differs`() {
        val days = listOf(
            movement("a", "2026-09-01T10:00:00Z"),
            movement("b", "2025-12-24T10:00:00Z"),
        ).toDayGroups(today, utc)

        assertEquals(listOf("1 de septiembre", "24 de diciembre de 2025"), days.map { it.label })
    }

    @Test
    fun `grouping runs on occurredAtMillis in the local zone, not UTC`() {
        // 00:30 UTC on the 6th is still the 5th in Buenos Aires (UTC-3).
        val days = listOf(movement("a", "2026-09-06T00:30:00Z"))
            .toDayGroups(today, TimeZone.of("America/Argentina/Buenos_Aires"))

        assertEquals(listOf("Ayer"), days.map { it.label })
    }

    @Test
    fun `an empty list produces no headers`() {
        assertEquals(emptyList(), emptyList<FoodMovement>().toDayGroups(today, utc))
    }

    private fun movement(
        id: String,
        occurredAt: String,
        direction: FoodMovementDirection = FoodMovementDirection.Intake,
    ) = FoodMovement(
        id = id,
        direction = direction,
        occurredAt = occurredAt,
        occurredAtMillis = Instant.parse(occurredAt).toEpochMilliseconds(),
        establishmentName = "Comedor",
    )
}
