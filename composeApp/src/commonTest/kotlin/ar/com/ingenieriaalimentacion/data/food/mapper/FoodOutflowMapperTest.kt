package ar.com.ingenieriaalimentacion.data.food.mapper

import ar.com.ingenieriaalimentacion.data.food.dto.FoodLineRequest
import ar.com.ingenieriaalimentacion.domain.model.food.FoodOutflowCategory
import ar.com.ingenieriaalimentacion.domain.model.food.NewFoodOutflow
import ar.com.ingenieriaalimentacion.domain.model.food.NewFoodOutflowLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FoodOutflowMapperTest {
    @Test
    fun `domain outflow maps to the backend request`() {
        val request = NewFoodOutflow(
            id = "id",
            establishmentId = "est",
            occurredAtEpochMillis = 0,
            category = FoodOutflowCategory.Other,
            comments = "  ",
            items = listOf(NewFoodOutflowLine("food", "0.750")),
        ).toRequest("1970-01-01T00:00:00Z")

        assertEquals("id", request.id)
        assertEquals("est", request.establishmentId)
        assertEquals("1970-01-01T00:00:00Z", request.occurredAt)
        assertEquals("other", request.category)
        assertNull(request.comments)
        assertEquals("manual", request.source)
        assertEquals(FoodLineRequest("food", "0.750"), request.items.single())
    }

    @Test
    fun `comments are trimmed for every outflow category`() {
        val base = NewFoodOutflow(
            id = "id",
            establishmentId = "est",
            occurredAtEpochMillis = 0,
            category = FoodOutflowCategory.Other,
            comments = "  Comedor barrial  ",
            items = listOf(NewFoodOutflowLine("food", "1")),
        )
        val otherRequest = base.toRequest("1970-01-01T00:00:00Z")
        val kitchenRequest = base.copy(category = FoodOutflowCategory.Kitchen).toRequest("1970-01-01T00:00:00Z")

        assertEquals("other", otherRequest.category)
        assertEquals("Comedor barrial", otherRequest.comments)
        assertEquals("kitchen", kitchenRequest.category)
        assertEquals("Comedor barrial", kitchenRequest.comments)
    }
}
