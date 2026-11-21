package ar.com.ingenieriaalimentacion.presentation.feature.auth.login

internal const val BLOCKED_ACCOUNT_MESSAGE =
    "Tu cuenta no tiene acceso a la plataforma. Contactá a un administrador."

data class LoginState(
    val destination: LoginDestination? = null,
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val fieldErrors: LoginFieldErrors = LoginFieldErrors(),
    val submitError: String? = null,
    val showEntryNotice: Boolean = true,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && !isSubmitting
}

data class LoginFieldErrors(
    val email: String? = null,
    val password: String? = null,
)
