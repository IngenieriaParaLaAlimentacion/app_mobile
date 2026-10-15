package ar.com.ingenieriaalimentacion.presentation.feature.food.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ar.com.ingenieriaalimentacion.core.design_system.components.card.AlSelectableCard
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlSelectField
import ar.com.ingenieriaalimentacion.core.design_system.components.segmented.AlSegmentedControl
import ar.com.ingenieriaalimentacion.core.design_system.components.sheet.AlBottomSheet
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.food.FoodIntakeOrigin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntakeProvenanceFields(
    origin: FoodIntakeOrigin?,
    isRecurring: Boolean?,
    originError: String?,
    recurrenceError: String?,
    enabled: Boolean,
    onOriginSelected: (FoodIntakeOrigin) -> Unit,
    onRecurrenceSelected: (Boolean) -> Unit,
) {
    var showOriginPicker by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.lg)) {
        AlSelectField(
            label = "Origen del alimento",
            required = true,
            value = origin?.label.orEmpty(),
            placeholder = "Seleccioná un origen",
            error = originError,
            enabled = enabled,
            onClick = { showOriginPicker = true },
        )
        Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
            Text("Tipo de aporte", style = AlTheme.typography.label, color = AlTheme.colors.textBase)
            AlSegmentedControl(
                segments = listOf("Único", "Recurrente"),
                selectedIndex = when (isRecurring) { false -> 0; true -> 1; null -> -1 },
                onSelect = { if (enabled) onRecurrenceSelected(it == 1) },
            )
            recurrenceError?.let {
                Text(it, style = AlTheme.typography.caption, color = AlTheme.colors.danger)
            }
        }
    }
    if (showOriginPicker) AlBottomSheet(onDismissRequest = { showOriginPicker = false }) {
        Column(
            modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(AlTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
        ) {
            Text("Origen del alimento", style = AlTheme.typography.subtitle,
                color = AlTheme.colors.textBase)
            FoodIntakeOrigin.entries.forEach { option ->
                AlSelectableCard(
                    title = option.label,
                    selected = origin == option,
                    onClick = { onOriginSelected(option); showOriginPicker = false },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
