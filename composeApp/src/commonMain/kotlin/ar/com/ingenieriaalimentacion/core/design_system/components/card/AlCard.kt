package ar.com.ingenieriaalimentacion.core.design_system.components.card

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import com.komoui.components.Card

/**
 * Generic design-system surface: a single-slot card wrapping the KomoUI [Card]
 * (background + radius) with a thin border and internal [AlTheme.spacing.md]
 * padding. Use it to give list rows or grouped content a discrete surface.
 */
@Composable
fun AlCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val radius = AlTheme.corners.lg
    Card(
        modifier = modifier.border(
            AlTheme.borders.thin,
            AlTheme.colors.border,
            RoundedCornerShape(radius),
        ),
        radius = radius,
    ) {
        Column(Modifier.padding(AlTheme.spacing.md), content = content)
    }
}
