package ar.com.ingenieriaalimentacion.presentation.feature.auth.forgot_password

sealed interface ForgotPasswordAction {
    data class EmailChanged(val value: String) : ForgotPasswordAction
    data object Submit : ForgotPasswordAction
    data object DismissError : ForgotPasswordAction
    data object Back : ForgotPasswordAction
}
