package ar.com.ingenieriaalimentacion.core.design_system.components.button

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme

@Composable
fun AlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Text(
        text = text,
        color = AlTheme.colors.textInverted,
        style = AlTheme.typography.body,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(AlTheme.corners.md))
            .background(AlTheme.colors.accent)
            .clickable(enabled = enabled) { onClick() }
            .padding(
                horizontal = AlTheme.spacing.lg,
                vertical = AlTheme.spacing.md,
            ),
    )
}
