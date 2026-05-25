package ar.com.ingenieriaalimentacion.domain.model.auth

sealed interface AuthError {
    data object InvalidCredentials : AuthError
    data object InvalidOrExpiredToken : AuthError
    data object WeakPassword : AuthError
    data object Network : AuthError
    data class Unknown(val cause: Throwable? = null) : AuthError
}
