package ar.com.ingenieriaalimentacion.core.design_system.components.button

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource

/**
 * Minimal tappable icon button: a 40dp circular touch target around a 20dp icon.
 * Use for quiet secondary actions (e.g. a trash icon) where a full [AlButton]
 * would over-emphasize the action. MingCute drawables are black fills, so a
 * [tint] is always applied.
 */
@Composable
fun AlIconButton(
    icon: DrawableResource,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AlTheme.colors.textMuted,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = vectorResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = if (enabled) tint else tint.copy(alpha = DISABLED_ALPHA),
        )
    }
}

private const val DISABLED_ALPHA = 0.38f
