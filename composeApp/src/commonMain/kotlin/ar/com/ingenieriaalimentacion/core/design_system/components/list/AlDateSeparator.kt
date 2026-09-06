package ar.com.ingenieriaalimentacion.core.design_system.components.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme

/**
 * Day header for a grouped history list ("Hoy", "Ayer", "3 de septiembre").
 *
 * Meant to be hosted by `stickyHeader`, so it paints an opaque [AlTheme.colors.surfaceBase] fill —
 * without it the rows scrolling underneath would show through the pinned header. Set in mono
 * (`dataSmall`), the type scale's role for a stamped date.
 *
 * [horizontalPadding] mirrors [AlListRow]'s: pass `0.dp` inside a list that already applies its own
 * `contentPadding`, so the header aligns with the rows instead of doubling their inset.
 */
@Composable
fun AlDateSeparator(
    label: String,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = AlTheme.spacing.md,
    topPadding: Dp = AlTheme.spacing.lg,
    bottomPadding: Dp = AlTheme.spacing.sm,
) {
    Text(
        text = label,
        style = AlTheme.typography.dataSmall,
        color = AlTheme.colors.textMuted,
        modifier = modifier
            .fillMaxWidth()
            .background(AlTheme.colors.surfaceBase)
            .padding(
                start = horizontalPadding,
                end = horizontalPadding,
                top = topPadding,
                bottom = bottomPadding,
            ),
    )
}

@Preview
@Composable
private fun AlDateSeparatorPreview() {
    AlTheme {
        Column(modifier = Modifier.background(AlTheme.colors.surfaceBase)) {
            AlDateSeparator(label = "Hoy", topPadding = AlTheme.spacing.xs)
            AlListRow(title = "Encuesta de nutrición", subtitle = "Escuela 12", onClick = {})
            AlListRow(title = "Encuesta de higiene", subtitle = "Anónima", onClick = {}, showDivider = false)
            AlDateSeparator(label = "Ayer")
            AlListRow(title = "Encuesta de nutrición", subtitle = "Juan Pérez", onClick = {}, showDivider = false)
            AlDateSeparator(label = "3 de septiembre")
            AlListRow(title = "Encuesta de higiene", subtitle = "Escuela 4", onClick = {}, showDivider = false)
        }
    }
}
