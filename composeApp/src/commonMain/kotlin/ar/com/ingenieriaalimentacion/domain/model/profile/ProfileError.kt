package ar.com.ingenieriaalimentacion.domain.model.profile

sealed interface ProfileError {
    data object Unauthorized : ProfileError       // 401
    data object UserNotFound : ProfileError        // 404
    data object ValidationError : ProfileError     // 422 (defensive)
    data object Network : ProfileError
    data class Unknown(val cause: Throwable?) : ProfileError
}
