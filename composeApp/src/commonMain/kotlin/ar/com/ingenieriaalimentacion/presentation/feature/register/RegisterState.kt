package ar.com.ingenieriaalimentacion.presentation.feature.register

import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment

data class RegisterState(
    val destination: RegisterDestination? = null,
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val lastName: String = "",
    val establishments: List<Establishment> = emptyList(),
    val selectedEstablishmentIds: Set<String> = emptySet(),
    val isLoadingEstablishments: Boolean = false,
    val isSubmitting: Boolean = false,
    val fieldErrors: RegisterFieldErrors = RegisterFieldErrors(),
    val submitError: String? = null,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && name.isNotBlank() &&
            lastName.isNotBlank() && selectedEstablishmentIds.isNotEmpty() && !isSubmitting
}

data class RegisterFieldErrors(
    val email: String? = null,
    val password: String? = null,
    val name: String? = null,
    val lastName: String? = null,
    val establishments: String? = null,
)
