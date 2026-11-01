package ar.com.ingenieriaalimentacion.domain.util

import kotlin.test.Test
import kotlin.test.assertEquals

class DecimalInputTest {

    @Test
    fun `sanitizeDecimalInput normalizes comma and strips letters`() {
        assertEquals("12.5", sanitizeDecimalInput("12,5"))
        assertEquals("12.5", sanitizeDecimalInput("1a2.5kg"))
        assertEquals("", sanitizeDecimalInput("abc"))
    }

    @Test
    fun `sanitizeDecimalInput keeps only the first separator`() {
        assertEquals("1.23", sanitizeDecimalInput("1.2.3"))
    }

    @Test
    fun `sanitizeQuantityInput caps the decimals at three`() {
        assertEquals("1.234", sanitizeQuantityInput("1.2345"))
        assertEquals("1.234", sanitizeQuantityInput("1,2349999"))
    }

    @Test
    fun `sanitizeQuantityInput caps the integer digits at seven`() {
        assertEquals("9999999", sanitizeQuantityInput("99999999"))
        assertEquals("9999999.999", sanitizeQuantityInput("99999999.9999"))
    }

    @Test
    fun `sanitizeQuantityInput keeps only the first separator`() {
        assertEquals("1.23", sanitizeQuantityInput("1.2.3"))
    }

    @Test
    fun `sanitizeQuantityInput keeps a trailing separator so the user can keep typing`() {
        assertEquals("12.", sanitizeQuantityInput("12."))
    }

    @Test
    fun `sanitizeIntegerInput drops separators and caps the digits`() {
        assertEquals("125", sanitizeIntegerInput("12.5"))
        assertEquals("125", sanitizeIntegerInput("12,5"))
        assertEquals("1234567", sanitizeIntegerInput("123456789"))
        assertEquals("", sanitizeIntegerInput("abc"))
    }
}
