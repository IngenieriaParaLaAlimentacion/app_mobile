package ar.com.ingenieriaalimentacion.core.design_system.components.button

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource

/**
 * Design-system tappable action tile: same container, radius and padding as a
 * stat tile, with an icon over a [label] in a centered column instead of a
 * value. Use it for a row of quick actions that should carry the same visual
 * weight as neighboring stat tiles, where a bare [AlIconButton] would read as
 * less important. The visible [label] carries the accessible name, so the icon
 * itself stays decorative.
 */
@Composable
fun AlCardIconButton(
    icon: DrawableResource,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = AlTheme.colors.surfaceMuted,
    iconTint: Color = AlTheme.colors.textBase,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AlTheme.corners.lg))
            .background(container)
            .clickable(onClick = onClick)
            .padding(vertical = AlTheme.spacing.md, horizontal = AlTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs),
    ) {
        Icon(
            imageVector = vectorResource(icon),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Text(text = label, style = AlTheme.typography.caption, color = AlTheme.colors.textMuted)
    }
}
