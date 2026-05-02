package ar.com.ingenieriaalimentacion.domain.model.auth

sealed interface SessionSource {
    data object Storage : SessionSource
    data object SignIn : SessionSource
    data class Refresh(val oldSession: UserSession) : SessionSource
    data object Unknown : SessionSource
}
