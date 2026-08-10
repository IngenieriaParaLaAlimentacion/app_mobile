package ar.com.ingenieriaalimentacion.presentation.feature.profile.notify_establishment

import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentAbsenceError

data class NotifyEstablishmentState(
    val destination: NotifyEstablishmentDestination? = null,
    val name: TextFieldState = TextFieldState(),
    val address: TextFieldState = TextFieldState(),
    val city: TextFieldState = TextFieldState(),
    val province: TextFieldState = TextFieldState(),
    val isSubmitting: Boolean = false,
    val submitError: EstablishmentAbsenceError? = null,
) {
    /** Name and address are required; city and province only guard max length. */
    val isFormValid: Boolean
        get() = name.isValid && address.isValid && city.isValid && province.isValid

    /** Submit stays tappable so it can force-show the required errors; only an in-flight submit blocks. */
    val canSubmit: Boolean get() = !isSubmitting
}
