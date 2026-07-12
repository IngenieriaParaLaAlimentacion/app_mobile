package ar.com.ingenieriaalimentacion.core.design_system.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.komoui.themes.styles

/** One selectable option shared by [AlChoiceGroup] and [AlCheckboxGroup]. */
data class AlChoiceOption(val id: String, val label: String)

/**
 * Design-system single-choice chip group. Generalizes `CreateSubjectScreen`'s hand-rolled
 * `SexSelector`/`SexOption` (clip/background/border/clickable visual treatment) into a reusable,
 * label-driven, N-option control — a [FlowRow] instead of `SexSelector`'s fixed two-`weight(1f)`
 * row, so longer survey option text wraps onto multiple lines instead of clipping.
 */
@Composable
fun AlChoiceGroup(
    label: String,
    options: List<AlChoiceOption>,
    selectedId: String?,
    onSelected: (String) -> Unit,
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            options.forEach { option ->
                ChoiceChip(
                    label = option.label,
                    selected = option.id == selectedId,
                    enabled = enabled,
                    hasError = error != null,
                    onClick = { onSelected(option.id) },
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

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    hasError: Boolean,
    onClick: () -> Unit,
) {
    val styles = MaterialTheme.styles
    val bg = if (selected) styles.primary else styles.background
    val fg = if (selected) styles.primaryForeground else styles.foreground
    val borderColor = when {
        selected -> styles.primary
        hasError -> styles.destructive
        else -> styles.border
    }
    Text(
        text = label,
        color = fg,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
