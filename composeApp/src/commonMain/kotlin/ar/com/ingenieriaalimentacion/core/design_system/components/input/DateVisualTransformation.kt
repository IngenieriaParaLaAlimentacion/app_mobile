package ar.com.ingenieriaalimentacion.core.design_system.components.input

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Renders a raw digit string (`DDMMYYYY`, up to 8 digits) as `DD/MM/AAAA`.
 *
 * The editing buffer must stay digits-only — the slashes live only in the
 * display layer. Because the underlying text never changes shape while typing,
 * the caret stays put (no jump-before-the-last-digit bug). The [OffsetMapping]
 * translates caret positions across the two inserted separators.
 */
class DateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter(Char::isDigit).take(8)
        val formatted = buildString {
            digits.forEachIndexed { i, c ->
                if (i == 2 || i == 4) append('/')
                append(c)
            }
        }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 4 -> offset + 1
                else -> offset + 2
            }

            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 5 -> offset - 1
                else -> offset - 2
            }
        }

        return TransformedText(AnnotatedString(formatted), mapping)
    }
}
