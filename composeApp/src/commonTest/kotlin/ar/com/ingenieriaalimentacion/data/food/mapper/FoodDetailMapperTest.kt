package ar.com.ingenieriaalimentacion.data.food.mapper

import ar.com.ingenieriaalimentacion.data.food.dto.FoodDetailDto
import ar.com.ingenieriaalimentacion.data.food.dto.IdentifierRefDto
import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FoodDetailMapperTest {
    private val category = IdentifierRefDto("c1", "Cereales")

    @Test fun detailUsesStrictBaseUnit() {
        assertEquals(FoodUnit.Litros, FoodDetailDto("f1", "A1", "Aceite", "litros", category).toDomain().unit)
        assertFailsWith<IllegalStateException> {
            FoodDetailDto("f1", "A1", "Aceite", "bultos", category).toDomain()
        }
    }
}
