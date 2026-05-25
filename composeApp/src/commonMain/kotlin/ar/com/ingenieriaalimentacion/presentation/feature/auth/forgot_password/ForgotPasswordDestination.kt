package ar.com.ingenieriaalimentacion.presentation.feature.auth.forgot_password

sealed class ForgotPasswordDestination {
    data object Back : ForgotPasswordDestination()
}
