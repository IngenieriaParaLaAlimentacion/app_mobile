package ar.com.ingenieriaalimentacion.presentation.feature.auth.reset_password

sealed interface ResetPasswordAction {
    data class NewPasswordChanged(val value: String) : ResetPasswordAction
    data class ConfirmPasswordChanged(val value: String) : ResetPasswordAction
    data object Submit : ResetPasswordAction
    data object DismissError : ResetPasswordAction
}
