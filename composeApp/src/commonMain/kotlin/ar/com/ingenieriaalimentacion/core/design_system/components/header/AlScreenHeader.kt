package ar.com.ingenieriaalimentacion.core.design_system.components.header

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.komoui.themes.styles

/**
 * Design-system screen header: a [title] and an optional [subtitle] with the
 * shared typography, color, and spacing used across the auth and form screens.
 * Centralizes the duplicated headline + supporting-text pair so every screen
 * renders identical heading UI.
 */
@Composable
fun AlScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val styles = MaterialTheme.styles
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = styles.foreground,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = styles.mutedForeground,
            )
        }
    }
}
