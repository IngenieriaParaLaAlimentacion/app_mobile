package ar.com.ingenieriaalimentacion.core.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextFieldStateTest {

    private val validator: Validator = Rules.of(
        Rules.required("required"),
        Rules.digitsInRange(1..99, "out of range"),
    )

    @Test
    fun `onChange recomputes error but keeps it hidden while not blurred`() {
        val field = TextFieldState().onChange("0", validator)
        assertEquals("out of range", field.error)
        assertFalse(field.isValid)
        assertNull(field.displayError) // never blurred yet
    }

    @Test
    fun `error stays hidden while the field is focused`() {
        val field = TextFieldState()
            .onFocus(true, validator)
            .onChange("0", validator)
        assertEquals("out of range", field.error)
        assertNull(field.displayError) // focused → hidden
    }

    @Test
    fun `invalid content surfaces after blur`() {
        val field = TextFieldState()
            .onFocus(true, validator)
            .onChange("0", validator)
            .onFocus(false, validator)
        assertTrue(field.hasBlurred)
        assertEquals("out of range", field.displayError)
    }

    @Test
    fun `blurring an empty field does not surface the required error`() {
        val field = TextFieldState()
            .onFocus(true, validator)
            .onFocus(false, validator)
        assertEquals("required", field.error) // computed
        assertNull(field.displayError) // but not shown for an empty, merely-touched field
    }

    @Test
    fun `valid content shows no error after blur`() {
        val field = TextFieldState()
            .onChange("42", validator)
            .onFocus(false, validator)
        assertTrue(field.isValid)
        assertNull(field.displayError)
    }

    @Test
    fun `showError forces the required error to display on an empty field`() {
        val field = TextFieldState().showError(validator)
        assertTrue(field.forceShow)
        assertEquals("required", field.displayError)
    }

    @Test
    fun `of returns the first failing rule message`() {
        assertEquals("required", validator(""))
        assertEquals("out of range", validator("0"))
        assertNull(validator("42"))
    }
}
