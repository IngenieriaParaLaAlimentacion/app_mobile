package ar.com.ingenieriaalimentacion.core.design_system.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme

/**
 * Centered title + caption + retry button, for a recoverable failure with nothing to fall back on.
 * The structural twin of [EmptyState], with an action — full-surface rather than a toast, so it
 * cannot be missed, and the retry sits next to the explanation.
 *
 * Lifted verbatim from `FoodMovementHistoryContent`'s private `FoodMovementHistoryErrorState`,
 * which said in its own KDoc that it was the pattern to lift once it had shipped.
 *
 * Pass [onRetry] `null` for a terminal failure — the button disappears and the surface degrades to
 * an explanation, which is what a 404 "este establecimiento ya no existe" needs.
 */
@Composable
fun AlErrorState(
    title: String,
    caption: String,
    modifier: Modifier = Modifier,
    retryLabel: String = "Reintentar",
    onRetry: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize(), Alignment.Center) {
        Column(
            modifier = Modifier.padding(AlTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
        ) {
            Text(
                text = title,
                style = AlTheme.typography.bodyStrong,
                color = AlTheme.colors.textBase,
                textAlign = TextAlign.Center,
            )
            Text(
                text = caption,
                style = AlTheme.typography.caption,
                color = AlTheme.colors.textMuted,
                textAlign = TextAlign.Center,
            )
            onRetry?.let { AlButton(onClick = it) { Text(retryLabel) } }
        }
    }
}
