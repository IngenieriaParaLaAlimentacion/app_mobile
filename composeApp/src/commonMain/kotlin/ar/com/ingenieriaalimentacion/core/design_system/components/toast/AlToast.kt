package ar.com.ingenieriaalimentacion.core.design_system.components.toast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.components.banner.AlBannerVariant
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_check_circle_fill
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.vectorResource

private const val VISIBLE_DURATION_MS = 1_000L

/**
 * Floating, self-dismissing confirmation toast for success feedback after an
 * action (e.g. an upload). Unlike [ar.com.ingenieriaalimentacion.core.design_system.components.banner.AlInlineBanner],
 * it overlays the screen instead of living inline in scrollable content, so it
 * stays visible regardless of scroll position, and it always auto-dismisses —
 * there is no manual close affordance. Doing several contiguous submits (e.g.
 * uploading multiple measurements in a row) re-triggers the toast each time
 * [message] goes from null to a new value.
 */
@Composable
fun BoxScope.AlToast(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AlBannerVariant = AlBannerVariant.Success,
) {
    var visible by remember { mutableStateOf(false) }
    var displayedMessage by remember { mutableStateOf("") }

    LaunchedEffect(message) {
        if (message != null) {
            displayedMessage = message
            visible = true
            delay(VISIBLE_DURATION_MS)
            visible = false
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier
            .align(Alignment.TopCenter)
            .padding(horizontal = AlTheme.spacing.lg, vertical = AlTheme.spacing.md),
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        val colors = AlTheme.colors
        val (container, accent) = when (variant) {
            AlBannerVariant.Success -> colors.accentContainer to colors.accent
            AlBannerVariant.Warning -> colors.warningContainer to colors.warning
            AlBannerVariant.Danger -> colors.dangerContainer to colors.danger
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AlTheme.corners.lg))
                .background(container)
                .border(AlTheme.borders.thin, accent.copy(alpha = 0.25f), RoundedCornerShape(AlTheme.corners.lg))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_mingcute_check_circle_fill),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = displayedMessage,
                style = AlTheme.typography.body,
                color = accent,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
