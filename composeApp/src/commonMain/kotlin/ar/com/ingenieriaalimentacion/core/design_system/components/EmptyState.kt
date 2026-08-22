package ar.com.ingenieriaalimentacion.core.design_system.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme

/**
 * Centered title + caption shown when a list has no content. Shared across history
 * segments (subjects + measurements) so each empty state reads consistently.
 */
@Composable
fun EmptyState(
    title: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(AlTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
        ) {
            Text(
                text = title,
                style = AlTheme.typography.bodyStrong,
                color = AlTheme.colors.textBase,
            )
            Text(
                text = caption,
                style = AlTheme.typography.caption,
                color = AlTheme.colors.textMuted,
            )
        }
    }
}
