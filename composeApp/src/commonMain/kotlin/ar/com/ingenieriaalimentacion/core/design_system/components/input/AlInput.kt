package ar.com.ingenieriaalimentacion.core.design_system.components.input

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.text.input.VisualTransformation
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import com.komoui.components.Input
import kotlin.math.roundToInt

/**
 * Design-system text field. Wraps the KomoUI [Input] with an optional [label],
 * inline error [supportingText], and the IME scroll-into-view behavior from
 * CLAUDE.md (the [ScrollState] pattern, not `BringIntoViewRequester`, which is
 * unreliable on iOS in Compose Multiplatform).
 *
 * Pass [scrollState] + [isImeVisible] to enable auto-scroll-on-focus for fields
 * inside a form using [alInputScroll]. Omit them for standalone inputs (e.g.
 * a search box) that don't need the behavior.
 *
 * [onFocusChange] reports focus gained (`true`) / lost (`false`). The initial
 * unfocused state is never reported, so a blur callback only fires after the
 * field has actually been focused — feed it into a [TextFieldState.onFocus] to
 * drive blur-aware validation.
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
    required: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    scrollState: ScrollState? = null,
    isImeVisible: Boolean = false,
    onFocusChange: (Boolean) -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var fieldCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var scrollViewport by remember { mutableStateOf<InputScrollViewport?>(null) }
    var hasFocusedOnce by remember { mutableStateOf(false) }

    LaunchedEffect(isFocused) {
        if (isFocused) hasFocusedOnce = true
        // Skip the initial unfocused state; only report real focus/blur transitions.
        if (hasFocusedOnce) onFocusChange(isFocused)
    }

    if (scrollState != null) {
        val viewportCoordinates = scrollViewport?.coordinates
        LaunchedEffect(isFocused, isImeVisible, scrollState, fieldCoordinates, viewportCoordinates) {
            val field = fieldCoordinates
            val viewport = viewportCoordinates
            if (
                isFocused && isImeVisible && scrollViewport?.scrollState === scrollState &&
                field != null && field.isAttached && viewport != null && viewport.isAttached
            ) {
                // Use unclipped coordinates relative to the viewport. Root bounds include
                // the toolbar/safe area and would scroll the focused field above the form.
                val offset = viewport.localPositionOf(field, Offset.Zero).y + scrollState.value
                scrollState.animateScrollTo(offset.roundToInt().coerceAtLeast(0))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (scrollState != null) {
                    Modifier
                        .modifierLocalConsumer { scrollViewport = ModifierLocalInputScrollViewport.current }
                        .onGloballyPositioned { fieldCoordinates = it }
                } else {
                    Modifier
                },
            ),
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
    ) {
        if (label != null) {
            AlFieldLabel(text = label, required = required)
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
            trailingIcon = trailingIcon,
            supportingText = error?.let {
                { Text(text = it, color = AlTheme.colors.danger, style = AlTheme.typography.caption) }
            },
        )
    }
}
