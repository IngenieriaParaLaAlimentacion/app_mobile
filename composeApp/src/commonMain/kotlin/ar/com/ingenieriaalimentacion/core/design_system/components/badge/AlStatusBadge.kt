package ar.com.ingenieriaalimentacion.core.design_system.components.badge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme

enum class AlBadgeTone { Success, Warning, Danger, Neutral }

/**
 * Generic tokenized status pill. The tone→color mapping lives here; the
 * status→(label, tone) localization belongs to the calling feature layer.
 */
@Composable
fun AlStatusBadge(text: String, tone: AlBadgeTone, modifier: Modifier = Modifier) {
    val (bg, fg) = when (tone) {
        AlBadgeTone.Success -> AlTheme.colors.accentContainer to AlTheme.colors.accent
        AlBadgeTone.Warning -> AlTheme.colors.warningContainer to AlTheme.colors.warning
        AlBadgeTone.Danger -> AlTheme.colors.dangerContainer to AlTheme.colors.danger
        AlBadgeTone.Neutral -> AlTheme.colors.surfaceMuted to AlTheme.colors.textMuted
    }
    Box(
        modifier
            .clip(RoundedCornerShape(AlTheme.corners.sm))
            .background(bg)
            .padding(horizontal = AlTheme.spacing.sm, vertical = AlTheme.spacing.xs),
    ) {
        Text(text, style = AlTheme.typography.caption, color = fg)
    }
}
