package ar.com.ingenieriaalimentacion.core.design_system.components.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_right_line
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource

/**
 * Design-system list row: a [title] (with optional [subtitle]) plus an optional
 * leading icon, an optional trailing [value] text or [trailing] slot, and a
 * navigation chevron. Renders a hairline divider underneath by default so rows
 * stack into iOS-style grouped lists without needing a surrounding card.
 *
 * Pass [onClick] to make the row tappable (also enables a [showChevron] default
 * of `true`); leave it null for read-only info rows.
 */
@Composable
fun AlListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    leadingIcon: DrawableResource? = null,
    leadingIconTint: Color = AlTheme.colors.textMuted,
    titleColor: Color = AlTheme.colors.textBase,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    showDivider: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = AlTheme.spacing.md, vertical = AlTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
            ) {
                leadingIcon?.let {
                    Icon(
                        imageVector = vectorResource(it),
                        contentDescription = null,
                        tint = leadingIconTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs)) {
                    Text(
                        text = title,
                        style = AlTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                        color = titleColor,
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = AlTheme.typography.caption,
                            color = AlTheme.colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            value?.let {
                Text(
                    text = it,
                    modifier = Modifier.weight(1f),
                    style = AlTheme.typography.body,
                    color = AlTheme.colors.textMuted,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            trailing?.invoke(this)

            if (showChevron) {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_mingcute_right_line),
                    contentDescription = null,
                    tint = AlTheme.colors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AlTheme.spacing.md)
                    .height(AlTheme.borders.thin)
                    .background(AlTheme.colors.border),
            )
        }
    }
}
