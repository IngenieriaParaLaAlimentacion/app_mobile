package ar.com.ingenieriaalimentacion.core.design_system.components.input

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.komoui.themes.styles
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_eye_close_line
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_eye_line
import org.jetbrains.compose.resources.vectorResource

/**
 * Design-system password field. Wraps [AlInput] with a built-in show/hide
 * toggle: an eye icon in the trailing slot flips the [VisualTransformation]
 * between masked and plain text. Keeps the same label, error, and IME
 * scroll-into-view behavior as every other [AlInput].
 */
@Composable
fun AlPasswordInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    error: String? = null,
    enabled: Boolean = true,
    scrollState: ScrollState? = null,
    isImeVisible: Boolean = false,
) {
    val styles = MaterialTheme.styles
    var passwordVisible by remember { mutableStateOf(false) }

    AlInput(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        error = error,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            Icon(
                imageVector = vectorResource(
                    if (passwordVisible) {
                        Res.drawable.ic_mingcute_eye_close_line
                    } else {
                        Res.drawable.ic_mingcute_eye_line
                    },
                ),
                contentDescription = if (passwordVisible) {
                    "Ocultar contraseña"
                } else {
                    "Mostrar contraseña"
                },
                tint = styles.mutedForeground,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = enabled,
                    ) { passwordVisible = !passwordVisible },
            )
        },
        scrollState = scrollState,
        isImeVisible = isImeVisible,
    )
}
