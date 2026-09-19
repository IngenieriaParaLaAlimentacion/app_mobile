package ar.com.ingenieriaalimentacion.data.food.mapper

import ar.com.ingenieriaalimentacion.data.food.dto.FoodLineRequest
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
            comments = "  ",
            items = listOf(NewFoodOutflowLine("food", "0.750")),
        ).toRequest("1970-01-01T00:00:00Z")

        assertEquals("id", request.id)
        assertEquals("est", request.establishmentId)
        assertEquals("1970-01-01T00:00:00Z", request.occurredAt)
        assertNull(request.comments)
        assertEquals("manual", request.source)
        assertEquals(FoodLineRequest("food", "0.750"), request.items.single())
    }
}
