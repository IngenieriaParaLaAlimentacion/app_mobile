package ar.com.ingenieriaalimentacion.domain.model.auth

sealed interface SessionStatus {
    data object Initializing : SessionStatus
    data class NotAuthenticated(val isSignOut: Boolean = false) : SessionStatus
    data object Closing : SessionStatus
    data object AccountBlocked : SessionStatus
    data object PasswordResetCompleted : SessionStatus
    data class Authenticated(
        val session: UserSession,
        val generation: String,
        val source: SessionSource = SessionSource.Unknown,
    ) : SessionStatus
}
