package ar.com.ingenieriaalimentacion.presentation.feature.auth.reset_password

data class ResetPasswordState(
    val destination: ResetPasswordDestination? = null,
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isSubmitting: Boolean = false,
    val fieldErrors: ResetPasswordFieldErrors = ResetPasswordFieldErrors(),
    val submitError: String? = null,
) {
    val canSubmit: Boolean
        get() = newPassword.isNotBlank() && confirmPassword.isNotBlank() && !isSubmitting
}

data class ResetPasswordFieldErrors(
    val newPassword: String? = null,
    val confirmPassword: String? = null,
)
