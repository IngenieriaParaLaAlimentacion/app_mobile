package ar.com.ingenieriaalimentacion.core.design_system.components.card

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import com.komoui.components.Card
import com.komoui.components.Checkbox
import com.komoui.components.CheckboxDefaults

/**
 * Design-system selectable row card. Wraps the KomoUI [Card] (container, radius,
 * background) with a [Checkbox] selection affordance so any "pick one or more
 * from a list" surface (establishments, subjects, filters…) shares the same look
 * and toggle behavior.
 *
 * Built for multi-select: the whole card is clickable, the [Checkbox] reflects
 * [selected], and a selected card gets an accent border ring. For single-select
 * lists, drive [selected]/[onClick] so only one card is selected at a time.
 */
@Composable
fun AlSelectableCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    val radius = AlTheme.corners.lg
    val shape = RoundedCornerShape(radius)

    val cardModifier = modifier
        .fillMaxWidth()
        .clip(shape)
        .clickable(enabled = enabled) { onClick() }
        .then(
            if (selected) Modifier.border(1.5.dp, AlTheme.colors.accent, shape) else Modifier,
        )

    Card(modifier = cardModifier, radius = radius) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = AlTheme.typography.body,
                    color = AlTheme.colors.textBase,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = AlTheme.typography.caption,
                        color = AlTheme.colors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
                enabled = enabled,
                modifier = Modifier.padding(start = 12.dp),
                colors = CheckboxDefaults.colors().copy(
                    checkedColor = AlTheme.colors.accent,
                    checkedBorderColor = AlTheme.colors.accent,
                ),
            )
        }
    }
}
