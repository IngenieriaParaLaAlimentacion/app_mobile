package ar.com.ingenieriaalimentacion.core.design_system.components.segmented

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme

/**
 * A compact multi-segment toggle. The selected segment uses the brand accent
 * container/foreground; unselected segments stay muted on a transparent fill over
 * the muted track. Styling mirrors [ar.com.ingenieriaalimentacion.core.design_system.components.pill.AlPill]
 * and [ar.com.ingenieriaalimentacion.core.design_system.components.badge.AlStatusBadge]
 * (clip + tokenized container/foreground); no new tokens.
 *
 * The track is horizontally scrollable and segments size to their own label (with a minimum width
 * so a short label still reads as a tab). That is what keeps four Spanish labels — Sujetos |
 * Mediciones | Encuestas | Alimentos — legible on a narrow phone: they overflow into a scroll
 * instead of wrapping or being abbreviated.
 */
@Composable
fun AlSegmentedControl(
    segments: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AlTheme.corners.sm))
            .background(AlTheme.colors.surfaceMuted)
            .horizontalScroll(rememberScrollState())
            .padding(AlTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs),
    ) {
        segments.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    // No `weight`: a scrollable Row measures its children with infinite width
                    // constraints, which weights cannot resolve.
                    .widthIn(min = SEGMENT_MIN_WIDTH)
                    .clip(RoundedCornerShape(AlTheme.corners.sm))
                    .background(if (selected) AlTheme.colors.accentContainer else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = AlTheme.spacing.sm, vertical = AlTheme.spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = AlTheme.typography.body.copy(
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    ),
                    color = if (selected) AlTheme.colors.accent else AlTheme.colors.textMuted,
                )
            }
        }
    }
}

/** Wide enough that a short label ("Sujetos") still reads as a tab next to a long one. */
private val SEGMENT_MIN_WIDTH = 84.dp
