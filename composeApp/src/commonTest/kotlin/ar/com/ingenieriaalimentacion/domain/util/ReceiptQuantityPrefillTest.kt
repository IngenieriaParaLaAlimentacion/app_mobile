package ar.com.ingenieriaalimentacion.domain.util

import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReceiptQuantityPrefillTest {
    @Test fun convertsExactPrintedUnits() {
        assertEquals("0.5", exactReceiptPrefill("500", "g", FoodUnit.Kilogramos))
        assertEquals("0.75", exactReceiptPrefill("750", "ml", FoodUnit.Litros))
        assertEquals("3", exactReceiptPrefill("3.000", "un", FoodUnit.Unidades))
        assertEquals("9999999.999", exactReceiptPrefill("9999999.999", "kg", FoodUnit.Kilogramos))
    }

    @Test fun leavesAmbiguousOrInvalidValuesBlank() {
        assertNull(exactReceiptPrefill("2", null, FoodUnit.Unidades))
        assertNull(exactReceiptPrefill("2", "x3", FoodUnit.Unidades))
        assertNull(exactReceiptPrefill("0.5", "g", FoodUnit.Kilogramos))
        assertNull(exactReceiptPrefill("500", "g", FoodUnit.Litros))
        assertNull(exactReceiptPrefill("10000000", "kg", FoodUnit.Kilogramos))
        assertNull(exactReceiptPrefill("0", "kg", FoodUnit.Kilogramos))
        assertNull(exactReceiptPrefill("1.5", "un", FoodUnit.Unidades))
    }
}
