package ar.com.ingenieriaalimentacion.data.auth

import ar.com.ingenieriaalimentacion.data.auth.dto.LoginRequest
import ar.com.ingenieriaalimentacion.data.auth.dto.LogoutRequest
import ar.com.ingenieriaalimentacion.data.auth.dto.RefreshRequest
import ar.com.ingenieriaalimentacion.data.auth.dto.RequestPasswordRecoveryRequest
import ar.com.ingenieriaalimentacion.data.auth.dto.ResetPasswordRequest
import ar.com.ingenieriaalimentacion.data.auth.dto.VERIFICATION_TOKEN_EXPIRED_TYPE
import ar.com.ingenieriaalimentacion.data.auth.dto.VERIFICATION_TOKEN_USED_TYPE
import ar.com.ingenieriaalimentacion.data.auth.dto.VerifyEmailProblem
import ar.com.ingenieriaalimentacion.data.auth.dto.VerifyEmailRequest
import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

/**
 * Auth endpoints that must NOT carry an `Authorization` header.
 * Constructed against the no-auth refresh `HttpClient` (Koin qualifier `"refresh"`).
 */
class AuthApi(private val refreshClient: HttpClient) {

    suspend fun signIn(email: String, password: String, deviceId: String): UserSession =
        refreshClient.post("api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password, deviceId))
        }.body()

    suspend fun refresh(refreshToken: String, deviceId: String): UserSession =
        refreshClient.post("api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken, deviceId))
        }.body()

    suspend fun resetPassword(token: String, newPassword: String) {
        refreshClient.post("api/v1/auth/password-recovery/confirm") {
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordRequest(token = token, password = newPassword))
        }
    }

    suspend fun requestPasswordRecovery(email: String) {
        refreshClient.post("api/v1/auth/password-recovery/request") {
            contentType(ContentType.Application.Json)
            setBody(RequestPasswordRecoveryRequest(email = email))
        }
    }

    suspend fun logout(refreshToken: String, deviceId: String) {
        refreshClient.post("api/v1/auth/logout") {
            contentType(ContentType.Application.Json)
            setBody(LogoutRequest(deviceId = deviceId, refreshToken = refreshToken))
        }
    }

    /**
     * `POST /api/v1/auth/verify-email/confirm` — unauthenticated, `204` on success.
     *
     * Unlike the calls above, this one never throws on a non-2xx: the three token failures are
     * all `401` and differ only in the problem `type`, so the status alone is not enough. The
     * refresh client sets `expectSuccess = true`, hence the per-request opt-out; from there it
     * is the same status-then-type branching as `FoodIntakeApi`.
     */
    suspend fun verifyEmail(token: String): VerifyEmailOutcome = runCatching {
        val response = refreshClient.post("api/v1/auth/verify-email/confirm") {
            expectSuccess = false
            contentType(ContentType.Application.Json)
            setBody(VerifyEmailRequest(token = token))
        }
        when (response.status.value) {
            in 200..299 -> VerifyEmailOutcome.Verified
            401 -> when (response.problemType()) {
                VERIFICATION_TOKEN_USED_TYPE -> VerifyEmailOutcome.TokenUsed
                VERIFICATION_TOKEN_EXPIRED_TYPE -> VerifyEmailOutcome.TokenExpired
                // `/errors/auth/invalid-verification-token`, or a 401 this build does not
                // recognize: either way this link cannot confirm the email.
                else -> VerifyEmailOutcome.InvalidToken
            }
            400, 422 -> VerifyEmailOutcome.InvalidToken // malformed body, e.g. an empty token
            else -> VerifyEmailOutcome.Retryable(null) // 5xx and anything unexpected
        }
    }.getOrElse { t ->
        if (t is CancellationException) throw t
        VerifyEmailOutcome.Retryable(t) // transport failures
    }

    private suspend fun HttpResponse.problemType(): String? =
        runCatching { body<VerifyEmailProblem>() }.getOrNull()?.type
}

sealed interface VerifyEmailOutcome {
    data object Verified : VerifyEmailOutcome
    data object TokenUsed : VerifyEmailOutcome
    data object TokenExpired : VerifyEmailOutcome
    data object InvalidToken : VerifyEmailOutcome
    data class Retryable(val cause: Throwable?) : VerifyEmailOutcome
}
