package ar.com.ingenieriaalimentacion.core.design_system.components.button

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.komoui.components.Button
import com.komoui.components.ButtonVariant

/**
 * Design-system button variants. Re-exported from KomoUI so screens reference
 * the design system instead of importing KomoUI directly.
 */
typealias AlButtonVariant = ButtonVariant

/**
 * Design-system button. Thin wrapper over the KomoUI [Button] so every screen
 * imports a single canonical button from the design system instead of reaching
 * for KomoUI or raw Material directly.
 */
@Composable
fun AlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    fullWidth: Boolean = false,
    variant: AlButtonVariant = AlButtonVariant.Default,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        fullWidth = fullWidth,
        variant = variant,
        content = content,
    )
}
