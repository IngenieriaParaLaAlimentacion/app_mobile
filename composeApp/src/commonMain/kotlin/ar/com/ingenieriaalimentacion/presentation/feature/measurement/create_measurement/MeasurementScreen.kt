package ar.com.ingenieriaalimentacion.presentation.feature.measurement.create_measurement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.card.AlCard
import ar.com.ingenieriaalimentacion.core.design_system.components.dialog.AlConfirmDialog
import ar.com.ingenieriaalimentacion.core.design_system.components.dialog.AlConfirmTone
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlInput
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlSelectField
import ar.com.ingenieriaalimentacion.core.design_system.components.input.alInputScroll
import ar.com.ingenieriaalimentacion.core.design_system.components.list.AlListRow
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.sheet.AlBottomSheet
import ar.com.ingenieriaalimentacion.core.design_system.components.toast.AlToast
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary
import com.komoui.components.Spinner
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MeasurementScreenRoot(
    pickedSubjectResult: SubjectSummary?,
    onResultConsumed: () -> Unit,
    navigate: (MeasurementDestination) -> Unit,
    viewModel: MeasurementViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    LaunchedEffect(pickedSubjectResult) {
        pickedSubjectResult?.let {
            viewModel.setSubjectFromResult(it)
            onResultConsumed()
        }
    }

    MeasurementScreen(state = state, onAction = viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementScreen(
    state: MeasurementState,
    onAction: (MeasurementAction) -> Unit,
) {
    val scrollState = rememberScrollState()
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val sheetState = rememberModalBottomSheetState()
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.confirmation?.token, state.showMeasurementDatePicker, state.showMeasurementTimePicker) {
        if (state.confirmation != null || state.showMeasurementDatePicker || state.showMeasurementTimePicker) {
            keyboard?.hide()
        }
    }

    AlScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            AlBackTopAppBar(
                onBackClick = { onAction(MeasurementAction.BackClick) },
                title = "Cargar medición",
                enabled = !state.isSubmitting,
            )

            when {
                state.isLoadingProfile -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Spinner(size = 28.dp)
                }

                state.profileError != null -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(AlTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.profileError,
                        style = AlTheme.typography.body,
                        color = AlTheme.colors.textMuted,
                    )
                    AlButton(onClick = { onAction(MeasurementAction.RetryProfile) }) { Text("Reintentar") }
                }

                else -> MeasurementForm(
                    state = state,
                    onAction = onAction,
                    scrollState = scrollState,
                    isImeVisible = isImeVisible,
                )
            }
        }

        state.confirmation?.let { confirmation ->
            AlConfirmDialog(
                open = true,
                title = "Revisá las medidas",
                description = confirmation.description,
                confirmText = "Confirmar y guardar",
                cancelText = "Corregir",
                confirmTone = AlConfirmTone.Neutral,
                onConfirm = { onAction(MeasurementAction.ConfirmMeasurement(confirmation.token)) },
                onDismiss = { onAction(MeasurementAction.DismissConfirmation(confirmation.token)) },
            )
        }

        if (state.showMeasurementDatePicker) {
            val selected = state.measuredDateTime
            val maximum = state.latestAllowedMeasurementDate
            if (selected != null && maximum != null) {
                MeasurementDateDialog(
                    initialDate = selected.date,
                    maximumDate = maximum,
                    onSelected = { onAction(MeasurementAction.MeasurementDateSelected(it)) },
                    onDismiss = { onAction(MeasurementAction.MeasurementDatePickerDismiss) },
                )
            }
        }
        if (state.showMeasurementTimePicker) {
            state.measuredDateTime?.let { selected ->
                MeasurementTimeDialog(
                    initialTime = selected.time,
                    onSelected = { onAction(MeasurementAction.MeasurementTimeSelected(it)) },
                    onDismiss = { onAction(MeasurementAction.MeasurementTimePickerDismiss) },
                )
            }
        }

        if (state.showEstablishmentPicker) {
            AlBottomSheet(
                onDismissRequest = { onAction(MeasurementAction.EstablishmentPickerDismiss) },
                sheetState = sheetState,
            ) {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    Text(
                        text = "Elegí el establecimiento",
                        style = AlTheme.typography.body,
                        color = AlTheme.colors.textBase,
                        modifier = Modifier.padding(horizontal = AlTheme.spacing.md, vertical = AlTheme.spacing.sm),
                    )
                    state.establishmentOptions.forEach { establishment ->
                        AlListRow(
                            title = establishment.name,
                            subtitle = establishment.formattedAddress,
                            onClick = { onAction(MeasurementAction.EstablishmentSelected(establishment)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MeasurementForm(
    state: MeasurementState,
    onAction: (MeasurementAction) -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
    isImeVisible: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AlTheme.spacing.lg),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .alInputScroll(scrollState)
                    .padding(top = AlTheme.spacing.lg, bottom = AlTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SubjectRow(subject = state.subject, enabled = !state.isSubmitting, onChange = {
                    onAction(MeasurementAction.ChangeSubjectClick)
                })

                AlSelectField(
                    label = "Establecimiento",
                    value = state.selectedEstablishment?.name ?: "",
                    placeholder = "Elegí el establecimiento",
                    error = state.establishmentError,
                    enabled = !state.isSubmitting && state.establishmentOptions.isNotEmpty(),
                    onClick = { onAction(MeasurementAction.EstablishmentPickerOpen) },
                )
                if (state.establishmentOptions.isEmpty()) {
                    Text(
                        text = "No tenés establecimientos aprobados.",
                        style = AlTheme.typography.caption,
                        color = AlTheme.colors.textMuted,
                    )
                }

                AlInput(
                    label = "Peso (kg)",
                    value = state.weight.value,
                    onValueChange = { onAction(MeasurementAction.WeightChanged(it)) },
                    onFocusChange = { onAction(MeasurementAction.WeightFocusChanged(it)) },
                    error = state.weight.displayError,
                    enabled = !state.isSubmitting,
                    scrollState = scrollState,
                    isImeVisible = isImeVisible,
                    placeholder = "Ej. 72,5",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )

                AlInput(
                    label = "Talla (cm)",
                    value = state.height.value,
                    onValueChange = { onAction(MeasurementAction.HeightChanged(it)) },
                    onFocusChange = { onAction(MeasurementAction.HeightFocusChanged(it)) },
                    error = state.height.displayError,
                    enabled = !state.isSubmitting,
                    scrollState = scrollState,
                    isImeVisible = isImeVisible,
                    placeholder = "Ej. 170",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )

                Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                    AlInput(
                        label = "Observación (opcional)",
                        value = state.observation.value,
                        onValueChange = { onAction(MeasurementAction.ObservationChanged(it)) },
                        onFocusChange = { onAction(MeasurementAction.ObservationFocusChanged(it)) },
                        error = state.observation.displayError,
                        enabled = !state.isSubmitting,
                        singleLine = false,
                        scrollState = scrollState,
                        isImeVisible = isImeVisible,
                    )
                    Text(
                        text = "${state.observation.value.trim().length}/500",
                        color = AlTheme.colors.textMuted,
                        style = AlTheme.typography.caption,
                    )
                }

                val selected = state.measuredDateTime
                AlSelectField(
                    label = "Fecha de medición",
                    value = selected?.date?.let(::formatMeasurementDate).orEmpty(),
                    onClick = { onAction(MeasurementAction.MeasurementDatePickerOpen) },
                    enabled = !state.isSubmitting && state.confirmation == null,
                    error = state.measurementDateError,
                    required = true,
                )
                AlSelectField(
                    label = "Hora de medición",
                    value = selected?.time?.let(::formatMeasurementTime).orEmpty(),
                    onClick = { onAction(MeasurementAction.MeasurementTimePickerOpen) },
                    enabled = !state.isSubmitting && state.confirmation == null,
                    required = true,
                )
                Text(
                    "Fecha y hora de Argentina (Buenos Aires)",
                    color = AlTheme.colors.textMuted,
                    style = AlTheme.typography.caption,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = AlTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.submitError?.let { error ->
                    Text(text = error, color = AlTheme.colors.danger, style = AlTheme.typography.body)
                }
                AlButton(
                    onClick = { onAction(MeasurementAction.Submit) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.canSubmit,
                    loading = state.isSubmitting,
                    fullWidth = true,
                ) {
                    Text("Guardar")
                }
            }
        }

        AlToast(
            message = state.successMessage,
            onDismiss = { onAction(MeasurementAction.DismissSuccess) },
        )
    }
}

@Composable
private fun SubjectRow(
    subject: SubjectSummary?,
    enabled: Boolean,
    onChange: () -> Unit,
) {
    if (subject == null) {
        AlSelectField(
            label = "Sujeto",
            value = "",
            placeholder = "Seleccionar sujeto",
            enabled = enabled,
            onClick = onChange,
        )
    } else {
        AlListRow(
            // No inner horizontal inset here: the row lives inside the lg-padded
            // MeasurementForm, so it must align flush with the inputs/selects.
            horizontalPadding = 0.dp,
            title = "${subject.name} ${subject.lastName}",
            subtitle = "DNI ${subject.dni}",
            value = "Cambiar",
            onClick = if (enabled) onChange else null,
            showDivider = false,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasurementDateDialog(
    initialDate: LocalDate,
    maximumDate: LocalDate,
    onSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectable = remember(maximumDate) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                Instant.fromEpochMilliseconds(utcTimeMillis).toLocalDateTime(TimeZone.UTC).date <= maximumDate

            override fun isSelectableYear(year: Int): Boolean = year <= maximumDate.year
        }
    }
    val initialPickerDate = if (initialDate > maximumDate) maximumDate else initialDate
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = initialPickerDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        yearRange = 1..maximumDate.year,
        selectableDates = selectable,
    )
    val selectedDate = picker.selectedDateMillis?.let {
        Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date
    }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            AlButton(
                onClick = { selectedDate?.let(onSelected) },
                enabled = selectedDate != null && selectedDate <= maximumDate,
            ) { Text("Aceptar") }
        },
        dismissButton = {
            AlButton(onClick = onDismiss, variant = AlButtonVariant.Outline) { Text("Cancelar") }
        },
    ) { DatePicker(state = picker) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasurementTimeDialog(
    initialTime: LocalTime,
    onSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val picker = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true,
    )
    var useTextInput by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.padding(AlTheme.spacing.md)) {
            AlCard(modifier = Modifier.widthIn(max = 600.dp)) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.md),
                ) {
                    Text("Hora de medición", style = AlTheme.typography.subtitle, color = AlTheme.colors.textBase)
                    Text("Argentina (Buenos Aires)", style = AlTheme.typography.caption, color = AlTheme.colors.textMuted)
                    if (useTextInput) TimeInput(state = picker) else TimePicker(state = picker)
                    AlButton(onClick = { useTextInput = !useTextInput }, variant = AlButtonVariant.Outline) {
                        Text(if (useTextInput) "Usar reloj" else "Ingresar hora")
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                        AlButton(onClick = onDismiss, variant = AlButtonVariant.Outline) { Text("Cancelar") }
                        AlButton(onClick = { onSelected(LocalTime(picker.hour, picker.minute)) }) { Text("Aceptar") }
                    }
                }
            }
        }
    }
}
