package ar.com.ingenieriaalimentacion.presentation.feature.register

import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment

data class RegisterState(
    val destination: RegisterDestination? = null,
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val lastName: String = "",
    /** Optional contact details — blur-aware validated, but never required to submit. */
    val locality: TextFieldState = TextFieldState(),
    val description: TextFieldState = TextFieldState(),
    val contactPhone: TextFieldState = TextFieldState(),
    /**
     * Optional too: the backend accepts a registration with no establishments (they are only a
     * request for access, not a precondition for the account), and the user can ask for more
     * from their profile once approved.
     */
    val selectedEstablishments: List<Establishment> = emptyList(),
    val isSubmitting: Boolean = false,
    val fieldErrors: RegisterFieldErrors = RegisterFieldErrors(),
    val submitError: String? = null,
) {
    /**
     * The optional fields never *enable* the button — they're valid while empty — but a
     * filled-in value that breaks its rule blocks the submit until it's corrected.
     */
    private val optionalFieldsValid: Boolean
        get() = locality.isValid && description.isValid && contactPhone.isValid

    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && name.isNotBlank() &&
            lastName.isNotBlank() && optionalFieldsValid && !isSubmitting
}

data class RegisterFieldErrors(
    val email: String? = null,
    val password: String? = null,
    val name: String? = null,
    val lastName: String? = null,
)
