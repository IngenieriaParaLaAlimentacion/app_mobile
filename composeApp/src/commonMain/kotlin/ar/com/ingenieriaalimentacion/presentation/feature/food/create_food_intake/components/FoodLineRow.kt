package ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_intake.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlIconButton
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlUnitInput
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_intake.FoodIntakeLine
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_delete

/**
 * Feature-local rather than a design-system component — it encodes *this* form's rules (the unit
 * comes from the catalog, the quantity is integer-only for `unidades`), not a general shape.
 * Removal is immediate, with no confirm dialog: re-adding the food costs two taps.
 */
@Composable
fun FoodLineRow(
    line: FoodIntakeLine,
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
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs),
    ) {
        Column {
            Text(
                text = line.food.name,
                style = AlTheme.typography.bodyStrong,
                color = AlTheme.colors.textBase,
            )
            Text(
                text = line.food.categoryName,
                style = AlTheme.typography.caption,
                color = AlTheme.colors.textMuted,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            AlUnitInput(
                modifier = Modifier.weight(1f),
                value = line.quantity.value,
                onValueChange = onQuantityChange,
                onFocusChange = onQuantityFocusChange,
                unit = line.food.unit.shortLabel,
                error = line.quantity.displayError,
                enabled = enabled,
                integerOnly = line.food.unit == FoodUnit.Unidades,
                placeholder = "Cantidad",
                scrollState = scrollState,
                isImeVisible = isImeVisible,
            )
            AlIconButton(
                icon = Res.drawable.ic_mingcute_delete,
                contentDescription = "Quitar ${line.food.name}",
                onClick = onRemove,
                tint = AlTheme.colors.danger,
                enabled = enabled,
            )
        }
    }
}
