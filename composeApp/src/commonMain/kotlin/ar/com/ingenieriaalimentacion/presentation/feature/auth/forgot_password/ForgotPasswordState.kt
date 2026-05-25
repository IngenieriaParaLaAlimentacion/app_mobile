package ar.com.ingenieriaalimentacion.presentation.feature.auth.forgot_password

data class ForgotPasswordState(
    val destination: ForgotPasswordDestination? = null,
    val email: String = "",
    val isSubmitting: Boolean = false,
    val isSent: Boolean = false,
    val fieldError: String? = null,
    val submitError: String? = null,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && !isSubmitting && !isSent
}
