package ar.com.ingenieriaalimentacion.presentation.feature.auth.login

sealed interface LoginAction {
    data class EmailChanged(val value: String) : LoginAction
    data class PasswordChanged(val value: String) : LoginAction
    data object Submit : LoginAction
    data object DismissError : LoginAction
    data object ForgotPasswordClick : LoginAction
}
