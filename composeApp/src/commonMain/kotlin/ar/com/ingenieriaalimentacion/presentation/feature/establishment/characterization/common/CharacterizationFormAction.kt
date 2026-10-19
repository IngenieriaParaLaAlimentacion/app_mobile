package ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common

import ar.com.ingenieriaalimentacion.domain.model.establishment.Weekday

/**
 * User actions on the ficha form. Screens embedding [CharacterizationForm] wrap these in one action
 * of their own and hand them to [CharacterizationFormReducer].
 */
sealed interface CharacterizationFormAction {
    data class TextChanged(val field: CharacterizationTextField, val value: String) : CharacterizationFormAction
    data class TextFocusChanged(val field: CharacterizationTextField, val focused: Boolean) :
        CharacterizationFormAction

    data class OptionToggled(val field: CharacterizationMultiField, val wireValue: String) :
        CharacterizationFormAction

    /** Tapping the selected option again clears the answer, since every field is optional. */
    data class OptionSelected(val field: CharacterizationSingleField, val wireValue: String) :
        CharacterizationFormAction

    data object SlotAdded : CharacterizationFormAction
    data class SlotRemoved(val slotId: Int) : CharacterizationFormAction
    data class SlotDayToggled(val slotId: Int, val day: Weekday) : CharacterizationFormAction
    data class SlotTimeChanged(val slotId: Int, val bound: SlotBound, val value: String) :
        CharacterizationFormAction

    data class SlotTimeFocusChanged(val slotId: Int, val bound: SlotBound, val focused: Boolean) :
        CharacterizationFormAction
}

enum class SlotBound { Opens, Closes }
