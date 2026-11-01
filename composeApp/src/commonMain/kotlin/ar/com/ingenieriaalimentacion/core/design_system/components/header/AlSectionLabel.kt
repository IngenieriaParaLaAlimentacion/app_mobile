package ar.com.ingenieriaalimentacion.core.design_system.components.header

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme

/**
 * Carries **no padding of its own**: the containers differ (a `Column` with
 * `Arrangement.spacedBy` on Inicio, a `LazyColumn` that cannot use an arrangement on
 * Alimentos), so spacing a section from its content is the parent's business. Pass it
 * through [modifier].
 */
@Composable
fun AlSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
) {
    if (onSeeAll == null) {
        SectionLabelText(text = text, modifier = modifier)
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabelText(text = text)
            Text(
                text = "Ver todo",
                style = AlTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                color = AlTheme.colors.textBase,
                modifier = Modifier.clickable(onClick = onSeeAll),
            )
        }
    }
}

@Composable
private fun SectionLabelText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = AlTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
        color = AlTheme.colors.textMuted,
    )
}
