package ar.com.ingenieriaalimentacion.presentation.feature.auth.login

sealed class LoginDestination {
    data object Home : LoginDestination()
    data object ForgotPassword : LoginDestination()
    data object Back : LoginDestination()
}
