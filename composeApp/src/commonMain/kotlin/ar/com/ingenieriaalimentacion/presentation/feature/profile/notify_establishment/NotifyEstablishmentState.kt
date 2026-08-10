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
    /** Every field is optional, so validity only guards content rules (max length). */
    val isFormValid: Boolean
        get() = name.isValid && address.isValid && city.isValid && province.isValid

    /** The notice may be sent completely empty — only an in-flight submit blocks. */
    val canSubmit: Boolean get() = !isSubmitting
}
