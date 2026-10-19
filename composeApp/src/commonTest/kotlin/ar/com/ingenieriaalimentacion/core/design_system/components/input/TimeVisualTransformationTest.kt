package ar.com.ingenieriaalimentacion.core.design_system.components.input

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeVisualTransformationTest {

    private val transformation = TimeVisualTransformation()

    private fun display(raw: String) = transformation.filter(AnnotatedString(raw)).text.text

    @Test
    fun `inserts the colon after the hours`() {
        assertEquals("", display(""))
        assertEquals("09", display("09"))
        assertEquals("09:3", display("093"))
        assertEquals("09:30", display("0930"))
    }

    @Test
    fun `caret offsets skip the colon both ways`() {
        val mapping = transformation.filter(AnnotatedString("0930")).offsetMapping

        assertEquals(2, mapping.originalToTransformed(2))
        assertEquals(4, mapping.originalToTransformed(3))
        assertEquals(5, mapping.originalToTransformed(4))
        assertEquals(2, mapping.transformedToOriginal(3))
        assertEquals(4, mapping.transformedToOriginal(5))
    }
}
