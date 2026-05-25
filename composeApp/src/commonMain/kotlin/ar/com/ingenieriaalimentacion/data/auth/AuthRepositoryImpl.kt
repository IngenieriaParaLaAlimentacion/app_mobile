package ar.com.ingenieriaalimentacion.data.auth

import ar.com.ingenieriaalimentacion.data.device.DeviceIdProvider
import ar.com.ingenieriaalimentacion.domain.model.auth.AuthError
import ar.com.ingenieriaalimentacion.domain.model.auth.Credentials
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionSource
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession
import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository
import ar.com.ingenieriaalimentacion.domain.repository.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.auth.authProviders
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepositoryImpl(
    private val api: AuthApi,
    private val sessionManager: SessionManager,
    private val httpClient: HttpClient,
    private val statusSink: MutableStateFlow<SessionStatus>,
    private val deviceIdProvider: DeviceIdProvider,
) : AuthRepository {

    override val sessionStatus: StateFlow<SessionStatus> = statusSink.asStateFlow()

    override suspend fun bootstrap() {
        if (statusSink.value !is SessionStatus.Initializing) return
        val stored = sessionManager.loadSessionOrNull()
        statusSink.value = if (stored != null) {
            SessionStatus.Authenticated(stored, SessionSource.Storage)
        } else {
            SessionStatus.NotAuthenticated(isSignOut = false)
        }
    }

    override suspend fun signIn(credentials: Credentials): Result<UserSession> = runCatching {
        val deviceId = deviceIdProvider.getOrCreate()
        val session = api.signIn(credentials.email, credentials.password, deviceId)
        sessionManager.saveSession(session)
        clearBearerCache()
        statusSink.value = SessionStatus.Authenticated(session, SessionSource.SignIn)
        session
    }.recoverCatching { throwable ->
        throw AuthFailure(mapToAuthError(throwable), throwable)
    }

    override suspend fun signOut() {
        // Best-effort server invalidation; always clear local state.
        runCatching { httpClient.post("auth/logout") }
        sessionManager.deleteSession()
        clearBearerCache()
        statusSink.value = SessionStatus.NotAuthenticated(isSignOut = true)
    }

    override suspend fun resetPassword(token: String, newPassword: String): Result<Unit> = runCatching {
        api.resetPassword(token, newPassword)
        // Force a clean re-login with the new password — and clear any stale session
        // (safe whether or not a session exists).
        signOut()
    }.recoverCatching { throwable ->
        throw AuthFailure(mapResetError(throwable), throwable)
    }

    override suspend fun requestPasswordRecovery(email: String): Result<Unit> = runCatching {
        api.requestPasswordRecovery(email)
    }.recoverCatching { throwable ->
        throw AuthFailure(mapRequestRecoveryError(throwable), throwable)
    }

    private fun clearBearerCache() {
        httpClient.authProviders
            .filterIsInstance<BearerAuthProvider>()
            .firstOrNull()
            ?.clearToken()
    }

    private fun mapToAuthError(throwable: Throwable): AuthError = when {
        throwable is ResponseException && throwable.response.status == HttpStatusCode.Unauthorized ->
            AuthError.InvalidCredentials
        throwable is ResponseException && throwable.response.status == HttpStatusCode.Forbidden ->
            AuthError.AccountPendingValidation
        throwable is ResponseException -> AuthError.Network
        else -> AuthError.Unknown(throwable)
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
 * Future Login feature will pattern-match on `error` to render messages.
 */
class AuthFailure(val error: AuthError, cause: Throwable? = null) : Exception(error.toString(), cause)
