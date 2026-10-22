package ar.com.ingenieriaalimentacion.domain.model.food

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FoodIntakeOriginTest {
    @Test
    fun `the new origins are read from the wire with their Spanish label`() {
        assertEquals("Donación particular", FoodIntakeOrigin.fromWire("private_donation")?.label)
        assertEquals("Colecta / evento", FoodIntakeOrigin.fromWire("collection_event")?.label)
    }

    @Test
    fun `foundation is labelled Fundación ONG and social organization is no longer an origin`() {
        assertEquals("Fundación / ONG", FoodIntakeOrigin.Foundation.label)
        assertNull(FoodIntakeOrigin.fromWire("social_organization"))
    }
}
