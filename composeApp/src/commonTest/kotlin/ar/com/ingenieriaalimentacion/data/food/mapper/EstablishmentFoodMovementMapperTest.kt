package ar.com.ingenieriaalimentacion.data.food.mapper

import ar.com.ingenieriaalimentacion.data.food.dto.EstablishmentFoodMovementDetailDto
import ar.com.ingenieriaalimentacion.data.food.dto.EstablishmentFoodMovementLineDto
import ar.com.ingenieriaalimentacion.data.food.dto.EstablishmentFoodMovementSummaryDto
import ar.com.ingenieriaalimentacion.data.food.dto.IdentifierRefDto
import ar.com.ingenieriaalimentacion.data.food.dto.PaginatedEstablishmentFoodMovementsResponse
import ar.com.ingenieriaalimentacion.data.food.dto.ResponsibleRefDto
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementDirection
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovementSource
import ar.com.ingenieriaalimentacion.domain.model.food.FoodIntakeOrigin
import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class EstablishmentFoodMovementMapperTest {
    private val responsible = ResponsibleRefDto("u1", "Ana", "Pérez")
    private fun summary(direction: String = "in", occurredAt: String = "2026-09-19T12:00:00Z") =
        EstablishmentFoodMovementSummaryDto("m1", direction, occurredAt, responsible, 3)

    @Test
    fun `summary and page map complete contract`() {
        val page = PaginatedEstablishmentFoodMovementsResponse(
            listOf(summary()), 30, 1, 20, 2,
        ).toDomain()
        val item = page.items.single()
        assertEquals(EstablishmentFoodMovementDirection.In, item.direction)
        assertEquals(Instant.parse("2026-09-19T12:00:00Z").toEpochMilliseconds(), item.occurredAtMillis)
        assertEquals("Ana Pérez", item.responsible.fullName)
        assertEquals(3, item.foodCount)
        assertTrue(page.hasMore)
        assertFalse(
            PaginatedEstablishmentFoodMovementsResponse(emptyList(), 0, 1, 20, 0)
                .toDomain().hasMore,
        )
    }

    @Test
    fun `detail preserves duplicate response order exact quantities and degrades source and unit`() {
        val dto = EstablishmentFoodMovementDetailDto(
            id = "m1",
            direction = "out",
            occurredAt = "2026-09-19T12:00:00Z",
            source = "future-source",
            comments = null,
            responsible = responsible,
            items = listOf(
                EstablishmentFoodMovementLineDto(IdentifierRefDto("f1", "Arroz"), "unknown", "2.500"),
                EstablishmentFoodMovementLineDto(IdentifierRefDto("f1", "Arroz"), "litros", "1.000"),
            ),
        )
        val detail = dto.toDomain()
        assertEquals(EstablishmentFoodMovementDirection.Out, detail.direction)
        assertEquals(FoodMovementSource.Manual, detail.source)
        assertNull(detail.comments)
        assertEquals(listOf("f1", "f1"), detail.items.map { it.foodId })
        assertEquals(listOf("2.500", "1.000"), detail.items.map { it.quantity })
        assertEquals(listOf(FoodUnit.Kilogramos, FoodUnit.Litros), detail.items.map { it.unit })
    }

    @Test
    fun `intake provenance maps true false and missing legacy values`() {
        val intake = summary().copy(originType = "foundation", isRecurring = true).toDomain()
        assertEquals(FoodIntakeOrigin.Foundation, intake.originType)
        assertEquals(true, intake.isRecurring)
        assertEquals(false, summary().copy(isRecurring = false).toDomain().isRecurring)
        assertNull(summary().toDomain().originType)
        assertNull(summary().toDomain().isRecurring)
        val detail = EstablishmentFoodMovementDetailDto(
            id = "m1", direction = "in", occurredAt = "2026-09-19T12:00:00Z",
            source = "photo", comments = null, responsible = responsible, items = emptyList(),
            originType = "church", isRecurring = false,
        ).toDomain()
        assertEquals(FoodIntakeOrigin.Church, detail.originType)
        assertEquals(false, detail.isRecurring)
    }

    @Test
    fun `outflow ignores malformed intake metadata`() {
        val summary = summary(direction = "out").copy(originType = "church", isRecurring = true).toDomain()
        assertNull(summary.originType)
        assertNull(summary.isRecurring)
        val detail = EstablishmentFoodMovementDetailDto(
            id = "m1", direction = "out", occurredAt = "2026-09-19T12:00:00Z",
            source = "manual", comments = null, responsible = responsible, items = emptyList(),
            originType = "church", isRecurring = true,
        ).toDomain()
        assertNull(detail.originType)
        assertNull(detail.isRecurring)
    }

    @Test
    fun `unknown direction and invalid timestamps fail`() {
        assertFails { summary(direction = "sideways").toDomain() }
        assertFails { summary(occurredAt = "invalid").toDomain() }
    }
}
