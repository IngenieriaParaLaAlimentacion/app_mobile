package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.auth.Credentials
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession
import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAuthRepository : AuthRepository {
    private val _status = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
    override val sessionStatus: StateFlow<SessionStatus> = _status.asStateFlow()

    var signOutCount: Int = 0

    /** Drives the session-status flow from tests. */
    fun emit(status: SessionStatus) {
        _status.value = status
    }

    override suspend fun bootstrap() = Unit
    override suspend fun signIn(credentials: Credentials): Result<UserSession> =
        Result.failure(IllegalStateException("not used"))

    override suspend fun signOut() {
        signOutCount++
    }

    override suspend fun resetPassword(token: String, newPassword: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun requestPasswordRecovery(email: String): Result<Unit> =
        Result.success(Unit)
}
