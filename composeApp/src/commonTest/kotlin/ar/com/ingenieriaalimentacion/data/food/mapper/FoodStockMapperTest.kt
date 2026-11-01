package ar.com.ingenieriaalimentacion.data.food.mapper

import ar.com.ingenieriaalimentacion.data.food.dto.FoodStockItemDto
import ar.com.ingenieriaalimentacion.data.food.dto.IdentifierRefDto
import ar.com.ingenieriaalimentacion.data.food.dto.PaginatedFoodStockResponse
import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class FoodStockMapperTest {

    private fun itemDto(
        foodId: String = "f1",
        foodName: String = "Arroz",
        categoryId: String = "c1",
        categoryName: String = "Cereales",
        unit: String = "kilogramos",
        quantity: String = "10.000",
        updatedAt: String? = "2026-09-02T10:15:33.412000Z",
    ) = FoodStockItemDto(
        food = IdentifierRefDto(id = foodId, name = foodName),
        category = IdentifierRefDto(id = categoryId, name = categoryName),
        unit = unit,
        quantity = quantity,
        updatedAt = updatedAt,
    )

    private fun response(
        items: List<FoodStockItemDto> = listOf(itemDto()),
        total: Int = 1,
        page: Int = 1,
        pageSize: Int = 20,
        totalPages: Int = 1,
    ) = PaginatedFoodStockResponse(
        items = items,
        total = total,
        page = page,
        pageSize = pageSize,
        totalPages = totalPages,
    )

    @Test
    fun `a row maps every field verbatim, quantity included`() {
        val item = itemDto().toDomain()

        assertEquals("f1", item.foodId)
        assertEquals("Arroz", item.foodName)
        assertEquals("c1", item.categoryId)
        assertEquals("Cereales", item.categoryName)
        assertEquals(FoodUnit.Kilogramos, item.unit)
        // Exact digits, never through Double.
        assertEquals("10.000", item.quantity)
        assertEquals(
            Instant.parse("2026-09-02T10:15:33.412000Z").toEpochMilliseconds(),
            item.updatedAtMillis,
        )
    }

    @Test
    fun `hasMore comes from total_pages, and an empty establishment reports total_pages 0`() {
        val empty = response(items = emptyList(), total = 0, totalPages = 0)

        assertEquals(false, empty.toDomain().hasMore)
        assertEquals(emptyList(), empty.toDomain().items)
    }

    @Test
    fun `hasMore is page less than total_pages, not an items-size heuristic`() {
        assertTrue(response(page = 1, totalPages = 3).toDomain().hasMore)
        assertTrue(response(page = 2, totalPages = 3).toDomain().hasMore)
        // An exactly-full last page must not cost a wasted round-trip.
        assertFalse(response(page = 3, totalPages = 3).toDomain().hasMore)
    }

    @Test
    fun `an unknown unit degrades to kilogramos instead of dropping the row`() {
        val dto = itemDto(unit = "toneladas")

        assertEquals(FoodUnit.Kilogramos, dto.toDomain().unit)
    }

    @Test
    fun `the known units still map to themselves`() {
        assertEquals(FoodUnit.Unidades, itemDto(unit = "unidades").toDomain().unit)
        assertEquals(FoodUnit.Litros, itemDto(unit = "litros").toDomain().unit)
    }

    @Test
    fun `an unparseable updated_at becomes null, not now`() {
        assertNull(itemDto(updatedAt = "not-a-date").toDomain().updatedAtMillis)
    }

    @Test
    fun `a missing updated_at becomes null`() {
        assertNull(itemDto(updatedAt = null).toDomain().updatedAtMillis)
    }

    @Test
    fun `a zero balance is depleted without ever parsing to Double`() {
        assertTrue(itemDto(quantity = "0.000").toDomain().isDepleted)
        assertFalse(itemDto(quantity = "0.001").toDomain().isDepleted)
    }
}
