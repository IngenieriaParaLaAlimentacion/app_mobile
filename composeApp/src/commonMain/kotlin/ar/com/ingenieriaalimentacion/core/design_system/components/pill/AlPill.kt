package ar.com.ingenieriaalimentacion.core.design_system.components.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme

/**
 * Semantic tone for [AlPill], mapped to the design-system container/on-container
 * color pairs. [Success] reuses the brand [AlTheme.colors.accent] (which is green).
 */
enum class AlPillTone { Neutral, Success, Warning, Danger }

/**
 * Small rounded status pill — a pale container fill with its matching saturated
 * foreground text. Use for compact status labels (e.g. an establishment's
 * approval status) where a full row or icon would be too heavy.
 */
@Composable
fun AlPill(
    text: String,
    tone: AlPillTone,
    modifier: Modifier = Modifier,
) {
    val colors = AlTheme.colors
    val background: Color
    val content: Color
    when (tone) {
        AlPillTone.Neutral -> {
            background = colors.surfaceMuted
            content = colors.textMuted
        }
        AlPillTone.Success -> {
            background = colors.accentContainer
            content = colors.accent
        }
        AlPillTone.Warning -> {
            background = colors.warningContainer
            content = colors.warning
        }
        AlPillTone.Danger -> {
            background = colors.dangerContainer
            content = colors.danger
        }
    }

    Text(
        text = text,
        style = AlTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
        color = content,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .padding(horizontal = AlTheme.spacing.sm, vertical = AlTheme.spacing.xs),
    )
}
