package ar.com.ingenieriaalimentacion.core.design_system.components.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme

/**
 * Initials avatar. Sage is the palette's "soft accent, avatar" role, and ink on
 * sage keeps the initials readable where paper would sit at 3.3:1.
 *
 * There are no profile photos in the product, so this is deliberately
 * initials-only rather than a wrapper over an image-loading avatar.
 */
@Composable
fun AlAvatar(
    initials: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(AlTheme.colors.accentSoft, CircleShape)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        val fontSize = (size.value * 0.36).sp
        Text(
            text = initials,
            style = AlTheme.typography.bodyStrong.copy(
                fontSize = fontSize,
                lineHeight = fontSize * 1.2f,
            ),
            color = AlTheme.colors.textBase,
        )
    }
}
