package ar.com.ingenieriaalimentacion.core.design_system.components.input

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.komoui.components.Input
import com.komoui.themes.styles

/**
 * Design-system text field. Wraps the KomoUI [Input] with an optional [label],
 * inline error [supportingText], and the IME scroll-into-view behavior from
 * CLAUDE.md (the [ScrollState] pattern, not `BringIntoViewRequester`, which is
 * unreliable on iOS in Compose Multiplatform).
 *
 * Pass [scrollState] + [isImeVisible] to enable auto-scroll-on-focus for fields
 * inside a scrollable, IME-affected form. Omit them for standalone inputs (e.g.
 * a search box) that don't need the behavior.
 */
@Composable
fun AlInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    error: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    scrollState: ScrollState? = null,
    isImeVisible: Boolean = false,
) {
    val styles = MaterialTheme.styles
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var fieldScrollOffset by remember { mutableStateOf(0) }

    if (scrollState != null) {
        LaunchedEffect(isFocused, isImeVisible) {
            if (isFocused && isImeVisible) {
                scrollState.animateScrollTo(fieldScrollOffset)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (scrollState != null) {
                    Modifier.onGloballyPositioned { coordinates ->
                        fieldScrollOffset = coordinates.boundsInRoot().top.toInt() + scrollState.value
                    }
                } else {
                    Modifier
                },
            ),
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
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder,
            enabled = enabled,
            isError = error != null,
            singleLine = singleLine,
            interactionSource = interactionSource,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            supportingText = error?.let {
                { Text(text = it, color = styles.destructive, style = MaterialTheme.typography.bodySmall) }
            },
        )
    }
}
