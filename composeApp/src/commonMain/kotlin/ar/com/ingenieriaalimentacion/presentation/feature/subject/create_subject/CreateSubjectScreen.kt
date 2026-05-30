package ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.header.AlScreenHeader
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlInput
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import com.komoui.themes.styles
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CreateSubjectScreenRoot(
    viewModel: CreateSubjectViewModel = koinViewModel(),
    navigate: (CreateSubjectDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }
    CreateSubjectScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun CreateSubjectScreen(
    state: CreateSubjectState,
    onAction: (CreateSubjectAction) -> Unit,
) {
    val styles = MaterialTheme.styles
    val scrollState = rememberScrollState()
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(styles.background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AlScreenHeader(title = "Cargar sujeto")

        state.successMessage?.let { msg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(styles.primary)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = msg,
                    color = styles.primaryForeground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "×",
                    color = styles.primaryForeground,
                    modifier = Modifier.clickable { onAction(CreateSubjectAction.DismissSuccess) },
                )
            }
        }

        AlInput(
            label = "DNI",
            value = state.dni,
            onValueChange = { onAction(CreateSubjectAction.DniChanged(it)) },
            error = state.fieldErrors.dni,
            enabled = !state.isSubmitting,
            scrollState = scrollState,
            isImeVisible = isImeVisible,
            placeholder = "Sin puntos",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        SexSelector(
            selected = state.sex,
            onSelected = { onAction(CreateSubjectAction.SexChanged(it)) },
            error = state.fieldErrors.sex,
            enabled = !state.isSubmitting,
        )

        AlInput(
            label = "Nombre",
            value = state.name,
            onValueChange = { onAction(CreateSubjectAction.NameChanged(it)) },
            error = state.fieldErrors.name,
            enabled = !state.isSubmitting,
            scrollState = scrollState,
            isImeVisible = isImeVisible,
            placeholder = "Nombre del sujeto",
        )

        AlInput(
            label = "Apellido",
            value = state.lastName,
            onValueChange = { onAction(CreateSubjectAction.LastNameChanged(it)) },
            error = state.fieldErrors.lastName,
            enabled = !state.isSubmitting,
            scrollState = scrollState,
            isImeVisible = isImeVisible,
            placeholder = "Apellido del sujeto",
        )

        AlInput(
            label = "Fecha de nacimiento",
            value = state.birthDate,
            onValueChange = { onAction(CreateSubjectAction.BirthDateChanged(it)) },
            error = state.fieldErrors.birthDate,
            enabled = !state.isSubmitting,
            scrollState = scrollState,
            isImeVisible = isImeVisible,
            placeholder = "DD/MM/AAAA",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        ToggleRow(
            label = "Población rural",
            checked = state.isRural,
            enabled = !state.isSubmitting,
            onChange = { onAction(CreateSubjectAction.IsRuralChanged(it)) },
        )
        ToggleRow(
            label = "Población indígena",
            checked = state.isIndigenous,
            enabled = !state.isSubmitting,
            onChange = { onAction(CreateSubjectAction.IsIndigenousChanged(it)) },
        )

        state.submitError?.let { error ->
            Text(
                text = error,
                color = styles.destructive,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        AlButton(
            onClick = { onAction(CreateSubjectAction.Submit) },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSubmit,
            loading = state.isSubmitting,
            fullWidth = true,
        ) {
            Text("Guardar")
        }
    }
}

@Composable
private fun SexSelector(
    selected: Sex?,
    onSelected: (Sex) -> Unit,
    error: String?,
    enabled: Boolean,
) {
    val styles = MaterialTheme.styles
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Sexo",
            style = MaterialTheme.typography.labelLarge,
            color = styles.foreground,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SexOption(
                label = "Masculino",
                value = Sex.Male,
                selected = selected,
                enabled = enabled,
                hasError = error != null,
                modifier = Modifier.weight(1f),
                onSelected = onSelected,
            )
            SexOption(
                label = "Femenino",
                value = Sex.Female,
                selected = selected,
                enabled = enabled,
                hasError = error != null,
                modifier = Modifier.weight(1f),
                onSelected = onSelected,
            )
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
private fun SexOption(
    label: String,
    value: Sex,
    selected: Sex?,
    enabled: Boolean,
    hasError: Boolean,
    modifier: Modifier,
    onSelected: (Sex) -> Unit,
) {
    val styles = MaterialTheme.styles
    val isSelected = selected == value
    val bg = if (isSelected) styles.primary else styles.background
    val fg = if (isSelected) styles.primaryForeground else styles.foreground
    val borderColor = when {
        isSelected -> styles.primary
        hasError -> styles.destructive
        else -> styles.border
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onSelected(value) }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text = label, color = fg)
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = MaterialTheme.styles.foreground)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}