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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme

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
    required: Boolean = false,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
        modifier = modifier.fillMaxWidth(),
    ) {
        AlFieldLabel(text = label, required = required)
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
                color = AlTheme.colors.danger,
                style = AlTheme.typography.caption,
            )
        }
    }
}

/**
 * Multiple-choice twin of [AlChoiceGroup]: the same chips, each toggled on its own. For short
 * option lists that read better inline than as an [AlCheckboxGroup] card (e.g. weekdays). [label]
 * is optional so the group can sit inside a row that already names it.
 */
@Composable
fun AlMultiChoiceGroup(
    label: String?,
    options: List<AlChoiceOption>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
        modifier = modifier.fillMaxWidth(),
    ) {
        label?.let { AlFieldLabel(text = it) }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            options.forEach { option ->
                ChoiceChip(
                    label = option.label,
                    selected = option.id in selectedIds,
                    enabled = enabled,
                    hasError = error != null,
                    onClick = { onToggle(option.id) },
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

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    hasError: Boolean,
    onClick: () -> Unit,
) {
    val colors = AlTheme.colors
    val bg = if (selected) colors.accent else colors.surfaceBase
    val fg = if (selected) colors.textInverted else colors.textBase
    val borderColor = when {
        selected -> colors.accent
        hasError -> colors.danger
        else -> colors.border
    }
    Text(
        text = label,
        color = fg,
        style = AlTheme.typography.body.copy(fontSize = 14.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
