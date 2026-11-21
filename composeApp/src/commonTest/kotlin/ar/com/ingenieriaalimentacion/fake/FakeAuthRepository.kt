package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.auth.Credentials
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession
import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAuthRepository(
    private val sharedEvents: MutableList<String>? = null,
) : AuthRepository {
    private val _status = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
    override val sessionStatus: StateFlow<SessionStatus> = _status.asStateFlow()

    var signOutCount: Int = 0
    var resetPasswordResult: Result<Unit> = Result.success(Unit)

    /** Drives the session-status flow from tests. */
    fun emit(status: SessionStatus) {
        _status.value = status
    }

    override suspend fun bootstrap() = Unit
    override suspend fun signIn(credentials: Credentials): Result<UserSession> =
        Result.failure(IllegalStateException("not used"))

    override suspend fun signOut(beforeLogout: suspend () -> Unit) {
        beforeLogout()
        sharedEvents?.add("authSignOut")
        signOutCount++
    }

    override suspend fun resetPassword(
        token: String,
        newPassword: String,
        beforeLogout: suspend () -> Unit,
    ): Result<Unit> {
        sharedEvents?.add("resetPassword")
        if (resetPasswordResult.isSuccess && sessionStatus.value is SessionStatus.Authenticated) {
            signOut(beforeLogout)
        }
        return resetPasswordResult
    }

    override suspend fun requestPasswordRecovery(email: String): Result<Unit> =
        Result.success(Unit)

    var verifyEmailResult: Result<Unit> = Result.success(Unit)

    /** When set, [verifyEmail] suspends on this gate — lets tests observe the in-flight state. */
    var verifyEmailGate: CompletableDeferred<Unit>? = null
    val verifyEmailTokens = mutableListOf<String>()

    override suspend fun verifyEmail(token: String): Result<Unit> {
        verifyEmailTokens += token
        verifyEmailGate?.await()
        return verifyEmailResult
    }
}
