package ar.com.ingenieriaalimentacion.data.food.mapper

import ar.com.ingenieriaalimentacion.data.food.dto.FoodMovementActivityItemResponse
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovementDirection
import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FoodMovementMapperTest {

    // Mirrors the production `Json` (HttpClientFactory.json()): `isLenient` is what lets
    // QuantitySerializer.decodeString accept a JSON *number* for `quantity`.
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun item(raw: String): FoodMovementActivityItemResponse = json.decodeFromString(raw)

    private fun payload(lines: String) = """
        {
          "id": "fm-1",
          "establishment": { "id": "e1", "name": "Comedor Los Aromos" },
          "source": "manual",
          "comments": "Donación",
          "items": [$lines]
        }
    """.trimIndent()

    private val rice = """{ "food": { "id": "f1", "name": "Arroz" }, "unit": "kilogramos", "quantity": 2.5 }"""

    @Test
    fun `a food_intake item maps to the Intake direction`() {
        val movement = assertNotNull(
            item("""{ "type": "food_intake", "occurred_at": "2026-04-01T00:00:00Z", "food_intake": ${payload(rice)} }""")
                .toDomain(),
        )

        assertEquals("fm-1", movement.id)
        assertEquals(FoodMovementDirection.Intake, movement.direction)
        assertEquals("2026-04-01T00:00:00Z", movement.occurredAt)
        assertEquals(1775001600000L, movement.occurredAtMillis)
        assertEquals("Comedor Los Aromos", movement.establishmentName)
        assertEquals("Donación", movement.comments)
    }

    @Test
    fun `a food_outflow item maps to the Outflow direction`() {
        val movement = assertNotNull(
            item("""{ "type": "food_outflow", "occurred_at": "2026-04-01T00:00:00Z", "food_outflow": ${payload(rice)} }""")
                .toDomain(),
        )

        assertEquals(FoodMovementDirection.Outflow, movement.direction)
    }

    @Test
    fun `duplicate food lines are preserved in stored order, never collapsed`() {
        val duplicated = """
            { "food": { "id": "f1", "name": "Arroz" }, "unit": "kilogramos", "quantity": 2.5 },
            { "food": { "id": "f1", "name": "Arroz" }, "unit": "kilogramos", "quantity": 1.25 }
        """.trimIndent()
        val movement = assertNotNull(
            item("""{ "type": "food_intake", "occurred_at": "2026-04-01T00:00:00Z", "food_intake": ${payload(duplicated)} }""")
                .toDomain(),
        )

        assertEquals(2, movement.lines.size)
        assertEquals(listOf("f1", "f1"), movement.lines.map { it.foodId })
        assertEquals(listOf("2.5", "1.25"), movement.lines.map { it.quantity })
    }

    @Test
    fun `known units map exactly and an unknown unit degrades to kilogramos`() {
        val lines = """
            { "food": { "id": "f1", "name": "Huevo" }, "unit": "unidades", "quantity": 12 },
            { "food": { "id": "f2", "name": "Leche" }, "unit": "litros", "quantity": 1 },
            { "food": { "id": "f3", "name": "Arroz" }, "unit": "toneladas", "quantity": 1 }
        """.trimIndent()
        val movement = assertNotNull(
            item("""{ "type": "food_intake", "occurred_at": "2026-04-01T00:00:00Z", "food_intake": ${payload(lines)} }""")
                .toDomain(),
        )

        assertEquals(
            listOf(FoodUnit.Unidades, FoodUnit.Litros, FoodUnit.Kilogramos),
            movement.lines.map { it.unit },
        )
    }

    @Test
    fun `blank comments become null`() {
        val blank = """
            { "id": "fm-1", "establishment": { "id": "e1", "name": "C" }, "comments": "   ", "items": [$rice] }
        """.trimIndent()
        val movement = assertNotNull(
            item("""{ "type": "food_intake", "occurred_at": "2026-04-01T00:00:00Z", "food_intake": $blank }""")
                .toDomain(),
        )

        assertNull(movement.comments)
    }

    @Test
    fun `both sibling keys null yields null rather than throwing`() {
        assertNull(item("""{ "type": "subject", "occurred_at": "2026-04-01T00:00:00Z" }""").toDomain())
    }

    @Test
    fun `an unparseable occurred_at yields null rather than sorting to the epoch`() {
        assertNull(
            item("""{ "type": "food_intake", "occurred_at": "not-a-date", "food_intake": ${payload(rice)} }""")
                .toDomain(),
        )
    }
}
