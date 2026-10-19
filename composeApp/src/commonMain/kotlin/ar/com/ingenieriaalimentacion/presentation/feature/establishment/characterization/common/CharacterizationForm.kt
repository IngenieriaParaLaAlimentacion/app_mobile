package ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlIconButton
import ar.com.ingenieriaalimentacion.core.design_system.components.card.AlCard
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlCheckboxGroup
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlChoiceGroup
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlChoiceOption
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlFieldLabel
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlInput
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlMultiChoiceGroup
import ar.com.ingenieriaalimentacion.core.design_system.components.input.TimeVisualTransformation
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationOption
import ar.com.ingenieriaalimentacion.domain.model.establishment.Weekday
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_delete
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationMultiField as Multi
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationSingleField as Single
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationTextField as TextField

/**
 * The ficha form body: five sections, every field optional. It renders no scroll container or
 * submit button of its own — the host screen places it inside its scrolling column (pass that
 * column's [scrollState] so focused inputs scroll above the keyboard) and owns the actions.
 */
@Composable
fun CharacterizationForm(
    state: CharacterizationFormState,
    onAction: (CharacterizationFormAction) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    scrollState: ScrollState? = null,
    isImeVisible: Boolean = false,
) {
    val scope = FormScope(state, onAction, enabled, scrollState, isImeVisible)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.lg),
    ) {
        Text(
            text = CharacterizationCopy.FORM_HINT,
            style = AlTheme.typography.body,
            color = AlTheme.colors.textMuted,
        )

        Section(CharacterizationCopy.SECTION_LOCATION) {
            scope.TextInput(TextField.District)
            scope.TextInput(TextField.Neighborhood)
            scope.TextInput(TextField.CrossStreets, placeholder = CharacterizationCopy.CROSS_STREETS_PLACEHOLDER)
            Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs)) {
                scope.TextInput(TextField.ContactPhone, keyboardType = KeyboardType.Phone)
                Text(
                    text = CharacterizationCopy.PRIVATE_HINT,
                    style = AlTheme.typography.caption,
                    color = AlTheme.colors.textMuted,
                )
            }
            scope.TextInput(TextField.ContactEmail, keyboardType = KeyboardType.Email)
            scope.OpeningHours()
        }

        Section(CharacterizationCopy.SECTION_SPACE) {
            scope.Multi(Multi.SpaceTypes)
            scope.Multi(Multi.CareTasks)
            scope.Single(Single.Premises)
            scope.Single(Single.Bathroom)
            scope.Multi(Multi.Utilities)
            scope.Multi(Multi.Affiliations)
        }

        Section(CharacterizationCopy.SECTION_ATTENDEES) {
            scope.TextInput(TextField.DailyAttendance, keyboardType = KeyboardType.Number)
            scope.Multi(Multi.AttendeeAges)
        }

        Section(CharacterizationCopy.SECTION_FOOD) {
            scope.Multi(Multi.Meals)
            scope.Multi(Multi.ServiceTypes)
            scope.Single(Single.FoodReceptionFrequency)
            scope.TextInput(TextField.CooksPerService, keyboardType = KeyboardType.Number)
            scope.Multi(Multi.KitchenEquipment)
        }

        Section(CharacterizationCopy.SECTION_COMMUNITY) {
            scope.Multi(Multi.CulturalIdentities)
        }
    }
}

