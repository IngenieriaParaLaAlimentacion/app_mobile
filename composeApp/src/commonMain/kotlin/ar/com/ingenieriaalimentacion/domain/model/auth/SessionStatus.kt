package ar.com.ingenieriaalimentacion.domain.model.auth

sealed interface SessionStatus {
    data object Initializing : SessionStatus
    data class NotAuthenticated(val isSignOut: Boolean = false) : SessionStatus
    data class RefreshFailure(val cause: String) : SessionStatus
    data class Authenticated(
        val session: UserSession,
        val source: SessionSource = SessionSource.Unknown,
    ) : SessionStatus
}
