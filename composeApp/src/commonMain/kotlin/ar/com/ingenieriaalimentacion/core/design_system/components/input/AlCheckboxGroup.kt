package ar.com.ingenieriaalimentacion.core.design_system.components.input

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.components.card.AlSelectableCard
import com.komoui.themes.styles

/**
 * Design-system multiple-choice group: a vertical list of [AlSelectableCard] rows (reusing its
 * existing `Checkbox` selection affordance rather than a new checkbox primitive), each toggling
 * independently in [selectedIds].
 */
@Composable
fun AlCheckboxGroup(
    label: String,
    options: List<AlChoiceOption>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
) {
    val styles = MaterialTheme.styles
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = styles.foreground,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                AlSelectableCard(
                    title = option.label,
                    selected = option.id in selectedIds,
                    onClick = { onToggle(option.id) },
                    enabled = enabled,
                )
            }
        }
        error?.let {
            Text(
                text = it,
                color = styles.destructive,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
