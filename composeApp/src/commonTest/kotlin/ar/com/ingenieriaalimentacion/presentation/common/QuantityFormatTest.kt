package ar.com.ingenieriaalimentacion.presentation.common

import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class QuantityFormatTest {

    @Test
    fun `trims trailing zeros and uses a comma decimal separator`() {
        assertEquals("0,75", formatQuantity("0.750"))
        assertEquals("12", formatQuantity("12.000"))
        assertEquals("1,005", formatQuantity("1.005"))
        assertEquals("7", formatQuantity("7"))
        assertEquals("0", formatQuantity("0.000"))
    }

    @Test
    fun `keeps the full numeric(10,3) range without a thousands separator`() {
        assertEquals("9999999,999", formatQuantity("9999999.999"))
    }

    @Test
    fun `never rounds to one decimal the way formatMeasure would`() {
        // The regression this formatter exists for: 0,750 kg must not render as 0,8 kg.
        assertEquals("0,75", formatQuantity("0.750"))
        assertEquals("0,749", formatQuantity("0.7499"))
    }

    @Test
    fun `an unparseable value is returned unchanged`() {
        assertEquals("abc", formatQuantity("abc"))
        assertEquals("1.2.3", formatQuantity("1.2.3"))
    }

    @Test
    fun `a negative zero loses its sign`() {
        assertEquals("0", formatQuantity("-0.000"))
        assertEquals("-1,5", formatQuantity("-1.500"))
    }

    @Test
    fun `the unit suffix comes from shortLabel`() {
        assertEquals("0,75 kg", formatQuantityWithUnit("0.750", FoodUnit.Kilogramos))
        assertEquals("12 u", formatQuantityWithUnit("12.000", FoodUnit.Unidades))
        assertEquals("1,5 L", formatQuantityWithUnit("1.5", FoodUnit.Litros))
    }
}
