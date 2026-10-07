package ar.com.ingenieriaalimentacion.presentation.feature.auth.verify_email

sealed interface VerifyEmailAction {
    data object Retry : VerifyEmailAction
    data object Continue : VerifyEmailAction
}
