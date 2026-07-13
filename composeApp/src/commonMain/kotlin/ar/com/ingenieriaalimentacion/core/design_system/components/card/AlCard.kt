package ar.com.ingenieriaalimentacion.core.design_system.components.card

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import com.komoui.components.Card

/**
 * Generic design-system surface: a single-slot card wrapping the KomoUI [Card]
 * (background + radius) with a thin border and internal [contentPadding]
 * (defaults to [AlTheme.spacing.md]). Use it to give list rows or grouped
 * content a discrete surface.
 *
 * Pass `contentPadding = 0.dp` when [content] is already self-padded (e.g. a
 * stack of [ar.com.ingenieriaalimentacion.core.design_system.components.list.AlListRow]s),
 * so the card doesn't add an extra inset on top of the rows' own spacing.
 *
 * [radius] defaults to [AlTheme.corners.lg]; pass a different value (e.g.
 * KomoUI's `MaterialTheme.radius.md`) to match a specific sibling shape, such
 * as [ar.com.ingenieriaalimentacion.core.design_system.components.input.AlInput]'s
 * rounded corners.
 */
@Composable
fun AlCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = AlTheme.spacing.md,
    radius: Dp = AlTheme.corners.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.border(
            AlTheme.borders.thin,
            AlTheme.colors.border,
            RoundedCornerShape(radius),
        ),
        radius = radius,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}