private class FormScope(
    val state: CharacterizationFormState,
    val onAction: (CharacterizationFormAction) -> Unit,
    val enabled: Boolean,
    val scrollState: ScrollState?,
    val isImeVisible: Boolean,
) {
    @Composable
    fun TextInput(
        field: TextField,
        label: String? = field.label,
        placeholder: String = "",
        keyboardType: KeyboardType = KeyboardType.Text,
    ) {
        AlInput(
            label = label,
            value = state.text(field).value,
            onValueChange = { onAction(CharacterizationFormAction.TextChanged(field, it)) },
            onFocusChange = { onAction(CharacterizationFormAction.TextFocusChanged(field, it)) },
            error = state.displayError(field),
            enabled = enabled,
            placeholder = placeholder,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            scrollState = scrollState,
            isImeVisible = isImeVisible,
        )
    }

    @Composable
    fun Multi(field: Multi) {
        Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
            AlCheckboxGroup(
                label = field.label,
                options = field.options.toChoiceOptions(),
                selectedIds = state.selected(field),
                onToggle = { onAction(CharacterizationFormAction.OptionToggled(field, it)) },
                error = state.serverError(field.wireName),
                enabled = enabled,
            )
            field.other?.takeIf { state.showsOther(field) }?.let { Other(it) }
        }
    }

    @Composable
    fun Single(field: Single) {
        Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
            AlChoiceGroup(
                label = field.label,
                options = field.options.toChoiceOptions(),
                selectedId = state.selected(field),
                onSelected = { onAction(CharacterizationFormAction.OptionSelected(field, it)) },
                error = state.serverError(field.wireName),
                enabled = enabled,
            )
            field.other?.takeIf { state.showsOther(field) }?.let { Other(it) }
        }
    }

    @Composable
    private fun Other(field: TextField) {
        TextInput(field, label = null, placeholder = CharacterizationCopy.OTHER_PLACEHOLDER)
    }

    @Composable
    fun OpeningHours() {
        Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
            AlFieldLabel(text = CharacterizationCopy.OPENING_HOURS)
            Text(
                text = CharacterizationCopy.OPENING_HOURS_HINT,
                style = AlTheme.typography.caption,
                color = AlTheme.colors.textMuted,
            )
            state.openingHours.forEachIndexed { index, slot -> Slot(index, slot) }
            state.serverError(CharacterizationFormReducer.OPENING_HOURS)?.let {
                Text(text = it, style = AlTheme.typography.caption, color = AlTheme.colors.danger)
            }
            AlButton(
                onClick = { onAction(CharacterizationFormAction.SlotAdded) },
                enabled = enabled,
                variant = AlButtonVariant.Outline,
            ) {
                Text(CharacterizationCopy.ADD_SLOT)
            }
        }
    }

    @Composable
    private fun Slot(index: Int, slot: OpeningHoursSlotState) {
        AlCard {
            Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = CharacterizationCopy.slotLabel(index + 1),
                        style = AlTheme.typography.bodyStrong,
                        color = AlTheme.colors.textBase,
                    )
                    AlIconButton(
                        icon = Res.drawable.ic_mingcute_delete,
                        contentDescription = "${CharacterizationCopy.REMOVE_SLOT} ${index + 1}",
                        onClick = { onAction(CharacterizationFormAction.SlotRemoved(slot.id)) },
                        enabled = enabled,
                    )
                }
                AlMultiChoiceGroup(
                    label = null,
                    options = DAY_OPTIONS,
                    selectedIds = slot.days.mapTo(mutableSetOf()) { it.wireValue },
                    onToggle = { wire ->
                        Weekday.entries.firstOrNull { it.wireValue == wire }
                            ?.let { onAction(CharacterizationFormAction.SlotDayToggled(slot.id, it)) }
                    },
                    enabled = enabled,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                    Time(slot, SlotBound.Opens, Modifier.weight(1f))
                    Time(slot, SlotBound.Closes, Modifier.weight(1f))
                }
                slot.displayError?.let {
                    Text(text = it, style = AlTheme.typography.caption, color = AlTheme.colors.danger)
                }
            }
        }
    }

    @Composable
    private fun Time(slot: OpeningHoursSlotState, bound: SlotBound, modifier: Modifier) {
        val field = if (bound == SlotBound.Opens) slot.opensAt else slot.closesAt
        AlInput(
            label = if (bound == SlotBound.Opens) CharacterizationCopy.OPENS_AT else CharacterizationCopy.CLOSES_AT,
            value = field.value,
            onValueChange = { onAction(CharacterizationFormAction.SlotTimeChanged(slot.id, bound, it)) },
            onFocusChange = { onAction(CharacterizationFormAction.SlotTimeFocusChanged(slot.id, bound, it)) },
            error = field.displayError,
            enabled = enabled,
            placeholder = CharacterizationCopy.TIME_PLACEHOLDER,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = TimeVisualTransformation(),
            scrollState = scrollState,
            isImeVisible = isImeVisible,
            modifier = modifier,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.md)) {
        Text(text = title, style = AlTheme.typography.subtitle, color = AlTheme.colors.textBase)
        content()
    }
}

// Three-letter names: one-letter initials would make martes and miércoles both "M".
private val DAY_OPTIONS = Weekday.entries.map { AlChoiceOption(id = it.wireValue, label = it.label.take(3)) }

private fun List<CharacterizationOption>.toChoiceOptions(): List<AlChoiceOption> =
    map { AlChoiceOption(id = it.wireValue, label = it.label) }
