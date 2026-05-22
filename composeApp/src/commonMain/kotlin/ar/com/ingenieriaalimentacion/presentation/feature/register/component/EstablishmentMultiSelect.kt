package ar.com.ingenieriaalimentacion.presentation.feature.register.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment
import com.komoui.components.Checkbox
import com.komoui.components.DropdownMenu
import com.komoui.components.DropdownMenuItem
import com.komoui.themes.styles

/**
 * KomoUI has no native multi-select, so this composes one from a clickable trigger that
 * opens a [DropdownMenu] of [Checkbox] rows. Selected names are summarised below the trigger.
 */
@Composable
fun EstablishmentMultiSelect(
    options: List<Establishment>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val styles = MaterialTheme.styles
    val borderColor = if (isError) styles.destructive else styles.border
    val selectedNames = options.filter { it.id in selectedIds }.map { it.name }
    val triggerLabel = when {
        selectedNames.isEmpty() -> "Seleccioná establecimientos"
        selectedNames.size == 1 -> selectedNames.first()
        else -> "${selectedNames.size} establecimientos seleccionados"
    }
    val labelColor = if (selectedNames.isEmpty()) styles.mutedForeground else styles.foreground

    Column(modifier = modifier.fillMaxWidth()) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            trigger = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        .clickable(enabled = enabled) { expanded = !expanded }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = triggerLabel,
                        color = labelColor,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (expanded) "▲" else "▼",
                        color = styles.mutedForeground,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
        ) {
            options.forEach { establishment ->
                DropdownMenuItem(onClick = { onToggle(establishment.id) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = establishment.id in selectedIds,
                            onCheckedChange = { onToggle(establishment.id) },
                        )
                        Text(
                            text = establishment.name,
                            color = styles.foreground,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
