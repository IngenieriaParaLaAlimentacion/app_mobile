package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.auth.Credentials
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val sessionStatus: StateFlow<SessionStatus>
    suspend fun bootstrap()
    suspend fun signIn(credentials: Credentials): Result<UserSession>
    suspend fun signOut()
    suspend fun resetPassword(token: String, newPassword: String): Result<Unit>
    suspend fun requestPasswordRecovery(email: String): Result<Unit>
}
