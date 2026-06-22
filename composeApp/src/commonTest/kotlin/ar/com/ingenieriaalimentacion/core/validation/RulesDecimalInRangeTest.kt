package ar.com.ingenieriaalimentacion.core.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RulesDecimalInRangeTest {

    // peso bounds: 0.5 < x <= 300
    private val weight = Rules.decimalInRange(0.5, 300.0, "fuera de rango")

    // talla bounds: 20 < x <= 250
    private val height = Rules.decimalInRange(20.0, 250.0, "fuera de rango")

    @Test
    fun `blank passes so required owns presence`() {
        assertNull(weight(""))
        assertNull(weight("   "))
    }

    @Test
    fun `at or below the exclusive lower bound fails`() {
        assertEquals("fuera de rango", weight("0.5"))
        assertEquals("fuera de rango", weight("0.4"))
    }

    @Test
    fun `just above the lower bound passes`() {
        assertNull(weight("0.6"))
    }

    @Test
    fun `the inclusive upper bound passes and just above fails`() {
        assertNull(weight("300.0"))
        assertEquals("fuera de rango", weight("300.1"))
    }

    @Test
    fun `comma is accepted as the decimal separator`() {
        assertNull(weight("72,5"))
        assertNull(height("170,0"))
    }

    @Test
    fun `non-numeric fails`() {
        assertEquals("fuera de rango", weight("abc"))
        assertEquals("fuera de rango", height("1.2.3"))
    }

    @Test
    fun `talla bounds enforced`() {
        assertEquals("fuera de rango", height("20"))
        assertNull(height("20.1"))
        assertNull(height("250"))
        assertEquals("fuera de rango", height("250.1"))
    }
}
