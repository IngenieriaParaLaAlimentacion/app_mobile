package ar.com.ingenieriaalimentacion.presentation.feature.auth.verify_email

sealed class VerifyEmailDestination {
    /** Leave the screen; the navigation layer decides where the back stack leads. */
    data object Exit : VerifyEmailDestination()
}
