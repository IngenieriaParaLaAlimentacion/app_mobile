package ar.com.ingenieriaalimentacion.presentation.feature.auth.login

data class LoginState(
    val destination: LoginDestination? = null,
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val fieldErrors: LoginFieldErrors = LoginFieldErrors(),
    val submitError: String? = null,
    val isForgotPasswordEnabled: Boolean = false,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && !isSubmitting
}

data class LoginFieldErrors(
    val email: String? = null,
    val password: String? = null,
)
