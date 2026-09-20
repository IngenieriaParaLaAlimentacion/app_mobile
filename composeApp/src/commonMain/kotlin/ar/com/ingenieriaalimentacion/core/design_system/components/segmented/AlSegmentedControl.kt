package ar.com.ingenieriaalimentacion.core.design_system.components.segmented

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme

/**
 * A compact multi-segment toggle. The selected segment uses the brand accent
 * container/foreground; unselected segments stay muted on a transparent fill over
 * the muted track. Styling mirrors [ar.com.ingenieriaalimentacion.core.design_system.components.pill.AlPill]
 * and [ar.com.ingenieriaalimentacion.core.design_system.components.badge.AlStatusBadge]
 * (clip + tokenized container/foreground); no new tokens.
 *
 * Segments size to their own label and the track scrolls horizontally when they do not fit. That
 * is what keeps four Spanish labels — Sujetos | Mediciones | Encuestas | Alimentos — legible on a
 * narrow phone: they overflow into a scroll instead of wrapping or being abbreviated.
 *
 * When they *do* fit, the leftover track is split evenly between the segments. `Modifier.weight`
 * cannot do that here — a scrollable row measures its children against infinite width, which
 * weights cannot resolve — so the track's real width is captured outside the scroll
 * ([BoxWithConstraints]) and handed out as an equal share per segment in a small [Layout].
 */
@Composable
fun AlSegmentedControl(
    segments: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gap = AlTheme.spacing.xs
    val inset = AlTheme.spacing.xs
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AlTheme.corners.sm))
            .background(AlTheme.colors.surfaceMuted),
    ) {
        val trackWidth = maxWidth - inset * 2
        Layout(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(inset),
            content = {
                segments.forEachIndexed { index, label ->
                    val selected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .widthIn(min = SEGMENT_MIN_WIDTH)
                            .clip(RoundedCornerShape(AlTheme.corners.sm))
                            .background(
                                if (selected) AlTheme.colors.accentContainer else Color.Transparent,
                            )
                            .clickable { onSelect(index) }
                            .padding(horizontal = AlTheme.spacing.sm, vertical = AlTheme.spacing.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            // `label`, not `body`: a tab is a control, not content, and the
                            // weight — not the size — is what carries the selection.
                            style = AlTheme.typography.label.copy(
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            ),
                            color = if (selected) AlTheme.colors.accent else AlTheme.colors.textMuted,
                        )
                    }
                }
            },
        ) { measurables, constraints ->
            if (measurables.isEmpty()) return@Layout layout(0, 0) {}
            val gapPx = gap.roundToPx()
            val gaps = gapPx * (measurables.size - 1)
            // Intrinsics, not a trial measure: a Measurable may only be measured once per pass,
            // and the natural widths are needed before the final width of each segment is known.
            val natural = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }
            val leftover = (trackWidth.roundToPx() - gaps - natural.sum()).coerceAtLeast(0)
            val share = leftover / measurables.size
            // The integer division loses up to n-1 px; the first segments absorb it so the row
            // ends exactly on the track's edge rather than a hair short of it.
            val remainder = leftover % measurables.size
            val widths = natural.mapIndexed { index, width ->
                width + share + if (index < remainder) 1 else 0
            }
            val placeables = measurables.mapIndexed { index, measurable ->
                measurable.measure(
                    Constraints(
                        minWidth = widths[index],
                        maxWidth = widths[index],
                        maxHeight = constraints.maxHeight,
                    ),
                )
            }
            val height = placeables.maxOf { it.height }
            layout(widths.sum() + gaps, height) {
                var x = 0
                placeables.forEach { placeable ->
                    placeable.place(x, Alignment.CenterVertically.align(placeable.height, height))
                    x += placeable.width + gapPx
                }
            }
        }
    }
}

/** Wide enough that a short label ("Sujetos") still reads as a tab next to a long one. */
private val SEGMENT_MIN_WIDTH = 84.dp
