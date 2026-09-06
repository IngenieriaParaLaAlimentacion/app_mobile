package ar.com.ingenieriaalimentacion.core.design_system.components.input

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme

/**
 * Numeric field with a fixed, non-editable unit suffix (kg / L / u). The unit is a property of the
 * value, never a choice — the food catalog decides it — so it is rendered *inside* the field in the
 * mono data face rather than in a selector next to it.
 *
 * [integerOnly] picks `KeyboardType.Number` (no decimal separator) over `Decimal`, which is how a
 * `unidades` food keeps fractional quantities off the keyboard in the first place.
 *
 * Forwards [scrollState] / [isImeVisible] to [AlInput] so the IME scroll-into-view behavior works
 * unchanged for a dynamic list of these inside a scrolling form.
 */
@Composable
fun AlUnitInput(
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    error: String? = null,
    enabled: Boolean = true,
    integerOnly: Boolean = false,
    scrollState: ScrollState? = null,
    isImeVisible: Boolean = false,
    onFocusChange: (Boolean) -> Unit = {},
) {
    AlInput(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        error = error,
        enabled = enabled,
        keyboardOptions = if (integerOnly) IntegerKeyboard else DecimalKeyboard,
        trailingIcon = {
            Text(
                text = unit,
                style = AlTheme.typography.data,
                color = AlTheme.colors.textMuted,
            )
        },
        scrollState = scrollState,
        isImeVisible = isImeVisible,
        onFocusChange = onFocusChange,
    )
}

private val IntegerKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)
private val DecimalKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)
