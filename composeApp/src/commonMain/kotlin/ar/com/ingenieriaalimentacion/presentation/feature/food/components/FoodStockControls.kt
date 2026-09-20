package ar.com.ingenieriaalimentacion.presentation.feature.food.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.food.StockSortOrder
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_down_line
import ingenieria_alimentacion.composeapp.generated.resources.ic_sort_order
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun FoodStockControls(
    categoryName: String?,
    filtered: Boolean,
    categoriesEnabled: Boolean,
    sortOrder: StockSortOrder,
    onCategoryClick: () -> Unit,
    onSortClick: () -> Unit,
    onClearCategory: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = AlTheme.spacing.md).padding(vertical = AlTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
            FilterChip(
                selected = filtered,
                onClick = onCategoryClick,
                enabled = categoriesEnabled || filtered,
                label = { Text(categoryName ?: if (filtered) "Categoría seleccionada" else "Todas las categorías") },
                trailingIcon = { PickerChevron() },
                modifier = Modifier.heightIn(min = 48.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AlTheme.colors.accentContainer,
                    selectedLabelColor = AlTheme.colors.accent,
                    selectedTrailingIconColor = AlTheme.colors.accent,
                ),
            )
            AssistChip(
                onClick = onSortClick,
                label = { Text("Ordenar") },
                leadingIcon = {
                    Icon(
                        imageVector = vectorResource(Res.drawable.ic_sort_order),
                        contentDescription = null,
                        tint = LocalContentColor.current,
                        modifier = Modifier.size(20.dp),
                    )
                },
                modifier = Modifier.heightIn(min = 48.dp),
            )
            if (filtered) {
                TextButton(
                    onClick = onClearCategory,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Quitar filtro", color = AlTheme.colors.accent) }
            }
        }
        Text(
            text = "Orden: ${sortOrder.label}",
            style = AlTheme.typography.caption,
            color = AlTheme.colors.textMuted,
        )
    }
}

@Composable
private fun PickerChevron() {
    Icon(
        imageVector = vectorResource(Res.drawable.ic_mingcute_down_line),
        contentDescription = null,
        tint = LocalContentColor.current,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
internal fun FoodSelectionOption(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) AlTheme.colors.accentContainer else AlTheme.colors.surfaceRaised)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = AlTheme.spacing.md, vertical = AlTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.md),
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = AlTheme.typography.body,
            color = if (selected) AlTheme.colors.accent else AlTheme.colors.textBase,
        )
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = AlTheme.colors.accent,
                unselectedColor = AlTheme.colors.textMuted,
            ),
        )
    }
}
