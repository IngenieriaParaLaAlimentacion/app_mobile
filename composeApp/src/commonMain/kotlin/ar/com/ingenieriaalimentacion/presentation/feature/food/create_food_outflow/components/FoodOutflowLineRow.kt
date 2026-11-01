package ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_outflow.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlIconButton
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlUnitInput
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import ar.com.ingenieriaalimentacion.presentation.common.formatQuantityWithUnit
import ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_outflow.FoodOutflowLine
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_delete

@Composable
fun FoodOutflowLineRow(
    line: FoodOutflowLine,
    enabled: Boolean,
    scrollState: ScrollState,
    isImeVisible: Boolean,
    onQuantityChange: (String) -> Unit,
    onQuantityFocusChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs),
            ) {
                Text(
                    text = line.stock.foodName,
                    style = AlTheme.typography.bodyStrong,
                    color = AlTheme.colors.textBase,
                )
                Text(
                    text = line.stock.categoryName,
                    style = AlTheme.typography.caption,
                    color = AlTheme.colors.textMuted,
                )
            }

            AlIconButton(
                icon = Res.drawable.ic_mingcute_delete,
                modifier = Modifier.size(48.dp),
                contentDescription = "Quitar ${line.stock.foodName}",
                onClick = onRemove,
                tint = AlTheme.colors.textMuted,
                enabled = enabled,
            )
        }
        Text(
            text = "Disponible: " + formatQuantityWithUnit(line.stock.quantity, line.stock.unit),
            style = AlTheme.typography.label,
            color = AlTheme.colors.textBase,
        )
        AlUnitInput(
            modifier = Modifier.fillMaxWidth(),
            value = line.quantity.value,
            onValueChange = onQuantityChange,
            onFocusChange = onQuantityFocusChange,
            unit = line.stock.unit.shortLabel,
            error = line.quantity.displayError,
            enabled = enabled,
            integerOnly = line.stock.unit == FoodUnit.Unidades,
            label = "Cantidad",
            scrollState = scrollState,
            isImeVisible = isImeVisible,
        )
    }
}
