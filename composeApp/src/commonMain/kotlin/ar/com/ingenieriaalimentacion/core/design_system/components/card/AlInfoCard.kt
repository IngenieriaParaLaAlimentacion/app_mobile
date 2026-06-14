package ar.com.ingenieriaalimentacion.core.design_system.components.card

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import com.komoui.components.Card
import com.komoui.themes.styles
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_right_line
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource

/**
 * Design-system info card: same container, radius and [title]/[description]
 * layout as [AlSelectableCard] but with no selection affordance. Use it for
 * read-only "here are your items" lists (e.g. accepted establishments) where the
 * checkbox of [AlSelectableCard] doesn't apply.
 *
 * Pass [onClick] to make the whole card tappable — a navigation chevron then
 * appears; leave it null for a purely informational card. Use [trailing] for an
 * end-aligned slot (e.g. a status [ar.com.ingenieriaalimentacion.core.design_system.components.pill.AlPill]);
 * it renders before the chevron.
 */
@Composable
fun AlInfoCard(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    leadingIcon: DrawableResource? = null,
    leadingIconTint: Color = AlTheme.colors.textMuted,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val styles = MaterialTheme.styles
    val radius = AlTheme.corners.lg
    val shape = RoundedCornerShape(radius)

    val cardModifier = modifier
        .fillMaxWidth()
        .clip(shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)

    Card(modifier = cardModifier, radius = radius) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.let {
                Icon(
                    imageVector = vectorResource(it),
                    contentDescription = null,
                    tint = leadingIconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = styles.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = styles.mutedForeground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke()
            if (onClick != null) {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_mingcute_right_line),
                    contentDescription = null,
                    tint = AlTheme.colors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
