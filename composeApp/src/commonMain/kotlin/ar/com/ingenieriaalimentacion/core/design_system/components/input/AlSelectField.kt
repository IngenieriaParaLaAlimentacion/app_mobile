package ar.com.ingenieriaalimentacion.core.design_system.components.input

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.komoui.components.Input
import com.komoui.themes.styles

/**
 * Design-system "picker" field. Looks identical to [AlInput] — same label,
 * border, error treatment — but is read-only and tappable instead of editable.
 * Use it for fields whose value is chosen on another screen or in a sheet
 * (e.g. the establishment selector), so picker fields and text fields stay
 * visually consistent.
 *
 * Reuses the KomoUI [Input] in [Input]'s `readOnly` mode (no keyboard) and
 * detects taps via the [MutableInteractionSource] press stream, firing [onClick].
 */
@Composable
fun AlSelectField(
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    error: String? = null,
    enabled: Boolean = true,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val styles = MaterialTheme.styles
    val interactionSource = remember { MutableInteractionSource() }

    if (enabled) {
        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { interaction ->
                if (interaction is PressInteraction.Release) {
                    onClick()
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = styles.foreground,
            )
        }
        Input(
            value = value,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder,
            enabled = enabled,
            readOnly = true,
            isError = error != null,
            singleLine = true,
            interactionSource = interactionSource,
            trailingIcon = trailingIcon ?: {
                Text(
                    text = "›",
                    color = styles.mutedForeground,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            supportingText = error?.let {
                { Text(text = it, color = styles.destructive, style = MaterialTheme.typography.bodySmall) }
            },
        )
    }
}
