package ar.com.ingenieriaalimentacion.core.design_system.components.input

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Renders a raw digit string (`HHMM`, up to 4 digits) as `HH:MM`. Same contract as
 * [DateVisualTransformation]: the editing buffer stays digits-only and the colon lives only in the
 * display layer, so the caret never jumps while typing.
 */
class TimeVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter(Char::isDigit).take(4)
        val formatted = buildString {
            digits.forEachIndexed { i, c ->
                if (i == 2) append(':')
                append(c)
            }
        }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                if (offset <= 2) offset else offset + 1

            override fun transformedToOriginal(offset: Int): Int =
                if (offset <= 2) offset else offset - 1
        }

        return TransformedText(AnnotatedString(formatted), mapping)
    }
}
