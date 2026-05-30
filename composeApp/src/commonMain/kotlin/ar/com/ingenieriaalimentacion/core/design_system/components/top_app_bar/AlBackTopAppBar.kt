package ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme

val AlAppBarHeight = 56.dp

/**
 * Standard top bar with a leading back button, an optional centered [title], and an
 * optional [trailing] slot for an action. The bar is full-bleed; place it as the first
 * child inside [AlScaffold] so the surrounding scaffold supplies the safe-area insets.
 */
@Composable
fun AlBackTopAppBar(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    enabled: Boolean = true,
    backContentDescription: String = "Atrás",
    trailing: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(AlAppBarHeight),
        contentAlignment = Alignment.Center,
    ) {
        title?.let {
            Text(
                text = it,
                style = AlTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                color = AlTheme.colors.textBase,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AlTheme.spacing.xxl),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .padding(start = AlTheme.spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onBackClick, enabled = enabled) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = backContentDescription,
                    tint = AlTheme.colors.textBase,
                )
            }
        }

        trailing?.let {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = AlTheme.spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                it()
            }
        }
    }
}
