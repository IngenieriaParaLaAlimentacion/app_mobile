package ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common

import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.domain.model.establishment.Weekday

/**
 * State of the ficha form. Options are held as wire values so one generic composable renders every
 * group; [toCharacterization] turns them back into the domain enums.
 *
 * [serverInvalidField] is the `wireName` of the field a 422 named. It overrides that field's error
 * until the user changes it, so the form flags the input without clearing anything.
 */
data class CharacterizationFormState(
    val texts: Map<CharacterizationTextField, TextFieldState> = emptyMap(),
    val multi: Map<CharacterizationMultiField, Set<String>> = emptyMap(),
    val single: Map<CharacterizationSingleField, String> = emptyMap(),
    val openingHours: List<OpeningHoursSlotState> = emptyList(),
    val nextSlotId: Int = 0,
    val serverInvalidField: String? = null,
) {
    fun text(field: CharacterizationTextField): TextFieldState = texts[field] ?: TextFieldState()

    fun selected(field: CharacterizationMultiField): Set<String> = multi[field].orEmpty()

    fun selected(field: CharacterizationSingleField): String? = single[field]

    /** The "Otro" input is shown while its field holds "Otro". */
    fun showsOther(field: CharacterizationMultiField): Boolean =
        field.options.any { it.isOther && it.wireValue in selected(field) }

    fun showsOther(field: CharacterizationSingleField): Boolean =
        field.options.any { it.isOther && it.wireValue == selected(field) }

    fun displayError(field: CharacterizationTextField): String? =
        serverError(field.wireName) ?: text(field).displayError

    /** The server error for a field that has no client-side validation (options, hours). */
    fun serverError(wireName: String): String? =
        CharacterizationCopy.SERVER_INVALID.takeIf { serverInvalidField == wireName }

    /**
     * Client-side errors only: a server-flagged field doesn't block a retry, since the user may
     * have fixed it in a way the client can't check. A hidden "Otro" text doesn't count either —
     * it is dropped on save.
     */
    val isValid: Boolean
        get() = texts.all { (field, value) -> !isShown(field) || value.isValid } &&
            openingHours.all { it.isValid }

    private fun isShown(field: CharacterizationTextField): Boolean {
        CharacterizationMultiField.entries.firstOrNull { it.other == field }?.let { return showsOther(it) }
        CharacterizationSingleField.entries.firstOrNull { it.other == field }?.let { return showsOther(it) }
        return true
    }
}

/**
 * One opening-hours row. Times are held as raw `HHMM` digits (rendered as `HH:MM` by
 * `TimeVisualTransformation`). [id] keys the row in the UI and in actions, so removing a row never
 * shifts the others' identity.
 */
data class OpeningHoursSlotState(
    val id: Int,
    val days: Set<Weekday> = emptySet(),
    val opensAt: TextFieldState = TextFieldState(),
    val closesAt: TextFieldState = TextFieldState(),
    /** Set on submit; until then the row-level error waits for both times to be blurred. */
    val forceShow: Boolean = false,
) {
    /** A row the user added but never touched; it is dropped instead of blocking the save. */
    val isBlank: Boolean
        get() = days.isEmpty() && opensAt.value.isBlank() && closesAt.value.isBlank()

    /** Row-level error (days missing, a time missing, closing before opening). */
    val error: String?
        get() = when {
            isBlank -> null
            days.isEmpty() -> CharacterizationCopy.DAYS_REQUIRED
            opensAt.value.isBlank() || closesAt.value.isBlank() -> CharacterizationCopy.TIMES_REQUIRED
            !opensAt.isValid || !closesAt.isValid -> null   // shown on the time input itself
            closesAt.value <= opensAt.value -> CharacterizationCopy.CLOSES_BEFORE_OPENS
            else -> null
        }

    val displayError: String?
        get() = error.takeIf {
            forceShow || (opensAt.hasBlurred && closesAt.hasBlurred && !opensAt.isFocused && !closesAt.isFocused)
        }

    val isValid: Boolean get() = isBlank || (error == null && opensAt.isValid && closesAt.isValid)
}
