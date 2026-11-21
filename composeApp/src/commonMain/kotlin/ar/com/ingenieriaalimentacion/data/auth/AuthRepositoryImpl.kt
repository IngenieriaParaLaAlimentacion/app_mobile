package ar.com.ingenieriaalimentacion.data.auth

import ar.com.ingenieriaalimentacion.data.device.DeviceIdProvider
import ar.com.ingenieriaalimentacion.domain.model.auth.AuthError
import ar.com.ingenieriaalimentacion.domain.model.auth.Credentials
import ar.com.ingenieriaalimentacion.domain.model.auth.EmailVerificationError
import ar.com.ingenieriaalimentacion.domain.model.auth.EmailVerificationFailure
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession
import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow

class AuthRepositoryImpl(
    private val api: AuthApi,
    private val lifecycle: SessionLifecycle,
    private val deviceIdProvider: DeviceIdProvider,
) : AuthRepository {

    override val sessionStatus: StateFlow<SessionStatus> = lifecycle.status

    override suspend fun bootstrap() = lifecycle.bootstrap()

    override suspend fun signIn(credentials: Credentials): Result<UserSession> = authResult {
        val ticket = lifecycle.beginSignIn()
        val deviceId = deviceIdProvider.getOrCreate()
        val session = api.signIn(credentials.email, credentials.password, deviceId)
        lifecycle.completeSignIn(ticket, session)
        session
    }

    override suspend fun signOut(beforeLogout: suspend () -> Unit) {
        val lease = lifecycle.leaseOrNull() ?: return
        close(lease, SessionExit.SignedOut, beforeLogout)
    }

    override suspend fun resetPassword(
        token: String,
        newPassword: String,
        beforeLogout: suspend () -> Unit,
    ): Result<Unit> = authResult(::mapResetError) {
        val lease = lifecycle.leaseOrNull()
        api.resetPassword(token, newPassword)
        if (lease != null) close(lease, SessionExit.PasswordReset, beforeLogout)
    }

    private suspend fun close(lease: SessionLease, exit: SessionExit, beforeLogout: suspend () -> Unit) {
        try {
            withContext(lease) {
                if (lifecycle.sessionFor(lease) == null) return@withContext
                bestEffort(beforeLogout)
                val refreshToken = lifecycle.sessionFor(lease)?.session?.refreshToken ?: return@withContext
                bestEffort { api.logout(refreshToken, deviceIdProvider.getOrCreate()) }
            }
        } finally {
            lifecycle.end(lease, exit)
        }
    }

    private suspend fun bestEffort(operation: suspend () -> Unit) {
        try {
            operation()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Local sign-out proceeds when the server cannot be reached.
        }
    }

    override suspend fun requestPasswordRecovery(email: String): Result<Unit> = authResult(::mapRequestRecoveryError) {
        api.requestPasswordRecovery(email)
    }

    // Deliberately does not read or change the session: verification is independent of login.
    override suspend fun verifyEmail(token: String): Result<Unit> =
        when (val outcome = api.verifyEmail(token)) {
            VerifyEmailOutcome.Verified -> Result.success(Unit)
            VerifyEmailOutcome.TokenUsed -> verificationFailure(EmailVerificationError.TokenUsed)
            VerifyEmailOutcome.TokenExpired -> verificationFailure(EmailVerificationError.TokenExpired)
            VerifyEmailOutcome.InvalidToken -> verificationFailure(EmailVerificationError.InvalidToken)
            is VerifyEmailOutcome.Retryable ->
                verificationFailure(EmailVerificationError.Network, outcome.cause)
        }

    private fun verificationFailure(
        error: EmailVerificationError,
        cause: Throwable? = null,
    ): Result<Unit> = Result.failure(EmailVerificationFailure(error, cause))

    private inline fun <T> authResult(
        mapError: (Throwable) -> AuthError = { AuthError.Unknown(it) },
        operation: () -> T,
    ): Result<T> = try {
        Result.success(operation())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: AuthFailure) {
        Result.failure(failure)
    } catch (failure: Exception) {
        Result.failure(AuthFailure(mapError(failure), failure))
    }

    private fun mapResetError(throwable: Throwable): AuthError = when {
        throwable is ResponseException && throwable.response.status == HttpStatusCode.Unauthorized ->
            AuthError.InvalidOrExpiredToken
        throwable is ResponseException -> AuthError.Network
        else -> AuthError.Unknown(throwable)
    }

    private fun mapRequestRecoveryError(throwable: Throwable): AuthError = when (throwable) {
        is ResponseException -> AuthError.Network
        else -> AuthError.Unknown(throwable)
    }
}

/**
 * Wraps a typed [AuthError] for callers that bubble it through `Result.failure(...)`.
 */
class AuthFailure(val error: AuthError, cause: Throwable? = null) : Exception(error.toString(), cause)
