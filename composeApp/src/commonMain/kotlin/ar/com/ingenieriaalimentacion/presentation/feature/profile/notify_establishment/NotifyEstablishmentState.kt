package ar.com.ingenieriaalimentacion.presentation.feature.profile.notify_establishment

import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentAbsenceError
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationFormState

/** The request is two steps: the establishment's name and address, then its optional ficha. */
enum class NotifyEstablishmentStep { Basics, Characterization }

data class NotifyEstablishmentState(
    val destination: NotifyEstablishmentDestination? = null,
    val step: NotifyEstablishmentStep = NotifyEstablishmentStep.Basics,
    val name: TextFieldState = TextFieldState(),
    val address: TextFieldState = TextFieldState(),
    val city: TextFieldState = TextFieldState(),
    val province: TextFieldState = TextFieldState(),
    val characterization: CharacterizationFormState = CharacterizationFormState(),
    val isSubmitting: Boolean = false,
    val submitError: EstablishmentAbsenceError? = null,
    /** Set when sending the ficha was blocked by client-side errors, which it marks inline. */
    val hasInvalidCharacterization: Boolean = false,
) {
    /** Name and address are required; city and province only guard max length. */
    val isFormValid: Boolean
        get() = name.isValid && address.isValid && city.isValid && province.isValid

    /** Submit stays tappable so it can force-show the required errors; only an in-flight submit blocks. */
    val canSubmit: Boolean get() = !isSubmitting
}
