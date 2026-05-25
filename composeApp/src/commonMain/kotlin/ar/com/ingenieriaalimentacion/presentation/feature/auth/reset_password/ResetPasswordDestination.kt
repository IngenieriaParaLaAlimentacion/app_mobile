package ar.com.ingenieriaalimentacion.presentation.feature.auth.reset_password

sealed class ResetPasswordDestination {
    data object Success : ResetPasswordDestination()
}
