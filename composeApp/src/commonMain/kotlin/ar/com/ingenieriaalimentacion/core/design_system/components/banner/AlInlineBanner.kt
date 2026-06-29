package ar.com.ingenieriaalimentacion.core.design_system.components.banner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlIconButton
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_check_circle_fill
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_close_line
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource

/**
 * Soft, glanceable status banner for inline confirmations and notices. Unlike a
 * solid-fill bar, it uses the theme's muted `*Container` background with the
 * matching saturated colour for the icon and text, so it reads as a transient
 * note rather than a permanent brand surface.
 *
 * Title-only by default — keep [message] short and skip [onDismiss] for purely
 * informational banners. Pass [onDismiss] to render a quiet close affordance.
 */
@Composable
fun AlInlineBanner(
    message: String,
    modifier: Modifier = Modifier,
    variant: AlBannerVariant = AlBannerVariant.Success,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = AlTheme.colors
    val (container, accent, icon) = when (variant) {
        AlBannerVariant.Success -> Triple(
            colors.accentContainer,
            colors.accent,
            Res.drawable.ic_mingcute_check_circle_fill,
        )
        AlBannerVariant.Warning -> Triple(
            colors.warningContainer,
            colors.warning,
            Res.drawable.ic_mingcute_check_circle_fill,
        )
        AlBannerVariant.Danger -> Triple(
            colors.dangerContainer,
            colors.danger,
            Res.drawable.ic_mingcute_check_circle_fill,
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AlTheme.corners.lg))
            .background(container)
            .padding(start = 12.dp, end = if (onDismiss != null) 4.dp else 12.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = vectorResource(icon),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = message,
            style = AlTheme.typography.body,
            color = accent,
            modifier = Modifier.weight(1f),
        )
        if (onDismiss != null) {
            AlIconButton(
                icon = Res.drawable.ic_mingcute_close_line,
                contentDescription = "Cerrar",
                onClick = onDismiss,
                tint = accent,
            )
        }
    }
}

enum class AlBannerVariant { Success, Warning, Danger }
