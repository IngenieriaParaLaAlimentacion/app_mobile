package ar.com.ingenieriaalimentacion.core.design_system.components.input

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.com.ingenieriaalimentacion.core.design_system.components.card.AlCard
import ar.com.ingenieriaalimentacion.core.design_system.components.list.AlListRow
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import com.komoui.components.Checkbox
import com.komoui.components.CheckboxDefaults
import com.komoui.themes.radius

/**
 * Design-system multiple-choice group: a single [AlCard] holding one [AlListRow]
 * per option (hairline divider between rows, trailing [Checkbox]) so an N-option
 * checklist (e.g. a symptom list) reads as one grouped list instead of N stacked
 * cards with repeated chrome.
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
    required: Boolean = false,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
        modifier = modifier.fillMaxWidth(),
    ) {
        AlFieldLabel(text = label, required = required)
        AlCard(contentPadding = 0.dp, radius = MaterialTheme.radius.md) {
            options.forEachIndexed { index, option ->
                AlListRow(
                    title = option.label,
                    titleStyle = AlTheme.typography.body.copy(fontWeight = FontWeight.Normal, fontSize = 14.sp),
                    onClick = if (enabled) { { onToggle(option.id) } } else null,
                    showChevron = false,
                    showDivider = index != options.lastIndex,
                    trailing = {
                        Checkbox(
                            checked = option.id in selectedIds,
                            onCheckedChange = { onToggle(option.id) },
                            enabled = enabled,
                            colors = CheckboxDefaults.colors().copy(
                                checkedColor = AlTheme.colors.accent,
                                checkedBorderColor = AlTheme.colors.accent,
                            ),
                        )
                    },
                )
            }
        }
        error?.let {
            Text(
                text = it,
                color = AlTheme.colors.danger,
                style = AlTheme.typography.caption,
            )
        }
    }
}
