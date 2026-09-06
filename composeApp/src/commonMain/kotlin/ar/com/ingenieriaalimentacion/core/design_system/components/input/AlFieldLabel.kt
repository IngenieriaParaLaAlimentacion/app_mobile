package ar.com.ingenieriaalimentacion.core.design_system.components.input

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme

/**
 * Shared field/question label: [text] in [AlTheme.colors.textBase], with the
 * required marker (when [required]) rendered in [AlTheme.colors.danger] so it
 * doesn't blend into the rest of the label. Single source of truth for the
 * label styling [AlInput], [AlSelectField], [AlChoiceGroup] and
 * [AlCheckboxGroup] all render.
 */
@Composable
fun AlFieldLabel(text: String, modifier: Modifier = Modifier, required: Boolean = false) {
    val danger = AlTheme.colors.danger
    val content = buildAnnotatedString {
        append(text)
        if (required) {
            withStyle(SpanStyle(color = danger)) { append(" *") }
        }
    }
    Text(
        text = content,
        style = AlTheme.typography.bodyStrong,
        color = AlTheme.colors.textBase,
        modifier = modifier,
    )
}
